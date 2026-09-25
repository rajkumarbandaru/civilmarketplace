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

/** OpenAI's Chat Completions API, for a workspace that brings its own OpenAI key. */
@Component
@Slf4j
public class OpenAiChatModel implements ChatModel {

    private final RestClient restClient = ChatModel.restClient();

    @Value("${app.ai.openai.endpoint:https://api.openai.com/v1/chat/completions}")
    private String endpoint;

    @Value("${app.ai.openai.model:gpt-5-mini}")
    private String defaultModel;

    @Value("${app.ai.openai.api-key:}")
    private String apiKey;

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public String platformKey() {
        return apiKey;
    }

    @Override
    public String ask(String key, String model, String instruction, List<AiChatRequest.Turn> history, String message) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", instruction));
        for (AiChatRequest.Turn turn : history) {
            messages.add(Map.of("role", "assistant".equalsIgnoreCase(turn.getRole()) ? "assistant" : "user",
                    "content", turn.getText()));
        }
        messages.add(Map.of("role", "user", "content", message));
        String chosen = model == null || model.isBlank() ? defaultModel : model.trim();
        try {
            Map<?, ?> response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", chosen, "messages", messages, "max_completion_tokens", 4096))
                    .retrieve()
                    .body(Map.class);
            String text = text(response);
            if (text == null || text.isBlank()) {
                log.warn("[CivilAI] OpenAI returned no usable answer (model={})", chosen);
                return null;
            }
            return text.trim();
        } catch (Exception e) {
            log.error("[CivilAI] OpenAI call failed (model={}): {}", chosen, e.getMessage());
            return null;
        }
    }

    /** {@code choices[0].message.content}. */
    static String text(Map<?, ?> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) return null;
        if (!(choices.get(0) instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> msg)) return null;
        Object content = msg.get("content");
        return content == null ? null : content.toString();
    }
}
