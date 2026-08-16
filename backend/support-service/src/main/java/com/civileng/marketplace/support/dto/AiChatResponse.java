package com.civileng.marketplace.support.dto;

/**
 * The assistant's reply.
 *
 * @param reply     the model's answer, or an explanation of why there is none
 * @param available false when the assistant is switched off or unconfigured, so the panel can say
 *                  so plainly instead of rendering the fallback text as if the model had said it
 */
public record AiChatResponse(String reply, boolean available) {

    public static AiChatResponse of(String reply) {
        return new AiChatResponse(reply, true);
    }

    public static AiChatResponse unavailable(String reason) {
        return new AiChatResponse(reason, false);
    }
}
