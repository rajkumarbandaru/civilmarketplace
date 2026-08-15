package com.civileng.marketplace.notification.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Thin wrapper over the Twilio REST client, shared by {@link SmsService} and
 * {@link WhatsAppService} — both channels are the same Twilio Messages API, differing
 * only in the {@code whatsapp:} prefix on the addresses.
 *
 * <p>{@code Twilio.init} sets process-wide static state, so it is done exactly once here
 * rather than per send. When the configured credentials are not a real account SID and auth
 * token the gateway reports itself unconfigured and the callers fall back to logging, instead
 * of failing a login flow on credentials that were never filled in.
 */
@Component
@Slf4j
public class TwilioGateway {

    /**
     * A live account SID is {@code AC} followed by 32 hex digits. Matching the real shape,
     * rather than blocklisting known placeholder strings, rejects every dummy value at once
     * — the config-repo default (`placeholder`) and the `ACXXXX…` in `.env.example` alike.
     */
    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[0-9a-fA-F]{32}$");
    /** Auth tokens are 32 hex digits. */
    private static final Pattern AUTH_TOKEN = Pattern.compile("^[0-9a-fA-F]{32}$");

    @Value("${app.sms.twilio.account-sid:}")
    private String accountSid;

    @Value("${app.sms.twilio.auth-token:}")
    private String authToken;

    private boolean configured;

    @PostConstruct
    void init() {
        configured = accountSid != null && AUTH_TOKEN.matcher(nullToEmpty(authToken)).matches()
                && ACCOUNT_SID.matcher(accountSid).matches();
        if (configured) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio gateway initialised for account ending {}",
                    accountSid.substring(Math.max(0, accountSid.length() - 4)));
        } else {
            log.warn("Twilio credentials missing or not a real account SID/auth token "
                    + "- SMS and WhatsApp will fall back to logging");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * @param from sender address, already channel-prefixed for WhatsApp
     * @param to   recipient address, already channel-prefixed for WhatsApp
     * @return the Twilio message SID
     */
    public String send(String from, String to, String body) {
        Message message = Message.creator(new PhoneNumber(to), new PhoneNumber(from), body).create();
        return message.getSid();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
