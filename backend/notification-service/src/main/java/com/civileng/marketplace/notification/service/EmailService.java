package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.notification.model.EmailStatus;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
    private final EmailLogService emailLogService;

    /**
     * Where the site is served from, for the links in these mails. Falls back to the dev origin so
     * a missing setting produces a working local link rather than a dead relative one.
     */
    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String appBaseUrl;

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
        sendNow(to, subject, templateName, variables, null);
    }

    /**
     * The single send path, run on the caller's thread so the console's test send can report what
     * happened instead of returning "queued".
     *
     * <p>{@code subject} is only a fallback: if an admin has edited the subject line for this
     * template, that one wins — an editable subject that the code could silently override would
     * not be editable at all.
     *
     * @param triggeredBy the admin who asked for this send, or null for an application event
     * @return where the attempt ended up, mirroring the row written to {@code email_log}
     */
    public EmailStatus sendNow(String to, String subject, String templateName,
                               Map<String, Object> variables, Long triggeredBy) {
        if (to == null || to.isBlank()) {
            log.warn("[Email] skipped '{}' - no recipient address", subject);
            return EmailStatus.SKIPPED;
        }

        String resolvedSubject = templateService.resolveSubject(templateName, subject, variables);
        String activeProvider = activeProvider();
        Long logId = emailLogService.open(templateName, to, resolvedSubject, activeProvider, triggeredBy);

        String htmlContent;
        try {
            htmlContent = templateService.renderTemplate("email/" + templateName, variables);
        } catch (Exception e) {
            log.error("[Email] failed to render template {}: {}", templateName, e.getMessage());
            emailLogService.complete(logId, EmailStatus.FAILED, null,
                    "Template render failed: " + e.getMessage());
            return EmailStatus.FAILED;
        }

        return deliver(to, resolvedSubject, templateName, htmlContent, activeProvider, logId);
    }

    /** Hands a finished message to the selected provider and closes out its log row. */
    private EmailStatus deliver(String to, String resolvedSubject, String templateName,
                                String htmlContent, String activeProvider, Long logId) {
        // Stored before the provider call, so the record of what we composed survives even if the
        // send itself fails — a failed email is exactly when someone wants to read it.
        emailLogService.attachBody(logId, htmlContent);

        if ("brevo".equals(activeProvider)) {
            BrevoEmailSender.SendResult result =
                    brevoEmailSender.send(fromAddress, fromName, to, resolvedSubject, htmlContent);
            // SENT, not DELIVERED: Brevo has taken the message, and only its webhook can say
            // whether it landed.
            EmailStatus status = result.accepted() ? EmailStatus.SENT : EmailStatus.FAILED;
            emailLogService.complete(logId, status, result.messageId(), result.error());
            return status;
        }

        if ("log".equals(activeProvider)) {
            log.info("[Email:log] to={} subject={} template={}", to, resolvedSubject, templateName);
            emailLogService.complete(logId, EmailStatus.SKIPPED, null,
                    "No email provider configured — message was logged, not sent");
            return EmailStatus.SKIPPED;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(resolvedSubject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("[Email:smtp] sent to={} subject={}", to, resolvedSubject);
            // The relay accepted it; SMTP gives us no delivery callback, so SENT is where this
            // row rests unless a bounce is investigated by hand.
            emailLogService.complete(logId, EmailStatus.SENT, null, null);
            return EmailStatus.SENT;
        } catch (Exception e) {
            log.error("[Email:smtp] failed to send to {}: {}", to, e.getMessage());
            emailLogService.complete(logId, EmailStatus.FAILED, null, e.getMessage());
            return EmailStatus.FAILED;
        }
    }

    /**
     * Which channel this send will actually take: the configured provider, downgraded to
     * {@code log} when its credentials are missing or still placeholders.
     */
    private String activeProvider() {
        if ("brevo".equalsIgnoreCase(provider) && brevoEmailSender.isConfigured()) {
            return "brevo";
        }
        if (smtpConfigured()) {
            return "smtp";
        }
        return "log";
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

    /**
     * The receipt for a paid booking: amount, what was booked, and when it happens.
     *
     * Takes the whole event map rather than a parameter list because the template shows a dozen
     * fields and any of them may be blank — a signature that long is one transposed argument away
     * from emailing the GST as the total.
     */
    @Async
    public void sendBookingPaidReceipt(String to, Map<String, Object> event) {
        Map<String, Object> variables = new HashMap<>(event);
        variables.put("name", safe(str(event.get("name"))));
        variables.put("trackUrl", appUrl("/track/" + str(event.get("bookingId"))));
        sendEmail(to, "Payment received — booking " + str(event.get("bookingCode")) + " confirmed",
                "booking-paid-template", variables);
    }

    /** "Your professional is about N minutes away." */
    @Async
    public void sendWorkerArriving(String to, Map<String, Object> event) {
        Map<String, Object> variables = new HashMap<>(event);
        variables.put("name", safe(str(event.get("name"))));
        variables.put("trackUrl", appUrl("/track/" + str(event.get("bookingId"))));
        sendEmail(to, "Arriving in about " + str(event.get("etaMinutes")) + " minutes",
                "worker-arriving-template", variables);
    }

    /** Thank-you, rating request, and the invoice when the customer chose to pay later. */
    @Async
    public void sendBookingCompleted(String to, Map<String, Object> event) {
        Map<String, Object> variables = new HashMap<>(event);
        String bookingId = str(event.get("bookingId"));
        variables.put("name", safe(str(event.get("name"))));
        variables.put("reviewUrl", appUrl("/bookings/" + bookingId + "/review"));
        variables.put("payUrl", appUrl("/bookings/" + bookingId + "/pay"));

        String due = str(event.get("amountDue"));
        boolean invoiced = due != null && !due.isBlank();
        // Two templates, not one with a branch: the subject is a per-template row an admin edits,
        // so a single template could only ever have one subject — and it sent every pay-later
        // invoice out titled "Thanks for using our service", which does not read as a bill.
        sendEmail(to,
                invoiced
                        ? "Invoice for booking " + str(event.get("bookingCode"))
                        : "Thanks for using our service — " + str(event.get("bookingCode")),
                invoiced ? "booking-invoice-template" : "booking-completed-template", variables);
    }

    /** Absolute links, because a relative path in an email client goes nowhere. */
    private String appUrl(String path) {
        String base = appBaseUrl == null || appBaseUrl.isBlank() ? "http://localhost:3000" : appBaseUrl;
        return base.replaceAll("/+$", "") + path;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
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
