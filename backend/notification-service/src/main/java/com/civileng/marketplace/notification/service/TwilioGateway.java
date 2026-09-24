package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import com.civileng.marketplace.tenant.common.integration.ResolvedIntegration;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationResolver;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Twilio, per tenant — shared by {@link SmsService} and {@link WhatsAppService}, which differ only
 * in the {@code whatsapp:} address prefix.
 *
 * <p>This used to call {@code Twilio.init} once, which sets process-wide static credentials: every
 * tenant's SMS went out from one account and one sender. Each send now builds (and caches) a
 * {@link TwilioRestClient} for the account the current tenant resolves to. A customer tenant uses
 * its own account; only the operator tenant uses the {@code app.sms.twilio.*} account from
 * configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TwilioGateway {

    /**
     * A live account SID is {@code AC} followed by 32 hex digits. Matching the real shape,
     * rather than blocklisting known placeholder strings, rejects every dummy value at once
     * — the config-repo default (`placeholder`) and the `ACXXXX…` in `.env.example` alike.
     */
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    /** Auth tokens are 32 hex digits. */
    private static final Pattern AUTH_TOKEN = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final TenantIntegrationResolver resolver;
    private final Map<String, TwilioRestClient> clients = new ConcurrentHashMap<>();

    @Value("${app.sms.twilio.account-sid:}")
    private String platformAccountSid;

    @Value("${app.sms.twilio.auth-token:}")
    private String platformAuthToken;

    /**
     * The account one channel should send on for the current tenant, or why there is none.
     *
     * @param platformEnabled whether the platform's own provider setting selects Twilio at all
     * @param platformFrom    the platform's sender number for this channel
     * @param nameKey         the tenant setting holding its sender label ({@code senderId} /
     *                        {@code senderName})
     * @param platformName    the platform's label, for the operator tenant
     */
    public Route route(IntegrationCapability channel, boolean platformEnabled, String platformFrom,
                       String nameKey, String platformName) {
        Optional<ResolvedIntegration> resolved = resolver.find(channel);
        if (resolved.isEmpty()) {
            return Route.skipped(platformName, channel.key().toUpperCase()
                    + " is not configured for this tenant - message was logged, not sent");
        }
        ResolvedIntegration integration = resolved.get();
        if (integration.usesPlatformCredentials()) {
            if (!platformEnabled || !looksReal(platformAccountSid, platformAuthToken)) {
                return Route.skipped(platformName, "No " + channel.key().toUpperCase()
                        + " provider configured - message was logged, not sent");
            }
            return new Route(new Account(platformAccountSid, platformAuthToken), platformFrom,
                    platformName, null);
        }
        String label = integration.setting(nameKey) == null ? platformName : integration.setting(nameKey);
        Account account = new Account(integration.setting("accountSid"), integration.secret("authToken"));
        if (!looksReal(account.accountSid(), account.authToken())) {
            return Route.skipped(label, "The tenant's Twilio credentials are not a real account "
                    + "SID/auth token - message was logged, not sent");
        }
        return new Route(account, integration.setting("fromNumber"), label, null);
    }

    /**
     * @param from sender address, already channel-prefixed for WhatsApp
     * @param to   recipient address, already channel-prefixed for WhatsApp
     * @return the Twilio message SID
     */
    public String send(Account account, String from, String to, String body) {
        TwilioRestClient client = clients.computeIfAbsent(account.cacheKey(),
                key -> new TwilioRestClient.Builder(account.accountSid(), account.authToken()).build());
        Message message = Message.creator(new PhoneNumber(to), new PhoneNumber(from), body).create(client);
        return message.getSid();
    }

    static boolean looksReal(String accountSid, String authToken) {
        return accountSid != null && authToken != null
                && ACCOUNT_SID.matcher(accountSid).matches()
                && AUTH_TOKEN.matcher(authToken).matches();
    }

    /** A Twilio account. {@code toString} leaves the token out. */
    public record Account(String accountSid, String authToken) {

        String cacheKey() {
            return accountSid + ":" + Objects.hashCode(authToken);
        }

        @Override
        public String toString() {
            return "Account[" + accountSid + "]";
        }
    }

    /**
     * Where one message goes. {@code account} null means "log it instead", with
     * {@code skipReason} saying why.
     */
    public record Route(Account account, String from, String senderLabel, String skipReason) {

        static Route skipped(String senderLabel, String reason) {
            return new Route(null, null, senderLabel, reason);
        }

        public boolean sendable() {
            return account != null;
        }
    }
}
