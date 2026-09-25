package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.tenant.common.TenantContext;
import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import com.civileng.marketplace.tenant.common.integration.IntegrationMode;
import com.civileng.marketplace.tenant.common.integration.TenantIntegration;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationResolver;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Which account each tenant's SMS, WhatsApp and email go out on. The rule under test: a tenant's
 * own account, the platform's by default, or nothing when it switched the channel off.
 */
class TenantChannelRoutingTest {

    private static final String ACME_SID = "AC" + "a".repeat(32);
    private static final String ACME_TOKEN = "b".repeat(32);
    private static final String PLATFORM_SID = "AC" + "c".repeat(32);
    private static final String PLATFORM_TOKEN = "d".repeat(32);

    private final List<TenantIntegration> rows = new ArrayList<>();
    private TenantIntegrationResolver resolver;
    private TwilioGateway twilio;

    @BeforeEach
    void setUp() {
        TenantIntegrationStore store = new TenantIntegrationStore() {
            @Override
            public Optional<TenantIntegration> find(String tenantKey, IntegrationCapability capability) {
                return rows.stream()
                        .filter(r -> r.tenantKey().equals(tenantKey) && r.capability() == capability)
                        .findFirst();
            }

            @Override
            public Optional<TenantIntegration> findByWebhookToken(IntegrationCapability c, String t) {
                return Optional.empty();
            }

            @Override
            public void evict(String tenantKey) {
            }
        };
        resolver = new TenantIntegrationResolver(store, "platform");
        twilio = new TwilioGateway(resolver);
        ReflectionTestUtils.setField(twilio, "platformAccountSid", PLATFORM_SID);
        ReflectionTestUtils.setField(twilio, "platformAuthToken", PLATFORM_TOKEN);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void smsGoesOutOnTheTenantsOwnAccountAndSender() {
        rows.add(new TenantIntegration("acme", IntegrationCapability.SMS, IntegrationMode.BYO, "twilio", true,
                Map.of("accountSid", ACME_SID, "fromNumber", "+911111111111", "senderId", "ACMEBL"),
                Map.of("authToken", ACME_TOKEN), null));
        TenantContext.set("acme");

        TwilioGateway.Route route = smsRoute();

        assertThat(route.sendable()).isTrue();
        assertThat(route.account().accountSid()).isEqualTo(ACME_SID);
        assertThat(route.from()).isEqualTo("+911111111111");
        assertThat(route.senderLabel()).isEqualTo("ACMEBL");
    }

    @Test
    void aTenantWithoutAnSmsAccountSendsOnThePlatformsAccountByDefault() {
        TenantContext.set("bhoomi");

        TwilioGateway.Route route = smsRoute();

        assertThat(route.sendable()).isTrue();
        assertThat(route.account().accountSid()).isEqualTo(PLATFORM_SID);
    }

    @Test
    void aTenantThatSwitchedSmsOffSendsNothing() {
        rows.add(new TenantIntegration("quiet", IntegrationCapability.SMS, IntegrationMode.PLATFORM_SHARED, null,
                false, Map.of(), Map.of(), null));
        TenantContext.set("quiet");

        TwilioGateway.Route route = smsRoute();

        assertThat(route.sendable()).isFalse();
        assertThat(route.skipReason()).contains("SMS is not configured for this tenant");
    }

    @Test
    void theOperatorTenantSendsOnThePlatformsAccount() {
        TenantContext.set("platform");

        TwilioGateway.Route route = smsRoute();

        assertThat(route.sendable()).isTrue();
        assertThat(route.account().accountSid()).isEqualTo(PLATFORM_SID);
        assertThat(route.senderLabel()).isEqualTo("PLATFM");
    }

    @Test
    void placeholderTenantCredentialsAreNotUsed() {
        rows.add(new TenantIntegration("acme", IntegrationCapability.WHATSAPP, IntegrationMode.BYO, "twilio",
                true, Map.of("accountSid", "ACXXXX", "fromNumber", "+91", "senderName", "Acme"),
                Map.of("authToken", "placeholder"), null));
        TenantContext.set("acme");

        TwilioGateway.Route route = twilio.route(IntegrationCapability.WHATSAPP, true, "+1", "senderName", "P");

        assertThat(route.sendable()).isFalse();
        assertThat(route.senderLabel()).isEqualTo("Acme");
    }

    @Test
    void emailOnTheSharedPlatformAccountCarriesTheTenantsSenderName() {
        rows.add(new TenantIntegration("acme", IntegrationCapability.EMAIL, IntegrationMode.PLATFORM_SHARED,
                null, true, Map.of("fromName", "Acme Builders"), Map.of(), null));
        TenantContext.set("acme");
        JavaMailSender platformRelay = mock(JavaMailSender.class);

        EmailService.EmailRoute route = emailService(platformRelay, "smtp", "real-user", "real-pass").route();

        assertThat(route.provider()).isEqualTo("smtp");
        assertThat(route.smtp()).isSameAs(platformRelay);
        assertThat(route.fromName()).isEqualTo("Acme Builders");
        assertThat(route.fromAddress()).isEqualTo("no-reply@platform.test");
    }

    @Test
    void emailOnTheTenantsOwnRelayNeverTouchesThePlatformsSender() {
        rows.add(new TenantIntegration("acme", IntegrationCapability.EMAIL, IntegrationMode.BYO, "smtp", true,
                Map.of("host", "smtp.acme.test", "port", "2525", "username", "mailer",
                        "fromAddress", "hello@acme.test", "fromName", "Acme"),
                Map.of("password", "acme-mail-pass"), null));
        TenantContext.set("acme");
        JavaMailSender platformRelay = mock(JavaMailSender.class);

        EmailService.EmailRoute route = emailService(platformRelay, "smtp", "real-user", "real-pass").route();

        assertThat(route.smtp()).isNotSameAs(platformRelay).isInstanceOf(JavaMailSenderImpl.class);
        JavaMailSenderImpl tenantRelay = (JavaMailSenderImpl) route.smtp();
        assertThat(tenantRelay.getHost()).isEqualTo("smtp.acme.test");
        assertThat(tenantRelay.getPort()).isEqualTo(2525);
        assertThat(route.fromAddress()).isEqualTo("hello@acme.test");
    }

    @Test
    void aTenantWithNoEmailIntegrationSendsOnThePlatformsAccount() {
        TenantContext.set("bhoomi");

        EmailService.EmailRoute route =
                emailService(mock(JavaMailSender.class), "smtp", "real-user", "real-pass").route();

        assertThat(route.provider()).isNotEqualTo("log");
        assertThat(route.skipReason()).isNull();
    }

    @Test
    void aTenantThatSwitchedEmailOffIsLoggedNotSent() {
        rows.add(new TenantIntegration("quiet", IntegrationCapability.EMAIL, IntegrationMode.PLATFORM_SHARED, null,
                false, Map.of(), Map.of(), null));
        TenantContext.set("quiet");

        EmailService.EmailRoute route =
                emailService(mock(JavaMailSender.class), "smtp", "real-user", "real-pass").route();

        assertThat(route.provider()).isEqualTo("log");
        assertThat(route.skipReason()).contains("not configured for this tenant");
    }

    private TwilioGateway.Route smsRoute() {
        return twilio.route(IntegrationCapability.SMS, true, "+10000000000", "senderId", "PLATFM");
    }

    private EmailService emailService(JavaMailSender platformRelay, String provider, String user, String pass) {
        EmailService service = new EmailService(platformRelay, mock(EmailTemplateService.class),
                mock(BrevoEmailSender.class), mock(EmailLogService.class), resolver, new TenantMailSenders());
        ReflectionTestUtils.setField(service, "provider", provider);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@platform.test");
        ReflectionTestUtils.setField(service, "fromName", "Platform");
        ReflectionTestUtils.setField(service, "smtpUsername", user);
        ReflectionTestUtils.setField(service, "smtpPassword", pass);
        return service;
    }
}
