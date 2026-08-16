package com.civileng.marketplace.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * One turn of the Civil AI Assistant conversation.
 *
 * <p>The browser sends the prior turns back with each question rather than the service keeping a
 * session: the assistant is stateless and nothing here is worth persisting, so a server-side
 * conversation store would be a database table whose only job is to be garbage-collected.
 */
@Data
public class AiChatRequest {

    /** Capped because the whole body is forwarded to the model, which is billed by token. */
    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message must be at most 2000 characters")
    private String message;

    /**
     * Earlier turns, oldest first, excluding {@link #message}. Trimmed by the service before it
     * reaches the model — an unbounded history is how a free-tier quota disappears in an afternoon.
     */
    private List<Turn> history = List.of();

    @Data
    public static class Turn {
        /** {@code user} or {@code assistant}; anything else is treated as {@code user}. */
        private String role;
        @Size(max = 4000)
        private String text;
    }
}
