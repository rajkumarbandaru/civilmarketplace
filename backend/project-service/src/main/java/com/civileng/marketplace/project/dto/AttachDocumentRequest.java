package com.civileng.marketplace.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Registers an already-uploaded object-storage reference. The bytes never pass through this
 * service — same signed-URL pattern as KycDocument (ENT·01 FR-06).
 */
@Data
public class AttachDocumentRequest {

    @NotBlank(message = "File reference is required")
    private String fileRef;

    @NotBlank(message = "Document type is required")
    private String docType;

    private String fileName;
}
