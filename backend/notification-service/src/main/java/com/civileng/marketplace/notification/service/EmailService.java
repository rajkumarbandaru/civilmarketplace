package com.civileng.marketplace.notification.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Sends transactional HTML email rendered from the Thymeleaf templates in
 * {@code resources/templates/email}.
 *
 * <p>{@code app.email.provider} accepts {@code smtp} (JavaMail relay), {@code brevo} (Brevo's
 * HTTP API, which needs only an API key) or {@code log}. As with the SMS and WhatsApp channels,
 * a real provider whose credentials are still a template value degrades to logging, so
 * development flows keep working without a mail account.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    /**
     * Markers that identify an SMTP username/password still carrying a template value rather
     * than a real one — {@code placeholder} is the config-repo default, {@code your_} and
     * {@code XXXX} are what `.env.example` ships. Anything matching these degrades to logging
     * rather than handing obviously-fake credentials to the mail server on every send.
     */
    private static final String[] PLACEHOLDER_MARKERS = {"placeholder", "your_", "your-", "xxxx"};

    private final JavaMailSender mailSender;
    private final EmailTemplateService templateService;
    private final BrevoEmailSender brevoEmailSender;

    @Value("${app.email.provider:smtp}")
    private String provider;

    @Value("${app.email.from-address}")
    private String fromAddress;

    @Value("${app.email.from-name}")
    private String fromName;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Async
    public void sendEmail(String to, String subject, String templateName,
                          Map<String, Object> variables) {
        if (to == null || to.isBlank()) {
            log.warn("[Email] skipped '{}' - no recipient address", subject);
            return;
        }

        String htmlContent;
        try {
            htmlContent = templateService.renderTemplate("email/" + templateName, variables);
        } catch (Exception e) {
            log.error("[Email] failed to render template {}: {}", templateName, e.getMessage());
            return;
        }

        if ("brevo".equalsIgnoreCase(provider) && brevoEmailSender.isConfigured()) {
            brevoEmailSender.send(fromAddress, fromName, to, subject, htmlContent);
            return;
        }

        if (!smtpConfigured()) {
            log.info("[Email:log] to={} subject={} template={}", to, subject, templateName);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("[Email:smtp] sent to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("[Email:smtp] failed to send to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendOtpEmail(String to, String otp) {
        sendEmail(to, "Your OTP Code - Civil Engineering Marketplace",
                "otp-template",
                Map.of("otp", otp, "expiryMinutes", 5));
    }

    @Async
    public void sendWelcomeEmail(String to, String name) {
        sendEmail(to, "Welcome to Civil Engineering Marketplace!",
                "welcome-template",
                Map.of("name", safe(name)));
    }

    @Async
    public void sendBookingConfirmation(String to, String name, String bookingCode) {
        sendEmail(to, "Booking Confirmed - " + bookingCode,
                "booking-confirmed-template",
                Map.of("name", safe(name), "bookingCode", bookingCode));
    }

    @Async
    public void sendPaymentReceipt(String to, String name, String amount,
                                   String paymentCode) {
        sendEmail(to, "Payment Received - " + paymentCode,
                "payment-received-template",
                Map.of("name", safe(name), "amount", amount, "paymentCode", paymentCode));
    }

    /** Generic notification email, used by the multi-channel dispatcher. */
    @Async
    public void sendNotificationEmail(String to, String title, String body) {
        sendEmail(to, title, "notification-template",
                Map.of("title", title, "body", body));
    }

    boolean smtpConfigured() {
        return "smtp".equalsIgnoreCase(provider)
                && isRealCredential(smtpUsername)
                && isRealCredential(smtpPassword);
    }

    private static boolean isRealCredential(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase();
        for (String marker : PLACEHOLDER_MARKERS) {
            if (lower.contains(marker)) {
                return false;
            }
        }
        return true;
    }

    /** Thymeleaf's {@code Map.of} variables reject nulls, and names are optional upstream. */
    private static String safe(String value) {
        return value == null ? "there" : value;
    }
}
