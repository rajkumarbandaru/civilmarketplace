package com.civileng.marketplace.payment.controller;

import com.civileng.marketplace.payment.model.Payment;
import com.civileng.marketplace.payment.service.PaymentService;
import com.civileng.marketplace.web.common.AccessDeniedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Payment orders on behalf of another service, for things that are not bookings. Internal-only
 * at the gateway: the calling service has already decided that this payer may pay this amount
 * for this reference (procurement-service: an approver of the buyer, an approved invoice), and
 * the payer's identity arrives in the signed context it forwarded.
 */
@RestController
@RequestMapping("/api/v1/payments/internal")
@RequiredArgsConstructor
@Tag(name = "Internal payments", description = "Service-to-service payment orders")
public class InternalPaymentController {

    private final PaymentService paymentService;

    public record OrderRequest(@NotBlank @Size(max = 30) String referenceType, @NotNull Long referenceId,
                               @NotNull @DecimalMin("1") BigDecimal amount, @Size(max = 500) String description) { }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create (or return the pending) payment order for a reference")
    public Payment createOrder(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                               @Valid @RequestBody OrderRequest request) {
        if (userId == null) {
            throw new AccessDeniedException("A payer is required");
        }
        return paymentService.createReferenceOrder(request.referenceType(), request.referenceId(), userId,
                request.amount(), request.description());
    }
}
