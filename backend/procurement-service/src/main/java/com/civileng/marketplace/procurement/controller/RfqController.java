package com.civileng.marketplace.procurement.controller;

import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.PurchaseOrderDtos.PoDetail;
import com.civileng.marketplace.procurement.dto.RfqDtos.*;
import com.civileng.marketplace.procurement.service.RfqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/procurement/rfqs")
@RequiredArgsConstructor
@Tag(name = "RFQs", description = "Requests for quotation and quotations")
public class RfqController {

    private final RfqService service;

    @GetMapping
    @Operation(summary = "RFQs the caller's organizations raised or were invited to")
    public List<RfqSummary> list(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                 @RequestHeader(value = "X-User-Email", required = false) String email) {
        return service.list(new Actor(userId, email));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send an RFQ to chosen suppliers")
    public RfqDetail create(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                            @RequestHeader(value = "X-User-Email", required = false) String email,
                            @Valid @RequestBody CreateRfqRequest request) {
        return service.create(new Actor(userId, email), request);
    }

    @GetMapping("/{rfqId}")
    public RfqDetail get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                         @RequestHeader(value = "X-User-Email", required = false) String email,
                         @PathVariable Long rfqId) {
        return service.get(new Actor(userId, email), rfqId);
    }

    @PostMapping("/{rfqId}/quotations")
    @Operation(summary = "Submit or revise a quotation (an invited supplier)")
    public RfqDetail quote(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                           @RequestHeader(value = "X-User-Email", required = false) String email,
                           @PathVariable Long rfqId, @Valid @RequestBody QuotationRequest request) {
        return service.quote(new Actor(userId, email), rfqId, request);
    }

    @PostMapping("/{rfqId}/quotations/{quotationId}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Accept a quotation: raises the purchase order")
    public PoDetail accept(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                           @RequestHeader(value = "X-User-Email", required = false) String email,
                           @PathVariable Long rfqId, @PathVariable Long quotationId) {
        return service.accept(new Actor(userId, email), rfqId, quotationId);
    }

    @PostMapping("/{rfqId}/cancel")
    public RfqDetail cancel(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                            @RequestHeader(value = "X-User-Email", required = false) String email,
                            @PathVariable Long rfqId) {
        return service.cancel(new Actor(userId, email), rfqId);
    }
}
