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

/** Anthropic's Messages API (Claude), for a workspace that brings its own Anthropic key. */
@Component
@Slf4j
public class AnthropicChatModel implements ChatModel {

    private final RestClient restClient = ChatModel.restClient();

    @Value("${app.ai.anthropic.endpoint:https://api.anthropic.com/v1/messages}")
    private String endpoint;

    @Value("${app.ai.anthropic.model:claude-sonnet-5}")
    private String defaultModel;

    @Value("${app.ai.anthropic.api-key:}")
    private String apiKey;

    @Override
    public String provider() {
        return "anthropic";
    }

    @Override
    public String platformKey() {
        return apiKey;
    }

    @Override
    public String ask(String key, String model, String instruction, List<AiChatRequest.Turn> history, String message) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (AiChatRequest.Turn turn : history) {
            messages.add(Map.of("role", "assistant".equalsIgnoreCase(turn.getRole()) ? "assistant" : "user",
                    "content", turn.getText()));
        }
        messages.add(Map.of("role", "user", "content", message));
        String chosen = model == null || model.isBlank() ? defaultModel : model.trim();
        try {
            Map<?, ?> response = restClient.post()
                    .uri(endpoint)
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", chosen, "system", instruction, "messages", messages,
                            "max_tokens", 4096, "temperature", 0.3))
                    .retrieve()
                    .body(Map.class);
            String text = text(response);
            if (text == null || text.isBlank()) {
                log.warn("[CivilAI] Anthropic returned no usable answer (model={})", chosen);
                return null;
            }
            return text.trim();
        } catch (Exception e) {
            log.error("[CivilAI] Anthropic call failed (model={}): {}", chosen, e.getMessage());
            return null;
        }
    }

    /** The text blocks of {@code content}, joined. */
    static String text(Map<?, ?> response) {
        if (response == null || !(response.get("content") instanceof List<?> blocks)) return null;
        return blocks.stream()
                .filter(Map.class::isInstance)
                .map(b -> (Map<?, ?>) b)
                .filter(b -> "text".equals(b.get("type")))
                .map(b -> b.get("text"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .reduce("", String::concat);
    }
}
