package com.civileng.marketplace.support.service;

import com.civileng.marketplace.support.dto.AiChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Google's Gemini API — the platform's default assistant model, and one a workspace can choose with
 * its own key. See {@link AiAssistant} for which workspace uses which model.
 */
@Component
@Slf4j
public class GeminiClient implements ChatModel {

    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final RestClient restClient = ChatModel.restClient();

    /** The platform's own key: the default assistant every workspace starts on. */
    @Value("${app.ai.gemini.api-key:}")
    private String apiKey;

    /** Overridable so the model can be changed by config when a free tier is retired. */
    @Value("${app.ai.gemini.model:gemini-3.6-flash}")
    private String model;

    /**
     * Tried in order when the primary model is unavailable.
     *
     * <p>Gemini answers 503 "experiencing high demand" for a model that is perfectly valid, and it
     * is not rare — a shared free tier is exactly where that surfaces. Without a second model the
     * user is told to raise a support ticket over a condition that clears by itself, so a busy
     * model falls through to the next rather than failing the question.
     */
    @Value("${app.ai.gemini.fallback-models:gemini-3.5-flash,gemini-flash-latest}")
    private String fallbackModels;

    @Override
    public String provider() {
        return "gemini";
    }

    @Override
    public String platformKey() {
        return apiKey;
    }

    /**
     * @return the model's answer, or null when the call failed — the caller decides what the user
     *         sees, because "the assistant is down" is a support message, not an API detail
     */
    @Override
    public String ask(String key, String requestedModel, String instruction, List<AiChatRequest.Turn> history,
                      String message) {
        List<Map<String, Object>> contents = new ArrayList<>();
        String primary = requestedModel == null || requestedModel.isBlank() ? model : requestedModel.trim();

        if (history != null) {
            for (AiChatRequest.Turn turn : history) {
                // Gemini names the assistant role "model"; anything not explicitly the assistant
                // is attributed to the user, so a malformed role cannot put words in our mouth.
                String role = "assistant".equalsIgnoreCase(turn.getRole()) ? "model" : "user";
                contents.add(Map.of("role", role,
                        "parts", List.of(Map.of("text", turn.getText()))));
            }
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", message))));

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", instruction))),
                "contents", contents,
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 4096));

        // Primary first, then the fallbacks, so a busy model costs a retry rather than the answer.
        List<String> candidates = new ArrayList<>();
        candidates.add(primary);
        for (String fallback : fallbackModels.split(",")) {
            String trimmed = fallback.trim();
            if (!trimmed.isEmpty() && !trimmed.equals(primary)) candidates.add(trimmed);
        }

        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            try {
                Map<?, ?> response = restClient.post()
                        // In a header, not the query string: a URL ends up in exception messages,
                        // and the catch below logs those.
                        .uri(String.format(ENDPOINT_TEMPLATE, candidate))
                        .header("x-goog-api-key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(Map.class);

                String text = firstCandidateText(response);
                if (text == null || text.isBlank()) {
                    // Most often a safety block: the response carries a promptFeedback block and no
                    // candidates at all, which is a well-formed 200 rather than an error. Another
                    // model would block it too, so this is not worth a retry.
                    log.warn("[CivilAI] Gemini returned no usable candidate (model={}): {}",
                            candidate, response);
                    return null;
                }
                if (i > 0) {
                    log.info("[CivilAI] Answered with fallback model {} after {} was unavailable",
                            candidate, primary);
                }
                return text.trim();
            } catch (Exception e) {
                // 400 is a bad key, 429 is the quota, 404 a retired model name, 503 a model under
                // load. Only the last two are worth trying another model for — a bad key and an
                // exhausted quota fail identically everywhere.
                log.error("[CivilAI] Gemini call failed (model={}): {}", candidate, e.getMessage());
                if (!worthAnotherModel(e)) return null;
            }
        }
        return null;
    }

    /**
     * Whether a different model might succeed where this one did not. Retried on 503 (the model is
     * busy) and 404 (this name has been retired), because both are about the model rather than the
     * request; everything else would fail the same way on the next one.
     */
    private boolean worthAnotherModel(Exception e) {
        if (e instanceof org.springframework.web.client.HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            return status == 503 || status == 404;
        }
        // A read timeout or a dropped connection says nothing about the model's validity, and a
        // second attempt is cheap next to telling someone to raise a ticket.
        return e instanceof org.springframework.web.client.ResourceAccessException;
    }

    /** Digs {@code candidates[0].content.parts[*].text} out of the untyped response. */
    @SuppressWarnings("unchecked")
    private String firstCandidateText(Map<?, ?> response) {
        if (response == null) return null;
        Object candidates = response.get("candidates");
        if (!(candidates instanceof List<?> list) || list.isEmpty()) return null;
        if (!(list.get(0) instanceof Map<?, ?> candidate)) return null;
        if (!(candidate.get("content") instanceof Map<?, ?> content)) return null;
        if (!(content.get("parts") instanceof List<?> parts)) return null;

        // Long answers can arrive split across several parts, so they are joined rather than
        // taking parts[0] and silently truncating mid-sentence.
        return ((List<Object>) parts).stream()
                .filter(Map.class::isInstance)
                .map(part -> ((Map<?, ?>) part).get("text"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .reduce("", String::concat);
    }
}
