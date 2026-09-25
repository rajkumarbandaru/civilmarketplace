package com.civileng.marketplace.procurement.dto;

import com.civileng.marketplace.procurement.dto.RfqDtos.OrgRef;
import com.civileng.marketplace.procurement.model.InvoiceStatus;
import com.civileng.marketplace.procurement.model.PurchaseOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class PurchaseOrderDtos {

    private PurchaseOrderDtos() {
    }

    public record ReceiptLineRequest(@NotNull Long poLineId,
                                     @NotNull @DecimalMin("0") BigDecimal receivedQty,
                                     @DecimalMin("0") BigDecimal rejectedQty) { }

    /** {@code dispatchId}: the delivery this receipt is for, if the supplier recorded one. */
    public record ReceiptRequest(@NotEmpty List<@Valid ReceiptLineRequest> lines, @Size(max = 1000) String notes,
                                 Long dispatchId) { }

    public record DispatchLineRequest(@NotNull Long poLineId,
                                      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity) { }

    /** A consignment: e-way bill required above ₹50,000 of goods (12 digits). */
    public record DispatchRequest(@NotBlank @Size(max = 20) String vehicleNumber,
                                  @Size(max = 120) String transporter,
                                  @Size(max = 12) String ewayBillNumber,
                                  @NotEmpty List<@Valid DispatchLineRequest> lines) { }

    public record InvoiceLineRequest(@NotNull Long poLineId,
                                     @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
                                     @NotNull @DecimalMin("0") BigDecimal unitPrice,
                                     @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taxPercent) { }

    public record InvoiceRequest(@NotBlank @Size(max = 40) String invoiceNumber,
                                 @NotEmpty List<@Valid InvoiceLineRequest> lines) { }

    public record DecisionRequest(@Size(max = 500) String note) { }

    /** Ordered, and so far: dispatched, received, accepted (received minus rejected) and invoiced. */
    public record PoLineView(Long id, int lineNo, String description, BigDecimal quantity, String uom,
                             BigDecimal unitPrice, BigDecimal taxPercent, BigDecimal amount, BigDecimal dispatchedQty,
                             BigDecimal receivedQty, BigDecimal acceptedQty, BigDecimal invoicedQty) { }

    public record ReceiptLineView(Long poLineId, BigDecimal receivedQty, BigDecimal rejectedQty) { }

    public record ReceiptView(Long id, String number, Long dispatchId, String notes, List<ReceiptLineView> lines,
                              LocalDateTime receivedAt) { }

    public record DispatchLineView(Long poLineId, BigDecimal quantity) { }

    /** {@code receiptId}: the goods receipt that recorded its arrival, once there is one. */
    public record DispatchView(Long id, String number, String vehicleNumber, String transporter, String ewayBillNumber,
                               BigDecimal consignmentValue, List<DispatchLineView> lines, Long receiptId,
                               LocalDateTime dispatchedAt) { }

    /** What the browser needs to open Razorpay Checkout for an approved invoice. */
    public record PaymentCheckout(Long invoiceId, Long paymentId, String razorpayOrderId, String razorpayKeyId,
                                  BigDecimal amount, String description) { }

    public record InvoiceLineView(Long poLineId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxPercent) { }

    public record InvoiceView(Long id, String invoiceNumber, InvoiceStatus status, BigDecimal subtotal,
                              BigDecimal taxTotal, BigDecimal total, List<String> matchIssues,
                              List<InvoiceLineView> lines, String decisionNote, LocalDate dueDate,
                              LocalDateTime paidAt, String paymentReference, LocalDateTime submittedAt) { }

    public record PoSummary(Long id, String number, OrgRef buyer, OrgRef supplier, PurchaseOrderStatus status,
                            BigDecimal total, Set<String> roles, LocalDateTime createdAt) { }

    /**
     * {@code canApprove}: the caller may approve it now — an approver of the buyer who did not
     * raise it.
     */
    public record PoDetail(Long id, String number, Long rfqId, OrgRef buyer, OrgRef supplier,
                           PurchaseOrderStatus status, BigDecimal subtotal, BigDecimal taxTotal, BigDecimal total,
                           BigDecimal approvalThreshold, int paymentTermsDays, Long contractId,
                           String deliverySite, String reference,
                           List<PoLineView> lines, List<DispatchView> dispatches, List<ReceiptView> receipts,
                           List<InvoiceView> invoices,
                           Set<String> roles, boolean canApprove, String cancelReason,
                           LocalDateTime approvedAt, LocalDateTime acknowledgedAt, LocalDateTime createdAt) { }
}
