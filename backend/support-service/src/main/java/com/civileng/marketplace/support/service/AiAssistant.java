package com.civileng.marketplace.support.service;

import com.civileng.marketplace.support.dto.AiChatRequest;
import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import com.civileng.marketplace.tenant.common.integration.ResolvedIntegration;
import com.civileng.marketplace.tenant.common.integration.TenantIntegrationResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Civil AI Assistant: which model answers for the current workspace, with what key.
 *
 * <p>Every workspace starts on the platform's own assistant (Gemini, with the platform's key). A
 * workspace can bring its own account with any provider it prefers — Gemini, OpenAI or Anthropic —
 * and optionally pick the model; or switch the assistant off.
 *
 * <p>This lives server-side for one reason: the API key. A key placed in any {@code VITE_} variable
 * is inlined into the JavaScript bundle and handed to every visitor, so the browser never talks to
 * a provider directly — it talks to this service, which holds the key.
 *
 * <p>Distinct from {@code SupportChatWidget}'s scripted FAQ on the frontend: that one cannot state
 * a policy we do not honour, this one can, which is why the system prompt forbids it from inventing
 * prices, refund windows or timelines.
 */
@Component
@Slf4j
public class AiAssistant {

    /**
     * How many prior turns are forwarded. Enough for a follow-up question to make sense
     * ("what about cancelling it?") without letting a long session grow the per-request token
     * count without bound.
     */
    static final int MAX_HISTORY_TURNS = 12;

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

    private final TenantIntegrationResolver integrationResolver;
    private final Map<String, ChatModel> models;

    /** The provider the platform's own assistant runs on. */
    @Value("${app.ai.platform-provider:gemini}")
    private String platformProvider = "gemini";

    @Value("${app.ai.enabled:true}")
    private boolean enabled = true;

    public AiAssistant(TenantIntegrationResolver integrationResolver, List<ChatModel> models) {
        this.integrationResolver = integrationResolver;
        this.models = models.stream().collect(Collectors.toMap(ChatModel::provider, Function.identity()));
    }

    /** Who answers for the current workspace: provider, model (null = its default) and key. */
    public record Route(ChatModel model, String modelName, String apiKey) { }

    public Optional<Route> route() {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<ResolvedIntegration> resolved = integrationResolver.find(IntegrationCapability.AI);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        ResolvedIntegration ai = resolved.get();
        ChatModel model = models.get(ai.usesPlatformCredentials() ? platformProvider : ai.provider());
        if (model == null) {
            log.warn("[CivilAI] No adapter for AI provider '{}'", ai.provider());
            return Optional.empty();
        }
        String key = ai.usesPlatformCredentials() ? model.platformKey() : ai.secret("apiKey");
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Route(model, ai.usesPlatformCredentials() ? null : ai.setting("model"), key));
    }

    /** Whether the current workspace has an assistant to use. */
    public boolean isConfigured() {
        return route().isPresent();
    }

    /** The provider answering for the current workspace, or null when there is none. */
    public String provider() {
        return route().map(r -> r.model().provider()).orElse(null);
    }

    /**
     * @return the model's answer, or null when there is no assistant or the call failed — the
     *         caller decides what the user sees
     */
    public String ask(String message, List<AiChatRequest.Turn> history, String siteRateCard) {
        Optional<Route> route = route();
        if (route.isEmpty()) {
            return null;
        }
        // The rate card rides in the system instruction, not in the conversation: it is data the
        // assistant is given, and a turn in the transcript would let a later message argue with it
        // or be mistaken for something the user said.
        String instruction = siteRateCard == null || siteRateCard.isBlank()
                ? SYSTEM_PROMPT + NO_SITE_RATES
                : SYSTEM_PROMPT + "\n\nLIVE SITE DATA\n" + siteRateCard;
        return route.get().model().ask(route.get().apiKey(), route.get().modelName(), instruction,
                recent(history), message);
    }

    /** The last {@link #MAX_HISTORY_TURNS} non-empty turns: the recent exchange is what a follow-up depends on. */
    static List<AiChatRequest.Turn> recent(List<AiChatRequest.Turn> history) {
        if (history == null) {
            return List.of();
        }
        List<AiChatRequest.Turn> usable = history.stream()
                .filter(t -> t != null && t.getText() != null && !t.getText().isBlank())
                .toList();
        return usable.size() > MAX_HISTORY_TURNS
                ? usable.subList(usable.size() - MAX_HISTORY_TURNS, usable.size())
                : usable;
    }
}
