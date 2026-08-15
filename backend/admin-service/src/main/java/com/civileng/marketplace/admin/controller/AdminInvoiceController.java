package com.civileng.marketplace.admin.controller;

import com.civileng.marketplace.admin.service.AdminInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/invoices")
@RequiredArgsConstructor
@Tag(name = "Admin Invoices", description = "Invoices billed to customers, with their payment state")
public class AdminInvoiceController {

    private final AdminInvoiceService adminInvoiceService;

    @GetMapping
    @Operation(summary = "List invoices, newest first")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(adminInvoiceService.getInvoices(page, size, status, search));
    }

    @PostMapping
    @Operation(summary = "Raise an invoice against a booking")
    public ResponseEntity<Map<String, Object>> raise(@RequestBody Map<String, Object> command) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminInvoiceService.raiseInvoice(command));
    }

    @GetMapping("/summary")
    @Operation(summary = "Billed, collected, outstanding and refunded totals")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(adminInvoiceService.getSummary());
    }

    @GetMapping("/{invoiceNumber}")
    @Operation(summary = "One invoice with its billing lines")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String invoiceNumber) {
        return ResponseEntity.ok(adminInvoiceService.getInvoice(invoiceNumber));
    }
}
