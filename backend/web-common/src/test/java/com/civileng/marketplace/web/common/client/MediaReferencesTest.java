package com.civileng.marketplace.web.common.client;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaReferencesTest {

    private final MediaClient client = mock(MediaClient.class);
    private final MediaReferences refs = new MediaReferences(client);

    private static MediaRef ref(String purpose, Long owner) {
        return new MediaRef("m1", purpose, "PUBLIC", owner, "a.png", "image/png", 10L, "http://x/a.png", null);
    }

    private static FeignException status(int code) {
        Request request = Request.create(Request.HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("get", feign.Response.builder()
                .status(code).request(request).headers(Map.of()).build());
    }

    @Test
    void acceptsTheOwnersFileOfTheRightPurpose() {
        when(client.get("m1", 7L)).thenReturn(ref("AVATAR", 7L));
        assertThat(refs.requireOwned("m1", "AVATAR", 7L).url()).isEqualTo("http://x/a.png");
    }

    @Test
    void refusesSomeoneElsesFileOrOneUploadedForAnotherPurposeWithOneMessage() {
        when(client.get("m1", 7L)).thenReturn(ref("AVATAR", 8L));
        assertThatThrownBy(() -> refs.requireOwned("m1", "AVATAR", 7L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Uploaded file not found");

        when(client.get("m1", 7L)).thenReturn(ref("PORTFOLIO", 7L));
        assertThatThrownBy(() -> refs.requireOwned("m1", "KYC_DOCUMENT", 7L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Uploaded file not found");
    }

    @Test
    void missingIdOrUserIsABadRequest() {
        assertThatThrownBy(() -> refs.requireOwned(" ", "AVATAR", 7L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> refs.requireOwned("m1", "AVATAR", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notFoundIsABadRequestAndAnOutageIsRetryable() {
        when(client.get("gone", 7L)).thenThrow(status(404));
        assertThatThrownBy(() -> refs.fresh("gone", 7L)).isInstanceOf(IllegalArgumentException.class);

        when(client.get("m1", 7L)).thenThrow(status(503));
        assertThatThrownBy(() -> refs.fresh("m1", 7L)).isInstanceOf(MediaUnavailableException.class);
    }
}
