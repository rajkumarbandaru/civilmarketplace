package com.civileng.marketplace.user.dto;

import com.civileng.marketplace.user.model.KycDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/** Request and response shapes for endpoints that attach an uploaded file. */
public final class MediaDtos {

    private MediaDtos() {
    }

    /** A KYC submission. The file is referenced by its upload id, never by a URL the client sends. */
    public record KycSubmitRequest(
            @NotNull KycDocument.DocumentType documentType,
            @Size(max = 100) String documentNumber,
            @NotBlank String mediaId) {
    }

    public record PortfolioRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 2000) String description,
            @Size(max = 100) String category,
            @PastOrPresent LocalDate completionDate,
            @NotBlank String mediaId) {
    }

    /** A link to open a stored file; for private files it stops working at {@code expiresAt}. */
    public record FileLink(String url, Instant expiresAt, String filename, String contentType) {
    }
}
