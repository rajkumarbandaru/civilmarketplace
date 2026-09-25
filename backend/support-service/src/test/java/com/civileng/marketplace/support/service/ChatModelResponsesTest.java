package com.civileng.marketplace.support.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatModelResponsesTest {

    @Test
    void openAiAnswersComeFromTheFirstChoice() {
        assertThat(OpenAiChatModel.text(Map.of("choices", List.of(
                Map.of("message", Map.of("role", "assistant", "content", "Use M25 concrete.")))))).isEqualTo("Use M25 concrete.");
        assertThat(OpenAiChatModel.text(Map.of("choices", List.of()))).isNull();
        assertThat(OpenAiChatModel.text(null)).isNull();
    }

    @Test
    void anthropicAnswersJoinTheTextBlocks() {
        assertThat(AnthropicChatModel.text(Map.of("content", List.of(
                Map.of("type", "text", "text", "Part one. "),
                Map.of("type", "tool_use", "id", "x"),
                Map.of("type", "text", "text", "Part two."))))).isEqualTo("Part one. Part two.");
        assertThat(AnthropicChatModel.text(Map.of())).isNull();
    }
}
