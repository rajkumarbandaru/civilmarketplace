package com.civileng.marketplace.support.service;

import com.civileng.marketplace.support.dto.AiChatRequest;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/** One AI provider's chat API, as the Civil AI Assistant uses it. */
public interface ChatModel {

    /** The provider key, as stored on a workspace's AI integration ({@code gemini}, {@code openai}, …). */
    String provider();

    /** The platform's own key for this provider from configuration; blank when it has none. */
    String platformKey();

    /**
     * @param model   the model to use, or null for this provider's configured default
     * @param history earlier turns, oldest first, already trimmed and cleaned
     * @return the answer, or null when the call failed — the caller decides what the user sees
     */
    String ask(String apiKey, String model, String instruction, List<AiChatRequest.Turn> history, String message);

    /**
     * A client with timeouts. Not optional: the call happens while someone watches a "Thinking…"
     * indicator, and the default factory would wait forever on a hung connection.
     */
    static RestClient restClient() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(90).toMillis());
        return RestClient.builder().requestFactory(factory).build();
    }
}
