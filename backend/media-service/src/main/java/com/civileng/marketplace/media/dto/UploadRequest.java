package com.civileng.marketplace.media.dto;

import com.civileng.marketplace.media.model.MediaPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UploadRequest(
        @NotNull MediaPurpose purpose,
        @NotBlank @Size(max = 255) String filename,
        @NotBlank String contentType,
        @NotNull @Positive Long sizeBytes
) { }
