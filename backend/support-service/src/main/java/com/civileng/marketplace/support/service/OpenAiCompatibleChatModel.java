package com.civileng.marketplace.support.service;

import com.civileng.marketplace.support.dto.AiChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Any server speaking OpenAI's Chat Completions API, for a workspace running an open model: Ollama,
 * vLLM or LM Studio on its own machine, or a host such as Groq, OpenRouter, Together, DeepSeek or
 * Mistral. The workspace gives the base URL and model; the key is optional (a local server has none).
 *
 * <p>The base URL is typed in by a workspace admin, so it is checked before every call rather than
 * trusted: an address inside the platform's own network (a Docker service name, loopback, a private
 * range) would otherwise let a workspace make this service call vault, MySQL or another tenant's
 * service. Private addresses are allowed only when the platform opts in, for self-hosted models on
 * the platform's own infrastructure.
 */
@Component
@Slf4j
public class OpenAiCompatibleChatModel implements ChatModel {

    private final RestClient restClient = ChatModel.restClient();

    @Value("${app.ai.open-models.allow-private-networks:false}")
    private boolean allowPrivateNetworks;

    @Override
    public String provider() {
        return "openai_compatible";
    }

    /** No platform account: an open model is always a workspace's own server. */
    @Override
    public String platformKey() {
        return null;
    }

    @Override
    public boolean requiresKey() {
        return false;
    }

    @Override
    public String ask(String apiKey, String model, String instruction, List<AiChatRequest.Turn> history, String message) {
        log.warn("[CivilAI] Open-model provider asked without its base URL");
        return null;
    }

    @Override
    public String ask(String apiKey, String model, Map<String, String> settings, String instruction,
                      List<AiChatRequest.Turn> history, String message) {
        String endpoint;
        try {
            endpoint = endpoint(settings.get("baseUrl"), allowPrivateNetworks);
        } catch (IllegalArgumentException e) {
            log.warn("[CivilAI] Open-model base URL refused: {}", e.getMessage());
            return null;
        }
        if (model == null || model.isBlank()) {
            log.warn("[CivilAI] Open-model provider has no model configured");
            return null;
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", instruction));
        for (AiChatRequest.Turn turn : history) {
            messages.add(Map.of("role", "assistant".equalsIgnoreCase(turn.getRole()) ? "assistant" : "user",
                    "content", turn.getText()));
        }
        messages.add(Map.of("role", "user", "content", message));
        Map<String, Object> body = new HashMap<>();
        body.put("model", model.trim());
        body.put("messages", messages);
        // max_tokens, not OpenAI's newer max_completion_tokens: it is the one every compatible
        // server understands.
        body.put("max_tokens", 4096);
        try {
            RestClient.RequestBodySpec request = restClient.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                request = request.header("Authorization", "Bearer " + apiKey);
            }
            String text = OpenAiChatModel.text(request.body(body).retrieve().body(Map.class));
            if (text == null || text.isBlank()) {
                log.warn("[CivilAI] Open model returned no usable answer (model={})", model);
                return null;
            }
            return text.trim();
        } catch (Exception e) {
            log.error("[CivilAI] Open-model call failed (model={}): {}", model, e.getMessage());
            return null;
        }
    }

    /**
     * {@code baseUrl} + {@code /chat/completions}, after checking the address is one this service may
     * call. Accepts a base with or without the path already on it.
     */
    static String endpoint(String baseUrl, boolean allowPrivateNetworks) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("no base URL");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("not a URL: " + baseUrl);
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("only http(s) URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("no host in " + baseUrl);
        }
        if (!allowPrivateNetworks && isInternal(host)) {
            throw new IllegalArgumentException(host + " is inside a private network");
        }
        String base = baseUrl.trim().replaceAll("/+$", "");
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    /**
     * A single-label name (a Docker service such as {@code vault}), or an address in loopback,
     * link-local, private or wildcard ranges. Anything that does not resolve is treated as internal:
     * refusing a name that cannot be checked is safer than calling it.
     */
    static boolean isInternal(String host) {
        String h = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (!h.contains(".") && !h.contains(":")) {
            return true;
        }
        if (h.equalsIgnoreCase("localhost") || h.toLowerCase().endsWith(".localhost")
                || h.toLowerCase().endsWith(".internal") || h.toLowerCase().endsWith(".local")) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(h)) {
                if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress()
                        || isUniqueLocalV6(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true;
        }
    }

    /** fc00::/7, IPv6's private range, which {@link InetAddress#isSiteLocalAddress} does not cover. */
    private static boolean isUniqueLocalV6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
