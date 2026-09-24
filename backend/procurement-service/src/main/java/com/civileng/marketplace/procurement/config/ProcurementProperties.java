package com.civileng.marketplace.procurement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Procurement rules, bound from {@code procurement.*}.
 *
 * @param approvalThreshold     a purchase order whose total exceeds this needs a second person's
 *                              approval, unless the buyer organization set its own threshold
 * @param priceTolerancePercent how far an invoiced unit price may differ from the order's before
 *                              the three-way match flags it
 */
@ConfigurationProperties(prefix = "procurement")
public record ProcurementProperties(BigDecimal approvalThreshold, BigDecimal priceTolerancePercent) {

    public ProcurementProperties {
        approvalThreshold = approvalThreshold == null ? new BigDecimal("100000") : approvalThreshold;
        priceTolerancePercent = priceTolerancePercent == null ? new BigDecimal("2") : priceTolerancePercent;
    }
}
