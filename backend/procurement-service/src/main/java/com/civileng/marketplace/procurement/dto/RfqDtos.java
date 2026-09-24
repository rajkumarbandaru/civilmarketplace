package com.civileng.marketplace.procurement.dto;

import com.civileng.marketplace.procurement.model.QuotationStatus;
import com.civileng.marketplace.procurement.model.RfqStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class RfqDtos {

    private RfqDtos() {
    }

    public record RfqLineRequest(@NotBlank @Size(max = 300) String description,
                                 @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
                                 @NotBlank @Size(max = 20) String uom) { }

    public record CreateRfqRequest(@NotNull Long buyerOrgId,
                                   @NotBlank @Size(max = 200) String title,
                                   @Size(max = 300) String deliverySite,
                                   LocalDate neededBy,
                                   @Size(max = 120) String reference,
                                   @NotEmpty @Size(max = 100) List<@Valid RfqLineRequest> lines,
                                   @NotEmpty @Size(max = 50) Set<Long> supplierOrgIds) { }

    public record QuoteLineRequest(@NotNull Long rfqLineId,
                                   @NotNull @DecimalMin("0") BigDecimal unitPrice,
                                   @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taxPercent) { }

    public record QuotationRequest(@NotNull Long supplierOrgId,
                                   LocalDate validUntil,
                                   @Size(max = 1000) String notes,
                                   @NotEmpty List<@Valid QuoteLineRequest> lines) { }

    public record OrgRef(Long id, String name) { }

    public record RfqLineView(Long id, int lineNo, String description, BigDecimal quantity, String uom) { }

    public record QuoteLineView(Long rfqLineId, BigDecimal unitPrice, BigDecimal taxPercent, BigDecimal amount) { }

    public record QuotationView(Long id, OrgRef supplier, QuotationStatus status, LocalDate validUntil, String notes,
                                BigDecimal subtotal, BigDecimal taxTotal, BigDecimal total,
                                List<QuoteLineView> lines, LocalDateTime submittedAt) { }

    /** {@code roles}: how the caller relates to it — BUYER, SUPPLIER or both. */
    public record RfqSummary(Long id, String number, String title, OrgRef buyer, RfqStatus status,
                             LocalDate neededBy, int quotations, Set<String> roles, LocalDateTime createdAt) { }

    /**
     * The buyer sees every quotation; an invited supplier only its own. {@code purchaseOrderId}
     * once a quotation has been accepted.
     */
    public record RfqDetail(Long id, String number, String title, OrgRef buyer, RfqStatus status,
                            String deliverySite, LocalDate neededBy, String reference,
                            List<RfqLineView> lines, List<OrgRef> invitedSuppliers,
                            List<QuotationView> quotations, Set<String> roles, Long purchaseOrderId,
                            LocalDateTime createdAt) { }
}
