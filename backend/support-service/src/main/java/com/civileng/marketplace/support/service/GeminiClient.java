package com.civileng.marketplace.support.service;

import com.civileng.marketplace.support.dto.AiChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calls Google's Gemini API for the Civil AI Assistant.
 *
 * <p>This lives server-side for one reason: the API key. A key placed in any {@code VITE_} variable
 * is inlined into the JavaScript bundle and handed to every visitor, so the browser never talks to
 * Gemini directly — it talks to this service, which holds the key and is the only thing that can
 * spend the quota.
 *
 * <p>Distinct from {@code SupportChatWidget}'s scripted FAQ on the frontend, which stays as-is: that
 * one cannot state a policy we do not honour, this one can, which is why the system prompt below
 * forbids it from inventing prices, refund windows or timelines.
 */
@Component
@Slf4j
public class GeminiClient {

    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    /**
     * How many prior turns are forwarded. Enough for a follow-up question to make sense
     * ("what about cancelling it?") without letting a long session grow the per-request token
     * count without bound — the free tier is quota-limited per minute and per day.
     */
    private static final int MAX_HISTORY_TURNS = 12;

    /**
     * The assistant's brief: a construction estimator that must show its assumptions, defer
     * structural design to a qualified engineer, and never pass an invented rate off as a market
     * price. It lives in a resource file rather than a string constant because it is product copy
     * that is edited far more often than this class, and a text file diffs readably.
     */
    private static final String PROMPT_RESOURCE = "ai/civil-assistant-prompt.txt";

    private static final String SYSTEM_PROMPT = loadPrompt();

    /**
     * Appended when the site rate card could not be built. Without it the model fills the "site
     * rate" column from nowhere, which is the exact failure the card exists to prevent.
     */
    private static final String NO_SITE_RATES = """


            LIVE SITE DATA
            Site rates are unavailable for this answer — no registered provider rates could be read.
            Say so plainly wherever a site rate would have appeared, leave those cells as
            "not available", and give the market/actual side of the estimate only.
            """;

    private static String loadPrompt() {
        var resource = new org.springframework.core.io.ClassPathResource(PROMPT_RESOURCE);
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Failing the whole service over a missing prompt would take the support APIs down
            // with it, so the assistant falls back to a minimal brief and says so loudly.
            log.error("[CivilAI] Could not read {} — falling back to a minimal prompt", PROMPT_RESOURCE, e);
            return "You are a professional civil engineering and construction assistant. "
                    + "State your assumptions, never invent market rates, and recommend a "
                    + "qualified structural engineer verifies any structural guidance.";
        }
    }

    private final RestClient restClient = RestClient.builder()
            .requestFactory(timeoutFactory())
            .build();

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

    @Value("${app.ai.enabled:true}")
    private boolean enabled;

    /**
     * A timeout is not optional here: this call happens while someone watches a "Thinking…"
     * indicator, and the default factory would wait indefinitely on a hung connection, holding a
     * request thread with it.
     */
    private static org.springframework.http.client.ClientHttpRequestFactory timeoutFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(90).toMillis());
        return factory;
    }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /**
     * @return the model's answer, or null when the call failed — the caller decides what the user
     *         sees, because "the assistant is down" is a support message, not an API detail
     */
    public String ask(String message, List<AiChatRequest.Turn> history, String siteRateCard) {
        List<Map<String, Object>> contents = new ArrayList<>();

        // The rate card rides in the system instruction, not in the conversation: it is data the
        // assistant is given, and a turn in the transcript would let a later message argue with it
        // or be mistaken for something the user said.
        String instruction = siteRateCard == null || siteRateCard.isBlank()
                ? SYSTEM_PROMPT + NO_SITE_RATES
                : SYSTEM_PROMPT + "\n\nLIVE SITE DATA\n" + siteRateCard;

        if (history != null) {
            // Oldest turns are dropped, not newest: the recent exchange is what a follow-up
            // question actually depends on.
            List<AiChatRequest.Turn> recent = history.size() > MAX_HISTORY_TURNS
                    ? history.subList(history.size() - MAX_HISTORY_TURNS, history.size())
                    : history;
            for (AiChatRequest.Turn turn : recent) {
                if (turn == null || turn.getText() == null || turn.getText().isBlank()) continue;
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
        candidates.add(model);
        for (String fallback : fallbackModels.split(",")) {
            String trimmed = fallback.trim();
            if (!trimmed.isEmpty() && !trimmed.equals(model)) candidates.add(trimmed);
        }

        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            try {
                Map<?, ?> response = restClient.post()
                        .uri(String.format(ENDPOINT_TEMPLATE, candidate, apiKey))
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
                            candidate, model);
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
