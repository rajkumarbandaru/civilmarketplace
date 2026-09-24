package com.civileng.marketplace.procurement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Line and document arithmetic, rounded once per line to paise, the way a tax invoice is. */
public final class Money {

    private Money() {
    }

    public static final BigDecimal HUNDRED = new BigDecimal("100");

    public static BigDecimal amount(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal tax(BigDecimal amount, BigDecimal taxPercent) {
        return amount.multiply(taxPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Subtotal, tax and total of lines given as (quantity, unit price, tax %). */
    public record Totals(BigDecimal subtotal, BigDecimal tax, BigDecimal total) {

        public static final Totals ZERO = new Totals(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2));

        public Totals plus(BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxPercent) {
            BigDecimal a = amount(quantity, unitPrice);
            BigDecimal t = Money.tax(a, taxPercent);
            return new Totals(subtotal.add(a), tax.add(t), total.add(a).add(t));
        }
    }

    public static String qty(BigDecimal q) {
        return q.stripTrailingZeros().toPlainString();
    }
}
