package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.config.ProcurementProperties;
import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.PurchaseOrderDtos.*;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.GoodsReceiptRepository;
import com.civileng.marketplace.procurement.repository.OrgMemberRepository;
import com.civileng.marketplace.procurement.repository.PurchaseOrderRepository;
import com.civileng.marketplace.procurement.repository.SupplierInvoiceRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.civileng.marketplace.procurement.service.Fixtures.member;
import static com.civileng.marketplace.procurement.service.Fixtures.org;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseOrderServiceTest {

    // Buyer org 1: user 10 raises (MEMBER), user 11 approves (APPROVER). Supplier org 2: user 20.
    private static final Actor RAISER = new Actor(10L, "u10@example.com");
    private static final Actor APPROVER = new Actor(11L, "u11@example.com");
    private static final Actor SUPPLIER = new Actor(20L, "u20@example.com");

    private final Organization buyer = org(1, "BuildCo", Capability.BUYER, Capability.CONTRACTOR);
    private final Organization supplier = org(2, "CementCo", Capability.SUPPLIER);

    private final PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
    private final GoodsReceiptRepository receipts = mock(GoodsReceiptRepository.class);
    private final SupplierInvoiceRepository invoices = mock(SupplierInvoiceRepository.class);
    private final OrganizationService organizations = mock(OrganizationService.class);
    private final OrgMemberRepository memberRepo = mock(OrgMemberRepository.class);
    private final ProcurementProperties props = new ProcurementProperties(new BigDecimal("100000"), new BigDecimal("2"));
    private PurchaseOrderService service;

    private final Map<Long, PurchaseOrder> savedOrders = new HashMap<>();
    private final List<GoodsReceipt> savedReceipts = new ArrayList<>();
    private final List<SupplierInvoice> savedInvoices = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        Map<Long, List<OrgMember>> byUser = Map.of(
                10L, List.of(member(1, 10, MemberRole.MEMBER)),
                11L, List.of(member(1, 11, MemberRole.APPROVER)),
                20L, List.of(member(2, 20, MemberRole.OWNER)));
        when(memberRepo.findByUserId(anyLong())).thenAnswer(i -> byUser.getOrDefault(i.getArgument(0, Long.class), List.of()));
        Memberships memberships = new Memberships(memberRepo);

        when(organizations.find(1L)).thenReturn(buyer);
        when(organizations.find(2L)).thenReturn(supplier);
        when(organizations.approvalThreshold(any())).thenAnswer(i -> {
            Organization o = i.getArgument(0);
            return o.getApprovalThreshold() != null ? o.getApprovalThreshold() : props.approvalThreshold();
        });
        when(organizations.names(any())).thenReturn(Map.of(1L, "BuildCo", 2L, "CementCo"));

        when(orders.save(any())).thenAnswer(i -> {
            PurchaseOrder po = i.getArgument(0);
            if (po.getId() == null) {
                po.setId(ids.incrementAndGet());
                po.getLines().forEach(l -> l.setId(ids.incrementAndGet()));
            }
            savedOrders.put(po.getId(), po);
            return po;
        });
        when(orders.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(savedOrders.get(i.getArgument(0, Long.class))));
        when(receipts.save(any())).thenAnswer(i -> {
            GoodsReceipt g = i.getArgument(0);
            g.setId(ids.incrementAndGet());
            savedReceipts.add(g);
            return g;
        });
        when(receipts.findByPurchaseOrderIdOrderByIdAsc(anyLong())).thenAnswer(i -> List.copyOf(savedReceipts));
        when(invoices.saveAndFlush(any())).thenAnswer(i -> {
            SupplierInvoice inv = i.getArgument(0);
            inv.setId(ids.incrementAndGet());
            savedInvoices.add(inv);
            return inv;
        });
        when(invoices.findByPurchaseOrderIdOrderByIdAsc(anyLong())).thenAnswer(i -> List.copyOf(savedInvoices));
        when(invoices.findById(anyLong())).thenAnswer(i -> savedInvoices.stream()
                .filter(x -> x.getId().equals(i.getArgument(0, Long.class))).findFirst());

        service = new PurchaseOrderService(orders, receipts, invoices, organizations, memberships, props,
                mock(Audit.class), Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));
    }

    /** 500 bags at the given price, 28% GST. */
    private PoDetail raise(String unitPrice) {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setBuyerOrgId(1L);
        RfqLine line = new RfqLine();
        line.setId(7L);
        line.setDescription("OPC 53 cement");
        line.setQuantity(new BigDecimal("500"));
        line.setUom("bag");
        rfq.addLine(line);
        Quotation q = new Quotation();
        q.setId(3L);
        q.setSupplierOrgId(2L);
        QuotationLine ql = new QuotationLine();
        ql.setRfqLineId(7L);
        ql.setUnitPrice(new BigDecimal(unitPrice));
        ql.setTaxPercent(new BigDecimal("28"));
        q.addLine(ql);
        return service.raise(RAISER, rfq, q);
    }

    private Long line(PoDetail po) {
        return po.lines().get(0).id();
    }

    @Test
    void anOrderAtOrBelowTheThresholdIsIssuedStraightAway() {
        PoDetail po = raise("150.00"); // 75,000 + 28% = 96,000
        assertThat(po.total()).isEqualByComparingTo("96000.00");
        assertThat(po.status()).isEqualTo(PurchaseOrderStatus.ISSUED);
        assertThat(po.number()).startsWith("PO-");
    }

    @Test
    void anOrderAboveTheThresholdWaitsForASecondPerson() {
        PoDetail po = raise("400.00"); // 256,000
        assertThat(po.status()).isEqualTo(PurchaseOrderStatus.PENDING_APPROVAL);
        assertThat(po.canApprove()).isFalse();

        assertThatThrownBy(() -> service.approve(RAISER, po.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.approve(SUPPLIER, po.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.acknowledge(SUPPLIER, po.id())).isInstanceOf(IllegalStateException.class);

        assertThat(service.get(APPROVER, po.id()).canApprove()).isTrue();
        PoDetail approved = service.approve(APPROVER, po.id());
        assertThat(approved.status()).isEqualTo(PurchaseOrderStatus.ISSUED);
        assertThat(approved.approvedAt()).isNotNull();
    }

    @Test
    void theOrganizationsOwnThresholdWinsOverTheDefault() {
        buyer.setApprovalThreshold(new BigDecimal("50000"));
        assertThat(raise("150.00").status()).isEqualTo(PurchaseOrderStatus.PENDING_APPROVAL);
    }

    @Test
    void theRaiserCannotApproveEvenAsAnApprover() {
        PoDetail po = raise("400.00");
        savedOrders.get(po.id()).setCreatedBy(11L);
        assertThatThrownBy(() -> service.approve(APPROVER, po.id()))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("raised");
    }

    @Test
    void receiptsFollowAcknowledgementAndNeverAcceptMoreThanOrdered() {
        PoDetail po = raise("150.00");
        Long l = line(po);
        ReceiptRequest all = new ReceiptRequest(List.of(new ReceiptLineRequest(l, new BigDecimal("500"), BigDecimal.ZERO)), null);
        assertThatThrownBy(() -> service.receive(RAISER, po.id(), all)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.acknowledge(RAISER, po.id())).isInstanceOf(AccessDeniedException.class);
        service.acknowledge(SUPPLIER, po.id());
        assertThatThrownBy(() -> service.receive(SUPPLIER, po.id(), all)).isInstanceOf(AccessDeniedException.class);

        PoDetail partial = service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("320"), new BigDecimal("20"))), "first truck"));
        assertThat(partial.status()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(partial.lines().get(0).acceptedQty()).isEqualByComparingTo("300");
        assertThat(partial.receipts()).singleElement().extracting(ReceiptView::number).asString().startsWith("GRN-");

        assertThatThrownBy(() -> service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("201"), BigDecimal.ZERO)), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("would accept 501 of the 500");
        assertThatThrownBy(() -> service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("5"), new BigDecimal("6"))), null)))
                .isInstanceOf(IllegalArgumentException.class);

        PoDetail full = service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("200"), BigDecimal.ZERO)), null));
        assertThat(full.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
    }

    @Test
    void aMatchedInvoiceIsApprovedAndClosesAFullyReceivedOrder() {
        PoDetail po = raise("150.00");
        Long l = line(po);
        service.acknowledge(SUPPLIER, po.id());
        InvoiceRequest bill = new InvoiceRequest("INV-1", List.of(
                new InvoiceLineRequest(l, new BigDecimal("500"), new BigDecimal("150.00"), new BigDecimal("28"))));
        assertThatThrownBy(() -> service.invoice(SUPPLIER, po.id(), bill)).isInstanceOf(IllegalStateException.class);

        service.receive(RAISER, po.id(), new ReceiptRequest(List.of(new ReceiptLineRequest(l, new BigDecimal("500"), null)), null));
        assertThatThrownBy(() -> service.invoice(RAISER, po.id(), bill)).isInstanceOf(AccessDeniedException.class);
        PoDetail invoiced = service.invoice(SUPPLIER, po.id(), bill);
        InvoiceView inv = invoiced.invoices().get(0);
        assertThat(inv.status()).isEqualTo(InvoiceStatus.MATCHED);
        assertThat(inv.total()).isEqualByComparingTo("96000.00");

        assertThatThrownBy(() -> service.decideInvoice(RAISER, po.id(), inv.id(), true, null))
                .isInstanceOf(AccessDeniedException.class);
        PoDetail closed = service.decideInvoice(APPROVER, po.id(), inv.id(), true, null);
        assertThat(closed.invoices().get(0).status()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(closed.status()).isEqualTo(PurchaseOrderStatus.CLOSED);
    }

    @Test
    void anInvoiceOutsideTheMatchIsAnExceptionThatCanOnlyBeRejected() {
        PoDetail po = raise("150.00");
        Long l = line(po);
        service.acknowledge(SUPPLIER, po.id());
        service.receive(RAISER, po.id(), new ReceiptRequest(List.of(new ReceiptLineRequest(l, new BigDecimal("300"), null)), null));

        PoDetail d = service.invoice(SUPPLIER, po.id(), new InvoiceRequest("INV-2", List.of(
                new InvoiceLineRequest(l, new BigDecimal("400"), new BigDecimal("160.00"), new BigDecimal("28")))));
        InvoiceView inv = d.invoices().get(0);
        assertThat(inv.status()).isEqualTo(InvoiceStatus.EXCEPTION);
        assertThat(inv.matchIssues()).hasSize(2)
                .anyMatch(s -> s.contains("bills 400 but only 300"))
                .anyMatch(s -> s.contains("unit price 160.00"));
        assertThatThrownBy(() -> service.decideInvoice(APPROVER, po.id(), inv.id(), true, null))
                .isInstanceOf(IllegalStateException.class);

        service.decideInvoice(APPROVER, po.id(), inv.id(), false, "Bill only what arrived");
        // A rejected invoice frees its quantity: a corrected one for the 300 received matches.
        PoDetail corrected = service.invoice(SUPPLIER, po.id(), new InvoiceRequest("INV-3", List.of(
                new InvoiceLineRequest(l, new BigDecimal("300"), new BigDecimal("150.00"), new BigDecimal("28")))));
        assertThat(corrected.invoices()).extracting(InvoiceView::status)
                .containsExactly(InvoiceStatus.REJECTED, InvoiceStatus.MATCHED);
        assertThat(corrected.status()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    }

    @Test
    void outsidersSeeNothing() {
        PoDetail po = raise("150.00");
        assertThatThrownBy(() -> service.get(new Actor(99L, "x@example.com"), po.id()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
