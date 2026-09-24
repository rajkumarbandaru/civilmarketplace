package com.civileng.marketplace.media.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * What a file actually is, read from its first bytes rather than trusted from its name or the
 * Content-Type the browser sent — both of which the uploader controls completely.
 */
public final class FileSignature {

    /** Enough for every signature below. */
    public static final int HEAD_BYTES = 16;

    private FileSignature() {
    }

    public static Optional<String> detect(byte[] head) {
        if (head == null) return Optional.empty();
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) return Optional.of("image/jpeg");
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return Optional.of("image/png");
        if (ascii(head, 0, "GIF87a") || ascii(head, 0, "GIF89a")) return Optional.of("image/gif");
        if (ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP")) return Optional.of("image/webp");
        if (ascii(head, 0, "%PDF-")) return Optional.of("application/pdf");
        if (ascii(head, 4, "ftyp")) return Optional.of("video/mp4");
        if (startsWith(head, 0x1A, 0x45, 0xDF, 0xA3)) return Optional.of("video/webm");
        return Optional.empty();
    }

    /** The extension an object key gets for a (verified) content type. */
    public static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            default -> "bin";
        };
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }

    private static boolean ascii(byte[] data, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        return data.length >= offset + expected.length
                && Arrays.equals(data, offset, offset + expected.length, expected, 0, expected.length);
    }
}
