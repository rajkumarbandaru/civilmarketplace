package com.civileng.marketplace.support.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleChatModelTest {

    @Test
    void addsTheChatCompletionsPathOnce() {
        assertThat(OpenAiCompatibleChatModel.endpoint("http://10.0.0.5:11434/v1", true))
                .isEqualTo("http://10.0.0.5:11434/v1/chat/completions");
        assertThat(OpenAiCompatibleChatModel.endpoint("http://10.0.0.5:11434/v1/chat/completions/", true))
                .isEqualTo("http://10.0.0.5:11434/v1/chat/completions");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://vault:8200/v1", "http://localhost:11434/v1", "http://127.0.0.1/v1",
            "http://10.1.2.3/v1", "http://192.168.1.10/v1", "http://169.254.169.254/latest",
            "http://[::1]:8080/v1", "http://ollama.internal/v1", "http://mysql.local/v1"})
    void refusesAddressesInsideThePlatformsNetworkByDefault(String url) {
        assertThatThrownBy(() -> OpenAiCompatibleChatModel.endpoint(url, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void privateAddressesAreAllowedWhenThePlatformOptsIn() {
        assertThat(OpenAiCompatibleChatModel.endpoint("http://10.1.2.3:11434/v1", true))
                .isEqualTo("http://10.1.2.3:11434/v1/chat/completions");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/v1", "file:///etc/passwd", "not a url", ""})
    void onlyHttpUrlsAreAccepted(String url) {
        assertThatThrownBy(() -> OpenAiCompatibleChatModel.endpoint(url, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnresolvableHostIsTreatedAsInternal() {
        assertThat(OpenAiCompatibleChatModel.isInternal("does-not-exist.invalid")).isTrue();
    }

    @Test
    void aPublicAddressIsAllowed() {
        assertThat(OpenAiCompatibleChatModel.isInternal("8.8.8.8")).isFalse();
    }
}
