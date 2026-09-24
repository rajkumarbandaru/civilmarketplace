package com.civileng.marketplace.procurement.controller;

import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.PurchaseOrderDtos.*;
import com.civileng.marketplace.procurement.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/procurement/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "Purchase orders", description = "Approval, acknowledgement, goods receipts and invoices")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @GetMapping
    public List<PoSummary> list(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                @RequestHeader(value = "X-User-Email", required = false) String email) {
        return service.list(new Actor(userId, email));
    }

    @GetMapping("/{poId}")
    public PoDetail get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                        @RequestHeader(value = "X-User-Email", required = false) String email,
                        @PathVariable Long poId) {
        return service.get(new Actor(userId, email), poId);
    }

    @PostMapping("/{poId}/approve")
    @Operation(summary = "Approve an order above the threshold (an approver who did not raise it)")
    public PoDetail approve(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                            @RequestHeader(value = "X-User-Email", required = false) String email,
                            @PathVariable Long poId) {
        return service.approve(new Actor(userId, email), poId);
    }

    @PostMapping("/{poId}/reject")
    public PoDetail reject(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                           @RequestHeader(value = "X-User-Email", required = false) String email,
                           @PathVariable Long poId, @Valid @RequestBody(required = false) DecisionRequest request) {
        return service.reject(new Actor(userId, email), poId, request == null ? null : request.note());
    }

    @PostMapping("/{poId}/acknowledge")
    @Operation(summary = "The supplier accepts the order")
    public PoDetail acknowledge(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                @RequestHeader(value = "X-User-Email", required = false) String email,
                                @PathVariable Long poId) {
        return service.acknowledge(new Actor(userId, email), poId);
    }

    @PostMapping("/{poId}/receipts")
    @Operation(summary = "Record a goods receipt (GRN)")
    public PoDetail receive(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                            @RequestHeader(value = "X-User-Email", required = false) String email,
                            @PathVariable Long poId, @Valid @RequestBody ReceiptRequest request) {
        return service.receive(new Actor(userId, email), poId, request);
    }

    @PostMapping("/{poId}/invoices")
    @Operation(summary = "Submit a supplier invoice; the three-way match runs on it")
    public PoDetail invoice(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                            @RequestHeader(value = "X-User-Email", required = false) String email,
                            @PathVariable Long poId, @Valid @RequestBody InvoiceRequest request) {
        return service.invoice(new Actor(userId, email), poId, request);
    }

    @PostMapping("/{poId}/invoices/{invoiceId}/approve")
    public PoDetail approveInvoice(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                   @RequestHeader(value = "X-User-Email", required = false) String email,
                                   @PathVariable Long poId, @PathVariable Long invoiceId,
                                   @Valid @RequestBody(required = false) DecisionRequest request) {
        return service.decideInvoice(new Actor(userId, email), poId, invoiceId, true, request == null ? null : request.note());
    }

    @PostMapping("/{poId}/invoices/{invoiceId}/reject")
    public PoDetail rejectInvoice(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                  @RequestHeader(value = "X-User-Email", required = false) String email,
                                  @PathVariable Long poId, @PathVariable Long invoiceId,
                                  @Valid @RequestBody(required = false) DecisionRequest request) {
        return service.decideInvoice(new Actor(userId, email), poId, invoiceId, false, request == null ? null : request.note());
    }
}
