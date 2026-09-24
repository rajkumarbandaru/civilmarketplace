package com.civileng.marketplace.user.controller;

import com.civileng.marketplace.user.dto.MediaDtos;
import com.civileng.marketplace.user.model.KycDocument;
import com.civileng.marketplace.user.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "KYC document submission APIs")
public class KycController {

    private final KycService kycService;

    @PostMapping
    @Operation(summary = "Submit a KYC document for review")
    public ResponseEntity<KycDocument> submitDocument(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody MediaDtos.KycSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kycService.submitDocument(userId, request));
    }

    @GetMapping("/{documentId}/file")
    @Operation(summary = "A short-lived link to open one of my KYC documents")
    public ResponseEntity<MediaDtos.FileLink> getMyFile(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long documentId) {
        return ResponseEntity.ok(kycService.ownFileLink(documentId, userId));
    }

    @GetMapping
    @Operation(summary = "Get my submitted KYC documents")
    public ResponseEntity<List<KycDocument>> getMyDocuments(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(kycService.getMyDocuments(userId));
    }
}
