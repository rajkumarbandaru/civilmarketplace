package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.config.ProcurementProperties;
import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.PurchaseOrderDtos.*;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.client.PaymentsClient;
import com.civileng.marketplace.procurement.repository.DispatchRepository;
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
    private final DispatchRepository dispatchRepo = mock(DispatchRepository.class);
    private final PriceListService priceLists = mock(PriceListService.class);
    private final PaymentsClient payments = mock(PaymentsClient.class);
    private final Notifier notifier = mock(Notifier.class);
    private final List<Dispatch> savedDispatches = new ArrayList<>();
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

        when(dispatchRepo.save(any())).thenAnswer(i -> {
            Dispatch d = i.getArgument(0);
            d.setId(ids.incrementAndGet());
            savedDispatches.add(d);
            return d;
        });
        when(dispatchRepo.findByPurchaseOrderIdOrderByIdAsc(anyLong())).thenAnswer(i -> List.copyOf(savedDispatches));
        when(dispatchRepo.findById(anyLong())).thenAnswer(i -> savedDispatches.stream()
                .filter(x -> x.getId().equals(i.getArgument(0, Long.class))).findFirst());
        when(priceLists.contractFor(anyLong(), anyLong())).thenReturn(Optional.empty());

        service = new PurchaseOrderService(orders, receipts, invoices, dispatchRepo, organizations, priceLists, memberships,
                props, payments, notifier, mock(Audit.class), Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));
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
        ReceiptRequest all = new ReceiptRequest(List.of(new ReceiptLineRequest(l, new BigDecimal("500"), BigDecimal.ZERO)), null, null);
        assertThatThrownBy(() -> service.receive(RAISER, po.id(), all)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.acknowledge(RAISER, po.id())).isInstanceOf(AccessDeniedException.class);
        service.acknowledge(SUPPLIER, po.id());
        assertThatThrownBy(() -> service.receive(SUPPLIER, po.id(), all)).isInstanceOf(AccessDeniedException.class);

        PoDetail partial = service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("320"), new BigDecimal("20"))), "first truck", null));
        assertThat(partial.status()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(partial.lines().get(0).acceptedQty()).isEqualByComparingTo("300");
        assertThat(partial.receipts()).singleElement().extracting(ReceiptView::number).asString().startsWith("GRN-");

        assertThatThrownBy(() -> service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("201"), BigDecimal.ZERO)), null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("would accept 501 of the 500");
        assertThatThrownBy(() -> service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("5"), new BigDecimal("6"))), null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        PoDetail full = service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("200"), BigDecimal.ZERO)), null, null));
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

        service.receive(RAISER, po.id(), new ReceiptRequest(List.of(new ReceiptLineRequest(l, new BigDecimal("500"), null)), null, null));
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
        service.receive(RAISER, po.id(), new ReceiptRequest(List.of(new ReceiptLineRequest(l, new BigDecimal("300"), null)), null, null));

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

    private PriceList contract(int termsDays, String creditLimit) {
        PriceList c = new PriceList();
        c.setId(55L);
        c.setSupplierOrgId(2L);
        c.setBuyerOrgId(1L);
        c.setStatus(PriceListStatus.ACTIVE);
        c.setPaymentTermsDays(termsDays);
        c.setCreditLimit(creditLimit == null ? null : new BigDecimal(creditLimit));
        return c;
    }

    @Test
    void underAContractTheOrderTakesItsTermsAndInvoicesFallDueAfterThem() {
        when(priceLists.contractFor(1L, 2L)).thenReturn(Optional.of(contract(30, null)));
        PoDetail po = raise("150.00");
        assertThat(po.paymentTermsDays()).isEqualTo(30);
        assertThat(po.contractId()).isEqualTo(55L);
        Long l = line(po);
        service.acknowledge(SUPPLIER, po.id());
        service.receive(RAISER, po.id(), new ReceiptRequest(List.of(new ReceiptLineRequest(l, new BigDecimal("500"), null)), null, null));
        Long inv = service.invoice(SUPPLIER, po.id(), new InvoiceRequest("INV-9", List.of(
                new InvoiceLineRequest(l, new BigDecimal("500"), new BigDecimal("150.00"), new BigDecimal("28"))))).invoices().get(0).id();
        PoDetail approved = service.decideInvoice(APPROVER, po.id(), inv, true, null);
        assertThat(approved.invoices().get(0).dueDate()).isEqualTo(java.time.LocalDate.parse("2026-10-24"));
    }

    @Test
    void anOrderPastTheContractsCreditLimitIsRefused() {
        when(priceLists.contractFor(1L, 2L)).thenReturn(Optional.of(contract(30, "150000")));
        when(priceLists.exposure(1L, 2L)).thenReturn(new BigDecimal("60000"));
        assertThatThrownBy(() -> raise("150.00")) // 96,000 + 60,000 open > 150,000
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("credit limit of 150000");
        when(priceLists.exposure(1L, 2L)).thenReturn(new BigDecimal("54000"));
        assertThat(raise("150.00").status()).isEqualTo(PurchaseOrderStatus.ISSUED); // exactly at the limit
    }

    @Test
    void aDispatchAboveFiftyThousandNeedsAValidEwayBillAndCannotOvershipTheOrder() {
        PoDetail po = raise("150.00");
        Long l = line(po);
        DispatchRequest bigNoBill = new DispatchRequest("ts09ab1234", null, null,
                List.of(new DispatchLineRequest(l, new BigDecimal("300"))));   // 45,000 + 28% = 57,600
        assertThatThrownBy(() -> service.dispatch(SUPPLIER, po.id(), bigNoBill)).isInstanceOf(IllegalStateException.class);
        service.acknowledge(SUPPLIER, po.id());
        assertThatThrownBy(() -> service.dispatch(RAISER, po.id(), bigNoBill)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.dispatch(SUPPLIER, po.id(), bigNoBill))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("need an e-way bill");
        assertThatThrownBy(() -> service.dispatch(SUPPLIER, po.id(), new DispatchRequest("TS09AB1234", null, "12345",
                List.of(new DispatchLineRequest(l, new BigDecimal("300"))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("12 digits");

        PoDetail sent = service.dispatch(SUPPLIER, po.id(), new DispatchRequest("ts09ab1234", "VRL", "331000123456",
                List.of(new DispatchLineRequest(l, new BigDecimal("300")))));
        DispatchView d = sent.dispatches().get(0);
        assertThat(d.vehicleNumber()).isEqualTo("TS09AB1234");
        assertThat(d.consignmentValue()).isEqualByComparingTo("57600.00");
        assertThat(sent.lines().get(0).dispatchedQty()).isEqualByComparingTo("300");
        // Small consignments move without one.
        service.dispatch(SUPPLIER, po.id(), new DispatchRequest("TS09AB1234", null, null,
                List.of(new DispatchLineRequest(l, new BigDecimal("100")))));
        assertThatThrownBy(() -> service.dispatch(SUPPLIER, po.id(), new DispatchRequest("TS09AB1234", null, "331000123457",
                List.of(new DispatchLineRequest(l, new BigDecimal("101"))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("send 501 of the 500");
    }

    @Test
    void aReceiptAgainstADispatchTakesNoMoreThanItCarriedAndOnlyOnce() {
        PoDetail po = raise("150.00");
        Long l = line(po);
        service.acknowledge(SUPPLIER, po.id());
        Long dispatchId = service.dispatch(SUPPLIER, po.id(), new DispatchRequest("TS09AB1234", null, "331000123456",
                List.of(new DispatchLineRequest(l, new BigDecimal("300"))))).dispatches().get(0).id();
        assertThatThrownBy(() -> service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("301"), null)), null, dispatchId)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("carried only 300");
        PoDetail got = service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("300"), new BigDecimal("20"))), null, dispatchId));
        assertThat(got.dispatches().get(0).receiptId()).isEqualTo(got.receipts().get(0).id());
        assertThat(got.receipts().get(0).dispatchId()).isEqualTo(dispatchId);
        assertThatThrownBy(() -> service.receive(RAISER, po.id(), new ReceiptRequest(List.of(
                new ReceiptLineRequest(l, new BigDecimal("1"), null)), null, dispatchId)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already been received");
        // The 20 rejected may be replaced: 300 + 200 + 20 dispatched in all.
        service.dispatch(SUPPLIER, po.id(), new DispatchRequest("TS09AB1234", null, "331000123457",
                List.of(new DispatchLineRequest(l, new BigDecimal("220")))));
    }

    @Test
    void anApprovedInvoiceIsPaidThroughPaymentServiceAndMarkedPaidOnceConfirmed() {
        PoDetail po = raise("150.00");
        Long l = line(po);
        service.acknowledge(SUPPLIER, po.id());
        service.receive(RAISER, po.id(), new ReceiptRequest(List.of(new ReceiptLineRequest(l, new BigDecimal("500"), null)), null, null));
        Long inv = service.invoice(SUPPLIER, po.id(), new InvoiceRequest("INV-5", List.of(
                new InvoiceLineRequest(l, new BigDecimal("500"), new BigDecimal("150.00"), new BigDecimal("28"))))).invoices().get(0).id();
        assertThatThrownBy(() -> service.pay(APPROVER, po.id(), inv)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved");
        service.decideInvoice(APPROVER, po.id(), inv, true, null);
        assertThatThrownBy(() -> service.pay(RAISER, po.id(), inv)).isInstanceOf(AccessDeniedException.class);

        when(payments.createOrder(any())).thenReturn(new PaymentsClient.PaymentOrder(900L, "PAY1", "order_X", "rzp_test_k",
                new BigDecimal("96000.00"), "PROCESSING", null));
        PaymentCheckout checkout = service.pay(APPROVER, po.id(), inv);
        assertThat(checkout.razorpayOrderId()).isEqualTo("order_X");
        assertThat(checkout.amount()).isEqualByComparingTo("96000.00");
        verify(payments).createOrder(argThat(r -> r.referenceType().equals("SUPPLIER_INVOICE") && r.referenceId().equals(inv)
                && r.amount().compareTo(new BigDecimal("96000.00")) == 0));

        service.markPaid(inv, 900L, "pay_ABC");
        service.markPaid(inv, 900L, "pay_ABC");   // Kafka redelivery changes nothing
        InvoiceView paid = service.get(APPROVER, po.id()).invoices().get(0);
        assertThat(paid.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paid.paymentReference()).isEqualTo("pay_ABC");
        verify(notifier, times(1)).toOrg(eq(2L), any(), any(), eq("PROCUREMENT_INVOICE_PAID"), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.pay(APPROVER, po.id(), inv)).hasMessageContaining("already been paid");
    }

    @Test
    void paymentProblemsAreExplainedNotLeakedAs500s() {
        PoDetail po = raise("150.00");
        Long l = line(po);
        service.acknowledge(SUPPLIER, po.id());
        service.receive(RAISER, po.id(), new ReceiptRequest(List.of(new ReceiptLineRequest(l, new BigDecimal("500"), null)), null, null));
        Long inv = service.invoice(SUPPLIER, po.id(), new InvoiceRequest("INV-6", List.of(
                new InvoiceLineRequest(l, new BigDecimal("500"), new BigDecimal("150.00"), new BigDecimal("28"))))).invoices().get(0).id();
        service.decideInvoice(APPROVER, po.id(), inv, true, null);
        feign.Request req = feign.Request.create(feign.Request.HttpMethod.POST, "/x", Map.of(), null, java.nio.charset.StandardCharsets.UTF_8, null);
        when(payments.createOrder(any())).thenThrow(new feign.FeignException.Conflict("no merchant", req, null, null));
        assertThatThrownBy(() -> service.pay(APPROVER, po.id(), inv)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set up");
        reset(payments);
        when(payments.createOrder(any())).thenThrow(new feign.FeignException.ServiceUnavailable("down", req, null, null));
        assertThatThrownBy(() -> service.pay(APPROVER, po.id(), inv)).isInstanceOf(PaymentUnavailableException.class);
    }

    @Test
    void theRightPeopleAreToldAtEachStep() {
        PoDetail po = raise("400.00");
        verify(notifier).toOrg(eq(1L), same(Notifier.APPROVERS), eq(10L), eq("PROCUREMENT_PO_APPROVAL_NEEDED"),
                any(), any(), eq("PURCHASE_ORDER"), eq(po.id()), eq("/procurement/orders/" + po.id()));
        verify(notifier, never()).toOrg(eq(2L), any(), any(), eq("PROCUREMENT_PO_ISSUED"), any(), any(), any(), any(), any());
        service.approve(APPROVER, po.id());
        verify(notifier).toOrg(eq(2L), any(), eq(11L), eq("PROCUREMENT_PO_ISSUED"), any(), any(), any(), any(), any());
        service.acknowledge(SUPPLIER, po.id());
        verify(notifier).toOrg(eq(1L), any(), eq(20L), eq("PROCUREMENT_PO_ACKNOWLEDGED"), any(), any(), any(), any(), any());
    }
}
