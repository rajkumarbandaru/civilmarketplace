package com.civileng.marketplace.procurement.dto;

import com.civileng.marketplace.procurement.dto.RfqDtos.OrgRef;
import com.civileng.marketplace.procurement.model.InvoiceStatus;
import com.civileng.marketplace.procurement.model.PurchaseOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class PurchaseOrderDtos {

    private PurchaseOrderDtos() {
    }

    public record ReceiptLineRequest(@NotNull Long poLineId,
                                     @NotNull @DecimalMin("0") BigDecimal receivedQty,
                                     @DecimalMin("0") BigDecimal rejectedQty) { }

    public record ReceiptRequest(@NotEmpty List<@Valid ReceiptLineRequest> lines, @Size(max = 1000) String notes) { }

    public record InvoiceLineRequest(@NotNull Long poLineId,
                                     @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
                                     @NotNull @DecimalMin("0") BigDecimal unitPrice,
                                     @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taxPercent) { }

    public record InvoiceRequest(@NotBlank @Size(max = 40) String invoiceNumber,
                                 @NotEmpty List<@Valid InvoiceLineRequest> lines) { }

    public record DecisionRequest(@Size(max = 500) String note) { }

    /** Ordered, and from receipts and invoices so far: accepted (received minus rejected) and invoiced. */
    public record PoLineView(Long id, int lineNo, String description, BigDecimal quantity, String uom,
                             BigDecimal unitPrice, BigDecimal taxPercent, BigDecimal amount,
                             BigDecimal receivedQty, BigDecimal acceptedQty, BigDecimal invoicedQty) { }

    public record ReceiptLineView(Long poLineId, BigDecimal receivedQty, BigDecimal rejectedQty) { }

    public record ReceiptView(Long id, String number, String notes, List<ReceiptLineView> lines, LocalDateTime receivedAt) { }

    public record InvoiceLineView(Long poLineId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxPercent) { }

    public record InvoiceView(Long id, String invoiceNumber, InvoiceStatus status, BigDecimal subtotal,
                              BigDecimal taxTotal, BigDecimal total, List<String> matchIssues,
                              List<InvoiceLineView> lines, String decisionNote, LocalDateTime submittedAt) { }

    public record PoSummary(Long id, String number, OrgRef buyer, OrgRef supplier, PurchaseOrderStatus status,
                            BigDecimal total, Set<String> roles, LocalDateTime createdAt) { }

    /**
     * {@code canApprove}: the caller may approve it now — an approver of the buyer who did not
     * raise it.
     */
    public record PoDetail(Long id, String number, Long rfqId, OrgRef buyer, OrgRef supplier,
                           PurchaseOrderStatus status, BigDecimal subtotal, BigDecimal taxTotal, BigDecimal total,
                           BigDecimal approvalThreshold, String deliverySite, String reference,
                           List<PoLineView> lines, List<ReceiptView> receipts, List<InvoiceView> invoices,
                           Set<String> roles, boolean canApprove, String cancelReason,
                           LocalDateTime approvedAt, LocalDateTime acknowledgedAt, LocalDateTime createdAt) { }
}
