package com.civileng.marketplace.support.controller;

import com.civileng.marketplace.support.dto.AiChatRequest;
import com.civileng.marketplace.support.dto.AiChatResponse;
import com.civileng.marketplace.support.service.GeminiClient;
import com.civileng.marketplace.support.service.SiteRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * The Civil AI Assistant endpoint behind the header assistant.
 *
 * <p>It sits under {@code /api/v1/support/**}, which the gateway already routes here through the
 * JwtAuth filter — so callers are authenticated and {@code X-User-Id} is always present. That is
 * deliberate rather than incidental: every question spends a shared, finite free-tier quota, and an
 * endpoint open to anonymous traffic is one that a single script can exhaust for every user.
 */
@RestController
@RequestMapping("/api/v1/support/ai")
@RequiredArgsConstructor
@Tag(name = "Civil AI Assistant", description = "LLM-backed assistant")
public class AiAssistantController {

    /** Per-user ceiling, sized to sit well inside Gemini's free-tier per-minute allowance. */
    private static final int MAX_REQUESTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final GeminiClient gemini;
    private final SiteRateService siteRates;

    /**
     * Recent request times per user.
     *
     * <p>In-memory, so it is per-instance and resets on restart. That is the right trade here: the
     * point is to stop one person's runaway loop from draining a shared quota, not to enforce
     * billing, and a Redis round-trip per question would cost more than the limit is worth.
     */
    private final Map<String, Deque<Instant>> recentByUser = new ConcurrentHashMap<>();

    @GetMapping("/status")
    @Operation(summary = "Whether the assistant is configured and usable")
    public ResponseEntity<Map<String, Object>> status() {
        // The panel asks this on open so it can show a plain "not configured" state instead of
        // letting the user type a question and only then discover there is nothing behind it.
        return ResponseEntity.ok(Map.of(
                "available", gemini.isConfigured(),
                "siteRates", siteRates.status()));
    }

    @PostMapping("/chat")
    @Operation(summary = "Ask the assistant a question")
    public ResponseEntity<AiChatResponse> chat(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody AiChatRequest request) {

        if (!gemini.isConfigured()) {
            return ResponseEntity.ok(AiChatResponse.unavailable(
                    "The Civil AI Assistant is not switched on for this environment yet. "
                            + "You can still raise a support ticket and a person will reply."));
        }

        if (!allow(userId)) {
            return ResponseEntity.ok(AiChatResponse.unavailable(
                    "That is a lot of questions at once — give it a minute and try again."));
        }

        // Site rates are resolved per question rather than baked into the prompt: providers
        // sign up and change their rates while the service is running.
        String reply = gemini.ask(request.getMessage(), request.getHistory(), siteRates.rateCard());
        if (reply == null) {
            // 200 with available=false rather than a 5xx: the panel renders this as a message in
            // the thread, and an error status would send the frontend's axios interceptor down the
            // token-refresh path for something that has nothing to do with the session.
            return ResponseEntity.ok(AiChatResponse.unavailable(
                    "I could not reach the assistant just then. Please try again, "
                            + "or raise a support ticket if it keeps happening."));
        }
        return ResponseEntity.ok(AiChatResponse.of(reply));
    }

    /** Sliding window: drops timestamps older than {@link #WINDOW}, then admits if under the cap. */
    private boolean allow(String userId) {
        Deque<Instant> hits = recentByUser.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minus(WINDOW);
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst().isBefore(cutoff)) {
                hits.pollFirst();
            }
            if (hits.size() >= MAX_REQUESTS) return false;
            hits.addLast(Instant.now());
            return true;
        }
    }
}
