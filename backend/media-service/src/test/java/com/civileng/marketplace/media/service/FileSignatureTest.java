package com.civileng.marketplace.media.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileSignatureTest {

    static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10);
    static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0);
    static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void recognisesEachSupportedType() {
        assertThat(FileSignature.detect(JPEG)).contains("image/jpeg");
        assertThat(FileSignature.detect(PNG)).contains("image/png");
        assertThat(FileSignature.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII))).contains("image/gif");
        assertThat(FileSignature.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).contains("image/webp");
        assertThat(FileSignature.detect(PDF)).contains("application/pdf");
        assertThat(FileSignature.detect("\0\0\0\u0018ftypmp42".getBytes(StandardCharsets.US_ASCII))).contains("video/mp4");
        assertThat(FileSignature.detect(bytes(0x1A, 0x45, 0xDF, 0xA3, 1))).contains("video/webm");
    }

    @Test
    void rejectsScriptsAndUnknownOrTruncatedContent() {
        assertThat(FileSignature.detect("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(FileSignature.detect("<html><script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(FileSignature.detect(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(FileSignature.detect(new byte[0])).isEmpty();
        assertThat(FileSignature.detect(null)).isEmpty();
    }

    @Test
    void extensionComesFromTheVerifiedType() {
        assertThat(FileSignature.extensionFor("image/jpeg")).isEqualTo("jpg");
        assertThat(FileSignature.extensionFor("application/pdf")).isEqualTo("pdf");
        assertThat(FileSignature.extensionFor("text/html")).isEqualTo("bin");
    }
}
