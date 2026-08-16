package com.civileng.marketplace.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends email through Brevo's HTTP API rather than SMTP.
 *
 * <p>Why both exist: Brevo's SMTP relay needs a login *and* an SMTP key, where the login is a
 * separate value from the dashboard. The HTTP API needs only the API key, so this path can be
 * configured from a single secret — set {@code app.email.provider=brevo}.
 *
 * <p>The sender address must still be a verified sender on the Brevo account; Brevo rejects
 * anything else with a 400, which is surfaced in the log rather than thrown.
 */
@Component
@Slf4j
public class BrevoEmailSender {

    private static final String ENDPOINT = "https://api.brevo.com/v3/smtp/email";
    /** Live keys start with this; the `.env` template does not. */
    private static final String KEY_PREFIX = "xkeysib-";
    private static final Pattern MESSAGE_ID = Pattern.compile("\"messageId\"\\s*:\\s*\"([^\"]+)\"");

    private final RestClient restClient = RestClient.create();

    @Value("${app.email.brevo.api-key:}")
    private String apiKey;

    public boolean isConfigured() {
        return apiKey != null && apiKey.startsWith(KEY_PREFIX);
    }

    /**
     * The outcome of one API call.
     *
     * <p>{@code messageId} is what the delivery webhook later arrives under, so it is the join key
     * between a row in {@code email_log} and Brevo's view of the same message.
     */
    public record SendResult(boolean accepted, String messageId, String error) {

        static SendResult ok(String messageId) {
            return new SendResult(true, messageId, null);
        }

        static SendResult failed(String error) {
            return new SendResult(false, null, error);
        }
    }

    public SendResult send(String fromAddress, String fromName, String to,
                           String subject, String htmlContent) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("email", fromAddress, "name", fromName),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "htmlContent", htmlContent);

        try {
            String response = restClient.post()
                    .uri(ENDPOINT)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("[Email:brevo] sent to={} subject={} response={}", to, subject, response);
            return SendResult.ok(extractMessageId(response));
        } catch (Exception e) {
            // A 401 means a bad key; a 400 almost always means `fromAddress` is not a
            // verified sender on the account. Both are configuration, not transient.
            log.error("[Email:brevo] failed to send to {}: {}", to, e.getMessage());
            return SendResult.failed(e.getMessage());
        }
    }

    /**
     * Pulls {@code messageId} out of Brevo's {@code {"messageId":"<...@domain>"}} response.
     *
     * <p>Read with a regex rather than a JSON binding because the id is the only field we want and
     * a shape change elsewhere in the payload should cost us the correlation id, not the send.
     */
    private static String extractMessageId(String response) {
        if (response == null) {
            return null;
        }
        Matcher matcher = MESSAGE_ID.matcher(response);
        return matcher.find() ? matcher.group(1) : null;
    }
}
