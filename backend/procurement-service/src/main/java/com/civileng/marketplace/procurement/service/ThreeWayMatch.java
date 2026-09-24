package com.civileng.marketplace.procurement.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Purchase order ↔ goods receipt ↔ invoice (architecture 07, Diagram 12). An invoice matches when,
 * line by line, it bills no more than was accepted on receipt and not already invoiced, at the
 * ordered unit price within the tolerance, at the ordered tax rate. Pure: the facts are gathered
 * by the caller.
 */
public final class ThreeWayMatch {

    private ThreeWayMatch() {
    }

    /** What the order and its receipts say about one line. */
    public record LineFacts(int lineNo, BigDecimal unitPrice, BigDecimal taxPercent,
                            BigDecimal acceptedQty, BigDecimal invoicedQty) { }

    public record Billed(Long poLineId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxPercent) { }

    /** Why the invoice does not match, one reason per problem; empty when it matches. */
    public static List<String> issues(Map<Long, LineFacts> order, List<Billed> invoice, BigDecimal tolerancePercent) {
        List<String> issues = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Billed b : invoice) {
            LineFacts line = order.get(b.poLineId());
            if (line == null) {
                issues.add("A line is not on this purchase order");
                continue;
            }
            String label = "Line " + line.lineNo();
            if (!seen.add(b.poLineId())) {
                issues.add(label + " is billed twice");
                continue;
            }
            BigDecimal open = line.acceptedQty().subtract(line.invoicedQty());
            if (b.quantity().compareTo(open) > 0) {
                issues.add("%s: bills %s but only %s received, accepted and not yet invoiced"
                        .formatted(label, Money.qty(b.quantity()), Money.qty(open.max(BigDecimal.ZERO))));
            }
            BigDecimal allowed = line.unitPrice().multiply(tolerancePercent).divide(Money.HUNDRED);
            if (b.unitPrice().subtract(line.unitPrice()).abs().compareTo(allowed) > 0) {
                issues.add("%s: unit price %s is more than %s%% from the ordered %s"
                        .formatted(label, b.unitPrice().toPlainString(), Money.qty(tolerancePercent),
                                line.unitPrice().toPlainString()));
            }
            if (b.taxPercent().compareTo(line.taxPercent()) != 0) {
                issues.add("%s: tax %s%% differs from the ordered %s%%"
                        .formatted(label, Money.qty(b.taxPercent()), Money.qty(line.taxPercent())));
            }
        }
        return issues;
    }
}
