package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.service.ThreeWayMatch.Billed;
import com.civileng.marketplace.procurement.service.ThreeWayMatch.LineFacts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ThreeWayMatchTest {

    private static final BigDecimal TWO = new BigDecimal("2");

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    /** 500 bags of cement at 400 + 28%, 450 accepted so far, 100 of them already invoiced. */
    private final Map<Long, LineFacts> order = Map.of(
            1L, new LineFacts(1, d("400.00"), d("28"), d("450"), d("100")),
            2L, new LineFacts(2, d("65000.00"), d("18"), d("2"), d("0")));

    @Test
    void anInvoiceWithinWhatWasAcceptedAtTheOrderedPriceMatches() {
        assertThat(ThreeWayMatch.issues(order, List.of(
                new Billed(1L, d("350"), d("400.00"), d("28")),
                new Billed(2L, d("2"), d("65000.00"), d("18"))), TWO)).isEmpty();
    }

    @Test
    void billingMoreThanAcceptedAndNotYetInvoicedIsFlagged() {
        assertThat(ThreeWayMatch.issues(order, List.of(new Billed(1L, d("351"), d("400.00"), d("28"))), TWO))
                .containsExactly("Line 1: bills 351 but only 350 received, accepted and not yet invoiced");
    }

    @Test
    void priceWithinTolerancePassesAndBeyondItFails() {
        // 2% of 400 is 8: 408 is inside, 408.01 outside, either direction.
        assertThat(ThreeWayMatch.issues(order, List.of(new Billed(1L, d("10"), d("408.00"), d("28"))), TWO)).isEmpty();
        assertThat(ThreeWayMatch.issues(order, List.of(new Billed(1L, d("10"), d("392.00"), d("28"))), TWO)).isEmpty();
        assertThat(ThreeWayMatch.issues(order, List.of(new Billed(1L, d("10"), d("408.01"), d("28"))), TWO))
                .singleElement().asString().contains("more than 2% from the ordered 400.00");
    }

    @Test
    void aDifferentTaxRateIsFlagged() {
        assertThat(ThreeWayMatch.issues(order, List.of(new Billed(2L, d("1"), d("65000.00"), d("28"))), TWO))
                .containsExactly("Line 2: tax 28% differs from the ordered 18%");
    }

    @Test
    void unknownAndDuplicateLinesAreFlagged() {
        assertThat(ThreeWayMatch.issues(order, List.of(
                new Billed(9L, d("1"), d("1"), d("0")),
                new Billed(2L, d("1"), d("65000.00"), d("18")),
                new Billed(2L, d("1"), d("65000.00"), d("18"))), TWO))
                .containsExactly("A line is not on this purchase order", "Line 2 is billed twice");
    }

    @Test
    void totalsRoundPerLineToPaise() {
        Money.Totals t = Money.Totals.ZERO.plus(d("3"), d("33.33"), d("18")).plus(d("0.5"), d("0.99"), d("5"));
        assertThat(t.subtotal()).isEqualByComparingTo("100.49");   // 99.99 + 0.50 (0.495 → 0.50)
        assertThat(t.tax()).isEqualByComparingTo("18.03");         // 18.00 + 0.03 (0.025 → 0.03)
        assertThat(t.total()).isEqualByComparingTo("118.52");
    }
}
