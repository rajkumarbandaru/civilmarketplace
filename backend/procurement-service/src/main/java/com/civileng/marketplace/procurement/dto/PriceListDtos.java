package com.civileng.marketplace.procurement.dto;

import com.civileng.marketplace.procurement.dto.RfqDtos.OrgRef;
import com.civileng.marketplace.procurement.model.PriceListStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PriceListDtos {

    private PriceListDtos() {
    }

    public record ItemRequest(@NotBlank @Size(max = 300) String description,
                              @NotBlank @Size(max = 20) String uom,
                              @NotNull @DecimalMin("0") BigDecimal unitPrice,
                              @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taxPercent) { }

    /** The supplier's standard catalogue: replaces the items wholesale. */
    public record CatalogueRequest(@NotNull Long supplierOrgId,
                                   @Size(max = 120) String name,
                                   @NotNull @Size(max = 500) List<@Valid ItemRequest> items) { }

    /** A contract the supplier offers one buyer. */
    public record ContractRequest(@NotNull Long supplierOrgId,
                                  @NotNull Long buyerOrgId,
                                  @NotBlank @Size(max = 120) String name,
                                  @Min(0) @Max(180) int paymentTermsDays,
                                  @DecimalMin("0") BigDecimal creditLimit,
                                  LocalDate validFrom,
                                  LocalDate validUntil,
                                  @NotEmpty @Size(max = 500) List<@Valid ItemRequest> items) { }

    public record ItemView(Long id, String description, String uom, BigDecimal unitPrice, BigDecimal taxPercent) { }

    /**
     * {@code roles}: SUPPLIER for the organization that set the prices, BUYER for the one a
     * contract is with. {@code exposure}: what the buyer has open with the supplier under it.
     */
    public record PriceListView(Long id, OrgRef supplier, OrgRef buyer, String name, PriceListStatus status,
                                boolean contract, int paymentTermsDays, BigDecimal creditLimit, BigDecimal exposure,
                                LocalDate validFrom, LocalDate validUntil, List<ItemView> items,
                                List<String> roles, LocalDateTime createdAt, LocalDateTime decidedAt) { }
}
