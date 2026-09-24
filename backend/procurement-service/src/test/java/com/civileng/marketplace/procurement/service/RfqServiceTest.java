package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.RfqDtos.*;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.OrgMemberRepository;
import com.civileng.marketplace.procurement.repository.PurchaseOrderRepository;
import com.civileng.marketplace.procurement.repository.QuotationRepository;
import com.civileng.marketplace.procurement.repository.RfqRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.civileng.marketplace.procurement.service.Fixtures.member;
import static com.civileng.marketplace.procurement.service.Fixtures.org;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RfqServiceTest {

    private static final Actor BUYER = new Actor(10L, "u10@example.com");
    private static final Actor CEMENT = new Actor(20L, "u20@example.com");
    private static final Actor STEEL = new Actor(30L, "u30@example.com");

    private final Map<Long, Organization> orgs = Map.of(
            1L, org(1, "BuildCo", Capability.BUYER, Capability.CONTRACTOR),
            2L, org(2, "CementCo", Capability.SUPPLIER),
            3L, org(3, "SteelCo", Capability.SUPPLIER),
            4L, org(4, "DiggerCo", Capability.EQUIPMENT_PROVIDER),
            5L, org(5, "HomeOnly", Capability.CONTRACTOR));

    private final RfqRepository rfqs = mock(RfqRepository.class);
    private final QuotationRepository quotations = mock(QuotationRepository.class);
    private final OrganizationService organizations = mock(OrganizationService.class);
    private final PurchaseOrderService purchaseOrders = mock(PurchaseOrderService.class);
    private final OrgMemberRepository memberRepo = mock(OrgMemberRepository.class);
    private RfqService service;

    private final Map<Long, Rfq> savedRfqs = new HashMap<>();
    private final List<Quotation> savedQuotes = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        Map<Long, List<OrgMember>> byUser = Map.of(
                10L, List.of(member(1, 10, MemberRole.OWNER), member(5, 10, MemberRole.OWNER)),
                20L, List.of(member(2, 20, MemberRole.OWNER)),
                30L, List.of(member(3, 30, MemberRole.OWNER)));
        when(memberRepo.findByUserId(anyLong())).thenAnswer(i -> byUser.getOrDefault(i.getArgument(0, Long.class), List.of()));

        when(organizations.find(anyLong())).thenAnswer(i -> {
            Organization o = orgs.get(i.getArgument(0, Long.class));
            if (o == null) throw new NoSuchElementException("No such organization");
            return o;
        });
        when(organizations.names(any())).thenAnswer(i -> {
            Map<Long, String> m = new HashMap<>();
            orgs.forEach((k, v) -> m.put(k, v.getName()));
            return m;
        });
        when(rfqs.save(any())).thenAnswer(i -> {
            Rfq r = i.getArgument(0);
            r.setId(ids.incrementAndGet());
            r.getLines().forEach(l -> l.setId(ids.incrementAndGet()));
            savedRfqs.put(r.getId(), r);
            return r;
        });
        when(rfqs.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(savedRfqs.get(i.getArgument(0, Long.class))));
        when(quotations.save(any())).thenAnswer(i -> {
            Quotation q = i.getArgument(0);
            if (q.getId() == null) {
                q.setId(ids.incrementAndGet());
                savedQuotes.add(q);
            }
            return q;
        });
        when(quotations.findByRfqIdOrderByTotalAsc(anyLong())).thenAnswer(i -> savedQuotes.stream()
                .filter(q -> q.getRfqId().equals(i.getArgument(0, Long.class)))
                .sorted(Comparator.comparing(Quotation::getTotal)).toList());
        when(quotations.findByRfqIdAndSupplierOrgId(anyLong(), anyLong())).thenAnswer(i -> savedQuotes.stream()
                .filter(q -> q.getRfqId().equals(i.getArgument(0, Long.class)) && q.getSupplierOrgId().equals(i.getArgument(1, Long.class)))
                .findFirst());

        service = new RfqService(rfqs, quotations, mock(PurchaseOrderRepository.class), organizations,
                new Memberships(memberRepo), purchaseOrders, mock(Audit.class),
                Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));
    }

    private CreateRfqRequest request(Long buyerOrg, Long... suppliers) {
        return new CreateRfqRequest(buyerOrg, "Cement and steel for the Sharma extension", "Plot 12, Hyderabad",
                LocalDate.parse("2026-10-10"), "Booking #42",
                List.of(new RfqLineRequest("OPC 53 cement", new BigDecimal("500"), "bag"),
                        new RfqLineRequest("TMT bar 12mm", new BigDecimal("2"), "tonne")),
                new LinkedHashSet<>(List.of(suppliers)));
    }

    private QuotationRequest quote(Long supplierOrg, RfqDetail rfq, String cement, String steel) {
        return new QuotationRequest(supplierOrg, LocalDate.parse("2026-10-01"), null, List.of(
                new QuoteLineRequest(rfq.lines().get(0).id(), new BigDecimal(cement), new BigDecimal("28")),
                new QuoteLineRequest(rfq.lines().get(1).id(), new BigDecimal(steel), new BigDecimal("18"))));
    }

    @Test
    void onlyABuyerMemberRaisesAnRfqAndOnlyToSuppliersItMayTradeWith() {
        assertThatThrownBy(() -> service.create(CEMENT, request(1L, 2L))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(BUYER, request(5L, 2L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not set up as a buyer");
        assertThatThrownBy(() -> service.create(BUYER, request(1L, 4L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a supplier");
        assertThatThrownBy(() -> service.create(BUYER, request(1L, 1L))).isInstanceOf(IllegalArgumentException.class);
        when(organizations.blocked(1L, 3L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(BUYER, request(1L, 2L, 3L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SteelCo cannot be invited");

        RfqDetail rfq = service.create(BUYER, request(1L, 2L));
        assertThat(rfq.status()).isEqualTo(RfqStatus.OPEN);
        assertThat(rfq.number()).startsWith("RFQ-");
        assertThat(rfq.lines()).extracting(RfqLineView::lineNo).containsExactly(1, 2);
        assertThat(rfq.roles()).containsExactly("BUYER");
    }

    @Test
    void suppliersQuoteEveryLineAndSeeOnlyTheirOwnQuotation() {
        RfqDetail rfq = service.create(BUYER, request(1L, 2L, 3L));
        assertThatThrownBy(() -> service.quote(CEMENT, rfq.id(), new QuotationRequest(2L, null, null, List.of(
                new QuoteLineRequest(rfq.lines().get(0).id(), BigDecimal.ONE, BigDecimal.ZERO)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly once");
        assertThatThrownBy(() -> service.quote(CEMENT, rfq.id(), quote(3L, rfq, "1", "1")))
                .isInstanceOf(AccessDeniedException.class);

        RfqDetail mine = service.quote(CEMENT, rfq.id(), quote(2L, rfq, "400.00", "65000.00"));
        // 500 × 400 = 200,000 + 28% ; 2 × 65,000 = 130,000 + 18%
        assertThat(mine.quotations()).singleElement().satisfies(q -> {
            assertThat(q.subtotal()).isEqualByComparingTo("330000.00");
            assertThat(q.total()).isEqualByComparingTo("409400.00");
        });
        RfqDetail theirs = service.quote(STEEL, rfq.id(), quote(3L, rfq, "395.00", "64000.00"));
        assertThat(theirs.quotations()).singleElement().extracting(q -> q.supplier().name()).isEqualTo("SteelCo");
        assertThat(service.get(BUYER, rfq.id()).quotations()).extracting(q -> q.supplier().name())
                .containsExactly("SteelCo", "CementCo");

        // A revision replaces, never duplicates.
        service.quote(CEMENT, rfq.id(), quote(2L, rfq, "390.00", "64000.00"));
        assertThat(savedQuotes).hasSize(2);
        assertThatThrownBy(() -> service.get(new Actor(99L, "x@example.com"), rfq.id()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void acceptingAQuotationAwardsTheRfqDeclinesTheRestAndRaisesTheOrder() {
        RfqDetail rfq = service.create(BUYER, request(1L, 2L, 3L));
        service.quote(CEMENT, rfq.id(), quote(2L, rfq, "400.00", "65000.00"));
        service.quote(STEEL, rfq.id(), quote(3L, rfq, "395.00", "64000.00"));
        Long cheapest = service.get(BUYER, rfq.id()).quotations().get(0).id();

        assertThatThrownBy(() -> service.accept(CEMENT, rfq.id(), cheapest)).isInstanceOf(AccessDeniedException.class);
        service.accept(BUYER, rfq.id(), cheapest);

        verify(purchaseOrders).raise(eq(BUYER), same(savedRfqs.get(rfq.id())),
                argThat(q -> q.getId().equals(cheapest)));
        assertThat(savedRfqs.get(rfq.id()).getStatus()).isEqualTo(RfqStatus.AWARDED);
        assertThat(savedQuotes).extracting(Quotation::getStatus)
                .containsExactlyInAnyOrder(QuotationStatus.ACCEPTED, QuotationStatus.REJECTED);
        assertThatThrownBy(() -> service.quote(CEMENT, rfq.id(), quote(2L, rfq, "1", "1")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.accept(BUYER, rfq.id(), cheapest)).isInstanceOf(IllegalStateException.class);
    }
}
