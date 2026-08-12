package com.civileng.marketplace.project.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** The slice of payment-service's EscrowHold the budget rollup needs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EscrowDto {

    private Long id;
    private Long projectId;
    private Long milestoneId;
    private String status;
    private BigDecimal amount;
    private BigDecimal netAmount;

    /** Money committed but not yet paid out — the payer cannot spend it elsewhere. */
    public boolean isHeld() {
        return "HELD".equals(status) || "DISPUTED".equals(status);
    }

    public boolean isReleased() {
        return "RELEASED".equals(status);
    }
}
