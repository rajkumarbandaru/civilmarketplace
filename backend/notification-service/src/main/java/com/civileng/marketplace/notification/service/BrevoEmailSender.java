package com.civileng.marketplace.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

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

    private final RestClient restClient = RestClient.create();

    @Value("${app.email.brevo.api-key:}")
    private String apiKey;

    public boolean isConfigured() {
        return apiKey != null && apiKey.startsWith(KEY_PREFIX);
    }

    /**
     * @return true when Brevo accepted the message for delivery
     */
    public boolean send(String fromAddress, String fromName, String to,
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
            return true;
        } catch (Exception e) {
            // A 401 means a bad key; a 400 almost always means `fromAddress` is not a
            // verified sender on the account. Both are configuration, not transient.
            log.error("[Email:brevo] failed to send to {}: {}", to, e.getMessage());
            return false;
        }
    }
}
