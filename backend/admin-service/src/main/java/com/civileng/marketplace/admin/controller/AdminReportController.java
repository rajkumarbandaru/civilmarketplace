package com.civileng.marketplace.admin.controller;

import com.civileng.marketplace.admin.service.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin Reports", description = "The reports an admin can preview and export")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping
    @Operation(summary = "The catalogue of available reports")
    public ResponseEntity<Map<String, Object>> catalogue() {
        return ResponseEntity.ok(Map.of("success", true, "data", adminReportService.catalogue()));
    }

    @GetMapping("/{key}")
    @Operation(summary = "The first rows of a report, for the on-screen preview")
    public ResponseEntity<Map<String, Object>> preview(
            @PathVariable String key,
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(Map.of("success", true, "data", adminReportService.preview(key, limit)));
    }

    /**
     * The whole report as a CSV download. Served as a file rather than JSON the client turns into
     * one so that the row limit, the column order and the escaping are decided in exactly one
     * place — the same place the preview above reads from.
     */
    @GetMapping(value = "/{key}/export", produces = "text/csv")
    @Operation(summary = "Download a report as CSV")
    public ResponseEntity<byte[]> export(@PathVariable String key) {
        byte[] csv = adminReportService.csv(key).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + adminReportService.fileName(key) + "\"")
                .body(csv);
    }
}
