package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.procurement.client.PaymentsClient;
import com.civileng.marketplace.procurement.config.ProcurementProperties;
import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.PurchaseOrderDtos.*;
import com.civileng.marketplace.procurement.dto.RfqDtos.OrgRef;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.DispatchRepository;
import com.civileng.marketplace.procurement.repository.GoodsReceiptRepository;
import com.civileng.marketplace.procurement.repository.PurchaseOrderRepository;
import com.civileng.marketplace.procurement.repository.SupplierInvoiceRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A purchase order's life: approval above the buyer's threshold (by someone other than who raised
 * it), the supplier's acknowledgement, dispatches under e-way bills, goods receipts, invoices
 * checked by the three-way match, and payment of approved invoices through payment-service.
 * Orders raised under a contract carry its payment terms and respect its credit limit.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final String ENTITY = "PURCHASE_ORDER";
    /** GST: goods above this value need an e-way bill to move. */
    static final BigDecimal EWAY_BILL_THRESHOLD = new BigDecimal("50000");
    private static final Pattern EWAY_BILL = Pattern.compile("\\d{12}");
    private static final Pattern VEHICLE = Pattern.compile("[A-Z0-9 -]{4,20}");
    public static final String PAYMENT_REFERENCE_TYPE = "SUPPLIER_INVOICE";
    private static final Set<PurchaseOrderStatus> DELIVERABLE =
            EnumSet.of(PurchaseOrderStatus.ACKNOWLEDGED, PurchaseOrderStatus.PARTIALLY_RECEIVED);
    private static final Set<PurchaseOrderStatus> INVOICEABLE =
            EnumSet.of(PurchaseOrderStatus.PARTIALLY_RECEIVED, PurchaseOrderStatus.RECEIVED);

    private final PurchaseOrderRepository orders;
    private final GoodsReceiptRepository receipts;
    private final SupplierInvoiceRepository invoices;
    private final DispatchRepository dispatches;
    private final OrganizationService organizations;
    private final PriceListService priceLists;
    private final Memberships memberships;
    private final ProcurementProperties props;
    private final PaymentsClient payments;
    private final Notifier notifier;
    private final Audit audit;
    private final Clock clock;

    /**
     * From an accepted quotation. Issued straight away unless its total is above the buyer's
     * threshold. Under a contract it takes the contract's payment terms, and is refused if it
     * would take the buyer past the contract's credit limit.
     */
    @Transactional
    public PoDetail raise(Actor actor, Rfq rfq, Quotation quotation) {
        Organization buyer = organizations.find(rfq.getBuyerOrgId());
        Map<Long, QuotationLine> prices = quotation.getLines().stream()
                .collect(Collectors.toMap(QuotationLine::getRfqLineId, l -> l));
        PurchaseOrder po = new PurchaseOrder();
        po.setRfqId(rfq.getId());
        po.setQuotationId(quotation.getId());
        po.setBuyerOrgId(rfq.getBuyerOrgId());
        po.setSupplierOrgId(quotation.getSupplierOrgId());
        po.setDeliverySite(rfq.getDeliverySite());
        po.setReference(rfq.getReference());
        po.setCreatedBy(actor.userId());
        Money.Totals totals = Money.Totals.ZERO;
        for (RfqLine l : rfq.getLines()) {
            QuotationLine price = prices.get(l.getId());
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setDescription(l.getDescription());
            line.setQuantity(l.getQuantity());
            line.setUom(l.getUom());
            line.setUnitPrice(price.getUnitPrice());
            line.setTaxPercent(price.getTaxPercent());
            po.addLine(line);
            totals = totals.plus(l.getQuantity(), price.getUnitPrice(), price.getTaxPercent());
        }
        po.setSubtotal(totals.subtotal());
        po.setTaxTotal(totals.tax());
        po.setTotal(totals.total());

        Optional<PriceList> contract = priceLists.contractFor(po.getBuyerOrgId(), po.getSupplierOrgId());
        if (contract.isPresent()) {
            PriceList c = contract.get();
            po.setContractId(c.getId());
            po.setPaymentTermsDays(c.getPaymentTermsDays());
            if (c.getCreditLimit() != null) {
                BigDecimal open = priceLists.exposure(po.getBuyerOrgId(), po.getSupplierOrgId());
                if (open.add(po.getTotal()).compareTo(c.getCreditLimit()) > 0) {
                    throw new IllegalStateException("This order would take %s past its credit limit of %s with this supplier (%s already open)"
                            .formatted(buyer.getName(), c.getCreditLimit().toPlainString(), open.toPlainString()));
                }
            }
        }

        po.setStatus(needsApproval(po, buyer) ? PurchaseOrderStatus.PENDING_APPROVAL : PurchaseOrderStatus.ISSUED);
        orders.save(po);
        po.setNumber("PO-%05d".formatted(po.getId()));
        audit.record(actor.userId(), AuditAction.CREATE, ENTITY, po.getId(), po.getNumber() + " " + po.getStatus()
                + " total " + po.getTotal());
        if (po.getStatus() == PurchaseOrderStatus.PENDING_APPROVAL) {
            tellBuyer(po, Notifier.APPROVERS, actor, "PROCUREMENT_PO_APPROVAL_NEEDED", po.getNumber() + " needs your approval",
                    "Total %s is above %s's approval limit.".formatted(po.getTotal().toPlainString(), buyer.getName()));
        } else {
            tellSupplierIssued(po, actor);
        }
        return detail(po, actor);
    }

    @Transactional
    public List<PoSummary> list(Actor actor) {
        Set<Long> mine = memberships.byOrganization(actor).keySet();
        if (mine.isEmpty()) {
            return List.of();
        }
        List<PurchaseOrder> visible = orders.visibleTo(mine);
        Set<Long> ids = new HashSet<>();
        visible.forEach(p -> { ids.add(p.getBuyerOrgId()); ids.add(p.getSupplierOrgId()); });
        Map<Long, String> names = organizations.names(ids);
        return visible.stream().map(p -> new PoSummary(p.getId(), p.getNumber(),
                new OrgRef(p.getBuyerOrgId(), names.get(p.getBuyerOrgId())),
                new OrgRef(p.getSupplierOrgId(), names.get(p.getSupplierOrgId())),
                p.getStatus(), p.getTotal(), roles(p, mine), p.getCreatedAt())).toList();
    }

    @Transactional
    public PoDetail get(Actor actor, Long id) {
        return detail(visible(actor, id), actor);
    }

    /** Maker-checker: an approver of the buyer, never the person who raised it. */
    @Transactional
    public PoDetail approve(Actor actor, Long id) {
        PurchaseOrder po = find(id);
        requireChecker(actor, po);
        requireStatus(po, PurchaseOrderStatus.PENDING_APPROVAL, "approved");
        po.setStatus(PurchaseOrderStatus.ISSUED);
        po.setApprovedBy(actor.userId());
        po.setApprovedAt(LocalDateTime.now(clock));
        audit.record(actor.userId(), AuditAction.APPROVE, ENTITY, id, po.getNumber());
        tellSupplierIssued(po, actor);
        return detail(po, actor);
    }

    @Transactional
    public PoDetail reject(Actor actor, Long id, String reason) {
        PurchaseOrder po = find(id);
        requireChecker(actor, po);
        requireStatus(po, PurchaseOrderStatus.PENDING_APPROVAL, "rejected");
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po.setCancelReason(reason == null || reason.isBlank() ? "Rejected at approval" : reason.trim());
        audit.record(actor.userId(), AuditAction.REJECT, ENTITY, id, po.getNumber() + ": " + po.getCancelReason());
        tellBuyer(po, Notifier.EVERYONE, actor, "PROCUREMENT_PO_REJECTED", po.getNumber() + " was not approved", po.getCancelReason());
        return detail(po, actor);
    }

    @Transactional
    public PoDetail acknowledge(Actor actor, Long id) {
        PurchaseOrder po = find(id);
        memberships.require(actor, po.getSupplierOrgId());
        requireStatus(po, PurchaseOrderStatus.ISSUED, "acknowledged");
        po.setStatus(PurchaseOrderStatus.ACKNOWLEDGED);
        po.setAcknowledgedBy(actor.userId());
        po.setAcknowledgedAt(LocalDateTime.now(clock));
        audit.record(actor.userId(), AuditAction.UPDATE, ENTITY, id, po.getNumber() + " acknowledged");
        tellBuyer(po, Notifier.EVERYONE, actor, "PROCUREMENT_PO_ACKNOWLEDGED", supplierName(po) + " accepted " + po.getNumber(),
                "The supplier confirmed it will supply the order.");
        return detail(po, actor);
    }

    /**
     * The supplier sends goods. What is in transit or delivered never exceeds what was ordered
     * plus what was rejected (a rejected quantity may be replaced). Above ₹50,000 of goods a
     * valid 12-digit e-way bill is required.
     */
    @Transactional
    public PoDetail dispatch(Actor actor, Long id, DispatchRequest request) {
        PurchaseOrder po = find(id);
        memberships.require(actor, po.getSupplierOrgId());
        if (!DELIVERABLE.contains(po.getStatus())) {
            throw new IllegalStateException("Goods can be dispatched once the order is acknowledged and until it is received");
        }
        String vehicle = request.vehicleNumber().trim().toUpperCase();
        if (!VEHICLE.matcher(vehicle).matches()) {
            throw new IllegalArgumentException("Enter the vehicle registration, e.g. TS09AB1234");
        }
        Map<Long, PurchaseOrderLine> lines = lines(po);
        Map<Long, BigDecimal> dispatched = dispatched(po);
        Map<Long, BigDecimal> rejected = new HashMap<>();
        receipts.findByPurchaseOrderIdOrderByIdAsc(id).forEach(g -> g.getLines()
                .forEach(l -> rejected.merge(l.getPoLineId(), l.getRejectedQty(), BigDecimal::add)));

        Dispatch d = new Dispatch();
        d.setPurchaseOrderId(id);
        d.setVehicleNumber(vehicle);
        d.setTransporter(request.transporter() == null || request.transporter().isBlank() ? null : request.transporter().trim());
        d.setDispatchedBy(actor.userId());
        BigDecimal value = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (DispatchLineRequest l : request.lines()) {
            PurchaseOrderLine line = lines.get(l.poLineId());
            if (line == null || !seen.add(l.poLineId())) {
                throw new IllegalArgumentException("Each line must be on this order, once");
            }
            BigDecimal allowed = line.getQuantity().add(rejected.getOrDefault(line.getId(), BigDecimal.ZERO));
            BigDecimal after = dispatched.getOrDefault(line.getId(), BigDecimal.ZERO).add(l.quantity());
            if (after.compareTo(allowed) > 0) {
                throw new IllegalArgumentException("Line %d: that would send %s of the %s %s ordered".formatted(
                        line.getLineNo(), Money.qty(after), Money.qty(line.getQuantity()), line.getUom()));
            }
            DispatchLine dl = new DispatchLine();
            dl.setPoLineId(line.getId());
            dl.setQuantity(l.quantity());
            d.addLine(dl);
            BigDecimal amount = Money.amount(l.quantity(), line.getUnitPrice());
            value = value.add(amount).add(Money.tax(amount, line.getTaxPercent()));
        }
        d.setConsignmentValue(value);
        String eway = request.ewayBillNumber() == null ? "" : request.ewayBillNumber().trim();
        if (!eway.isEmpty() && !EWAY_BILL.matcher(eway).matches()) {
            throw new IllegalArgumentException("An e-way bill number is 12 digits");
        }
        if (eway.isEmpty() && value.compareTo(EWAY_BILL_THRESHOLD) > 0) {
            throw new IllegalArgumentException("Goods worth %s need an e-way bill (required above %s)"
                    .formatted(value.toPlainString(), EWAY_BILL_THRESHOLD.toPlainString()));
        }
        d.setEwayBillNumber(eway.isEmpty() ? null : eway);
        dispatches.save(d);
        d.setNumber("DSP-%05d".formatted(d.getId()));
        audit.record(actor.userId(), AuditAction.CREATE, "DISPATCH", d.getId(), po.getNumber() + " " + d.getNumber()
                + " vehicle " + vehicle + (d.getEwayBillNumber() == null ? "" : " EWB " + d.getEwayBillNumber()));
        tellBuyer(po, Notifier.EVERYONE, actor, "PROCUREMENT_DISPATCHED", "Goods on the way: " + po.getNumber(),
                "%s dispatched %s on vehicle %s%s.".formatted(supplierName(po), d.getNumber(), vehicle,
                        d.getEwayBillNumber() == null ? "" : " (e-way bill " + d.getEwayBillNumber() + ")"));
        return detail(po, actor);
    }

    /**
     * A delivery, whole or partial. What is accepted never exceeds what was ordered. A receipt
     * against a dispatch receives no more than that dispatch carried, and each dispatch once.
     */
    @Transactional
    public PoDetail receive(Actor actor, Long id, ReceiptRequest request) {
        PurchaseOrder po = find(id);
        memberships.require(actor, po.getBuyerOrgId());
        if (!DELIVERABLE.contains(po.getStatus())) {
            throw new IllegalStateException(po.getStatus() == PurchaseOrderStatus.RECEIVED
                    ? "Everything on this order has been received"
                    : "Goods can be received once the supplier has acknowledged the order");
        }
        Map<Long, BigDecimal> carried = null;
        if (request.dispatchId() != null) {
            Dispatch d = dispatches.findById(request.dispatchId()).filter(x -> x.getPurchaseOrderId().equals(id))
                    .orElseThrow(() -> new NoSuchElementException("No such dispatch on this order"));
            boolean received = receipts.findByPurchaseOrderIdOrderByIdAsc(id).stream()
                    .anyMatch(g -> d.getId().equals(g.getDispatchId()));
            if (received) {
                throw new IllegalStateException(d.getNumber() + " has already been received");
            }
            carried = new HashMap<>();
            for (DispatchLine l : d.getLines()) {
                carried.merge(l.getPoLineId(), l.getQuantity(), BigDecimal::add);
            }
        }
        Map<Long, PurchaseOrderLine> lines = lines(po);
        Map<Long, BigDecimal> accepted = accepted(po);
        GoodsReceipt grn = new GoodsReceipt();
        grn.setPurchaseOrderId(id);
        grn.setDispatchId(request.dispatchId());
        grn.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        grn.setReceivedBy(actor.userId());
        Set<Long> seen = new HashSet<>();
        boolean anything = false;
        for (ReceiptLineRequest l : request.lines()) {
            PurchaseOrderLine line = lines.get(l.poLineId());
            if (line == null || !seen.add(l.poLineId())) {
                throw new IllegalArgumentException("Each line must be on this order, once");
            }
            BigDecimal rejected = l.rejectedQty() == null ? BigDecimal.ZERO : l.rejectedQty();
            if (rejected.compareTo(l.receivedQty()) > 0) {
                throw new IllegalArgumentException("Line " + line.getLineNo() + ": more rejected than received");
            }
            if (carried != null && l.receivedQty().compareTo(carried.getOrDefault(line.getId(), BigDecimal.ZERO)) > 0) {
                throw new IllegalArgumentException("Line %d: the dispatch carried only %s".formatted(
                        line.getLineNo(), Money.qty(carried.getOrDefault(line.getId(), BigDecimal.ZERO))));
            }
            BigDecimal after = accepted.getOrDefault(line.getId(), BigDecimal.ZERO).add(l.receivedQty().subtract(rejected));
            if (after.compareTo(line.getQuantity()) > 0) {
                throw new IllegalArgumentException("Line %d: that would accept %s of the %s %s ordered".formatted(
                        line.getLineNo(), Money.qty(after), Money.qty(line.getQuantity()), line.getUom()));
            }
            if (l.receivedQty().signum() == 0) {
                continue;
            }
            anything = true;
            accepted.put(line.getId(), after);
            GoodsReceiptLine g = new GoodsReceiptLine();
            g.setPoLineId(line.getId());
            g.setReceivedQty(l.receivedQty());
            g.setRejectedQty(rejected);
            grn.addLine(g);
        }
        if (!anything) {
            throw new IllegalArgumentException("Record at least one quantity received");
        }
        receipts.save(grn);
        grn.setNumber("GRN-%05d".formatted(grn.getId()));
        boolean complete = lines.values().stream()
                .allMatch(l -> accepted.getOrDefault(l.getId(), BigDecimal.ZERO).compareTo(l.getQuantity()) >= 0);
        po.setStatus(complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        audit.record(actor.userId(), AuditAction.CREATE, "GOODS_RECEIPT", grn.getId(), po.getNumber() + " " + grn.getNumber());
        BigDecimal rejectedNow = grn.getLines().stream().map(GoodsReceiptLine::getRejectedQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        tellSupplier(po, actor, "PROCUREMENT_GOODS_RECEIVED", "Goods received: " + po.getNumber(),
                "%s recorded %s%s. You can invoice what was accepted.".formatted(buyerName(po), grn.getNumber(),
                        rejectedNow.signum() > 0 ? " with " + Money.qty(rejectedNow) + " rejected" : ""));
        return detail(po, actor);
    }

    /** The supplier bills against the order; the three-way match decides MATCHED or EXCEPTION. */
    @Transactional
    public PoDetail invoice(Actor actor, Long id, InvoiceRequest request) {
        PurchaseOrder po = find(id);
        memberships.require(actor, po.getSupplierOrgId());
        if (!INVOICEABLE.contains(po.getStatus())) {
            throw new IllegalStateException("An invoice can be raised once goods have been received");
        }
        Map<Long, ThreeWayMatch.LineFacts> facts = facts(po);
        List<ThreeWayMatch.Billed> billed = request.lines().stream()
                .map(l -> new ThreeWayMatch.Billed(l.poLineId(), l.quantity(), l.unitPrice(), l.taxPercent())).toList();
        List<String> issues = ThreeWayMatch.issues(facts, billed, props.priceTolerancePercent());

        SupplierInvoice inv = new SupplierInvoice();
        inv.setPurchaseOrderId(id);
        inv.setSupplierOrgId(po.getSupplierOrgId());
        inv.setInvoiceNumber(request.invoiceNumber().trim());
        inv.setSubmittedBy(actor.userId());
        Money.Totals totals = Money.Totals.ZERO;
        for (InvoiceLineRequest l : request.lines()) {
            SupplierInvoiceLine line = new SupplierInvoiceLine();
            line.setPoLineId(l.poLineId());
            line.setQuantity(l.quantity());
            line.setUnitPrice(l.unitPrice());
            line.setTaxPercent(l.taxPercent());
            inv.addLine(line);
            totals = totals.plus(l.quantity(), l.unitPrice(), l.taxPercent());
        }
        inv.setSubtotal(totals.subtotal());
        inv.setTaxTotal(totals.tax());
        inv.setTotal(totals.total());
        inv.setStatus(issues.isEmpty() ? InvoiceStatus.MATCHED : InvoiceStatus.EXCEPTION);
        inv.setMatchIssues(issues.isEmpty() ? null : String.join("\n", issues));
        invoices.saveAndFlush(inv);
        audit.record(actor.userId(), AuditAction.CREATE, "SUPPLIER_INVOICE", inv.getId(),
                po.getNumber() + " " + inv.getInvoiceNumber() + " " + inv.getStatus());
        tellBuyer(po, Notifier.APPROVERS, actor, "PROCUREMENT_INVOICE_SUBMITTED",
                "Invoice " + inv.getInvoiceNumber() + " for " + po.getNumber(),
                "%s billed %s. Three-way match: %s.".formatted(supplierName(po), inv.getTotal().toPlainString(),
                        issues.isEmpty() ? "passed" : "failed (" + issues.size() + " issue" + (issues.size() == 1 ? "" : "s") + ")"));
        return detail(po, actor);
    }

    /**
     * The buyer approves a matched invoice for payment, or rejects one (a rejected invoice frees
     * its quantities to be billed again). An exception cannot be approved: the supplier issues a
     * corrected invoice. An approved invoice is due after the order's payment terms. The order
     * closes once everything received has been billed and approved.
     */
    @Transactional
    public PoDetail decideInvoice(Actor actor, Long id, Long invoiceId, boolean approve, String note) {
        PurchaseOrder po = find(id);
        requireApprover(actor, po, "Only an owner or approver can decide on invoices");
        SupplierInvoice inv = invoiceOf(po, invoiceId);
        if (inv.getStatus() != InvoiceStatus.MATCHED && inv.getStatus() != InvoiceStatus.EXCEPTION) {
            throw new IllegalStateException("This invoice has already been " + inv.getStatus().name().toLowerCase());
        }
        if (approve && inv.getStatus() == InvoiceStatus.EXCEPTION) {
            throw new IllegalStateException("An invoice that fails the three-way match cannot be approved; reject it");
        }
        inv.setStatus(approve ? InvoiceStatus.APPROVED : InvoiceStatus.REJECTED);
        inv.setDecidedBy(actor.userId());
        inv.setDecidedAt(LocalDateTime.now(clock));
        inv.setDecisionNote(note == null || note.isBlank() ? null : note.trim());
        if (approve) {
            inv.setDueDate(LocalDate.now(clock).plusDays(po.getPaymentTermsDays()));
        }
        invoices.flush();
        if (approve && po.getStatus() == PurchaseOrderStatus.RECEIVED && fullyBilled(po)) {
            po.setStatus(PurchaseOrderStatus.CLOSED);
        }
        audit.record(actor.userId(), approve ? AuditAction.APPROVE : AuditAction.REJECT, "SUPPLIER_INVOICE", invoiceId,
                po.getNumber() + " " + inv.getInvoiceNumber());
        tellSupplier(po, actor, approve ? "PROCUREMENT_INVOICE_APPROVED" : "PROCUREMENT_INVOICE_REJECTED",
                "Invoice " + inv.getInvoiceNumber() + (approve ? " approved" : " rejected"),
                approve ? "Approved for payment, due " + inv.getDueDate() + "."
                        : "Rejected" + (inv.getDecisionNote() == null ? "." : ": " + inv.getDecisionNote()));
        return detail(po, actor);
    }

    /**
     * Starts paying an approved invoice: payment-service creates the order the buyer completes in
     * Razorpay Checkout. Asking again while one is pending returns the same payment.
     */
    @Transactional
    public PaymentCheckout pay(Actor actor, Long id, Long invoiceId) {
        PurchaseOrder po = find(id);
        requireApprover(actor, po, "Only an owner or approver can pay invoices");
        SupplierInvoice inv = invoiceOf(po, invoiceId);
        if (inv.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("This invoice has already been paid");
        }
        if (inv.getStatus() != InvoiceStatus.APPROVED) {
            throw new IllegalStateException("Only an approved invoice can be paid");
        }
        String description = "Invoice %s for %s (%s)".formatted(inv.getInvoiceNumber(), po.getNumber(), supplierName(po));
        PaymentsClient.PaymentOrder order;
        try {
            order = payments.createOrder(new PaymentsClient.OrderRequest(PAYMENT_REFERENCE_TYPE, inv.getId(), inv.getTotal(),
                    description));
        } catch (FeignException.Conflict e) {
            throw new IllegalStateException("Online payments are not set up for this workspace yet");
        } catch (FeignException e) {
            log.error("payment-service refused to create an order for invoice {}: {}", invoiceId, e.getMessage());
            throw new PaymentUnavailableException();
        }
        if ("FAILED".equals(order.paymentStatus())) {
            throw new IllegalStateException("The payment provider refused the order: " + order.failureReason());
        }
        inv.setPaymentId(order.id());
        audit.record(actor.userId(), AuditAction.CREATE, "SUPPLIER_INVOICE", invoiceId, "payment started " + order.paymentCode());
        return new PaymentCheckout(inv.getId(), order.id(), order.razorpayOrderId(), order.razorpayKeyId(),
                order.totalAmount(), description);
    }

    /** payment-service confirmed the money moved. Idempotent: Kafka redelivers. */
    @Transactional
    public void markPaid(Long invoiceId, Long paymentId, String reference) {
        SupplierInvoice inv = invoices.findById(invoiceId).orElse(null);
        if (inv == null) {
            log.warn("Payment {} completed for unknown invoice {}", paymentId, invoiceId);
            return;
        }
        if (inv.getStatus() == InvoiceStatus.PAID) {
            return;
        }
        if (inv.getStatus() != InvoiceStatus.APPROVED) {
            log.error("Payment {} completed for invoice {} in status {}; recorded, needs review", paymentId, invoiceId, inv.getStatus());
        }
        inv.setStatus(InvoiceStatus.PAID);
        inv.setPaymentId(paymentId);
        inv.setPaymentReference(reference);
        inv.setPaidAt(LocalDateTime.now(clock));
        PurchaseOrder po = find(inv.getPurchaseOrderId());
        audit.record(null, AuditAction.UPDATE, "SUPPLIER_INVOICE", invoiceId, po.getNumber() + " paid " + reference);
        tellSupplier(po, null, "PROCUREMENT_INVOICE_PAID", "Payment received: " + inv.getInvoiceNumber(),
                "%s paid %s against %s (ref %s).".formatted(buyerName(po), inv.getTotal().toPlainString(), po.getNumber(), reference));
    }

    boolean needsApproval(PurchaseOrder po, Organization buyer) {
        return po.getTotal().compareTo(organizations.approvalThreshold(buyer)) > 0;
    }

    private boolean fullyBilled(PurchaseOrder po) {
        Map<Long, BigDecimal> approved = new HashMap<>();
        for (SupplierInvoice i : invoices.findByPurchaseOrderIdOrderByIdAsc(po.getId())) {
            if (i.isAccepted()) {
                i.getLines().forEach(l -> approved.merge(l.getPoLineId(), l.getQuantity(), BigDecimal::add));
            }
        }
        return po.getLines().stream()
                .allMatch(l -> approved.getOrDefault(l.getId(), BigDecimal.ZERO).compareTo(l.getQuantity()) >= 0);
    }

    private SupplierInvoice invoiceOf(PurchaseOrder po, Long invoiceId) {
        return invoices.findById(invoiceId).filter(i -> i.getPurchaseOrderId().equals(po.getId()))
                .orElseThrow(() -> new NoSuchElementException("No such invoice on this order"));
    }

    private void requireApprover(Actor actor, PurchaseOrder po, String message) {
        if (!memberships.require(actor, po.getBuyerOrgId()).canApprove()) {
            throw new AccessDeniedException(message);
        }
    }

    private void requireChecker(Actor actor, PurchaseOrder po) {
        requireApprover(actor, po, "Only an owner or approver can approve orders");
        if (Objects.equals(actor.userId(), po.getCreatedBy())) {
            throw new AccessDeniedException("The person who raised an order cannot approve it");
        }
    }

    private static void requireStatus(PurchaseOrder po, PurchaseOrderStatus expected, String verb) {
        if (po.getStatus() != expected) {
            throw new IllegalStateException("This order is " + po.getStatus().name().toLowerCase().replace('_', ' ')
                    + " and cannot be " + verb);
        }
    }

    private PurchaseOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new NoSuchElementException("No such purchase order"));
    }

    private PurchaseOrder visible(Actor actor, Long id) {
        PurchaseOrder po = find(id);
        if (roles(po, memberships.byOrganization(actor).keySet()).isEmpty()) {
            throw new NoSuchElementException("No such purchase order");
        }
        return po;
    }

    private String supplierName(PurchaseOrder po) {
        return organizations.find(po.getSupplierOrgId()).getName();
    }

    private String buyerName(PurchaseOrder po) {
        return organizations.find(po.getBuyerOrgId()).getName();
    }

    private void tellBuyer(PurchaseOrder po, java.util.function.Predicate<OrgMember> who, Actor actor, String type,
                           String title, String message) {
        notifier.toOrg(po.getBuyerOrgId(), who, actor == null ? null : actor.userId(), type, title, message,
                ENTITY, po.getId(), "/procurement/orders/" + po.getId());
    }

    private void tellSupplier(PurchaseOrder po, Actor actor, String type, String title, String message) {
        notifier.toOrg(po.getSupplierOrgId(), Notifier.EVERYONE, actor == null ? null : actor.userId(), type, title, message,
                ENTITY, po.getId(), "/procurement/orders/" + po.getId());
    }

    private void tellSupplierIssued(PurchaseOrder po, Actor actor) {
        tellSupplier(po, actor, "PROCUREMENT_PO_ISSUED", "New order " + po.getNumber() + " from " + buyerName(po),
                "Total %s%s. Acknowledge it to confirm you will supply.".formatted(po.getTotal().toPlainString(),
                        po.getPaymentTermsDays() > 0 ? ", Net " + po.getPaymentTermsDays() : ""));
    }

    private static Set<String> roles(PurchaseOrder po, Set<Long> mine) {
        Set<String> roles = new TreeSet<>();
        if (mine.contains(po.getBuyerOrgId())) {
            roles.add(RfqService.BUYER);
        }
        if (mine.contains(po.getSupplierOrgId())) {
            roles.add(RfqService.SUPPLIER);
        }
        return roles;
    }

    private static Map<Long, PurchaseOrderLine> lines(PurchaseOrder po) {
        Map<Long, PurchaseOrderLine> m = new LinkedHashMap<>();
        po.getLines().forEach(l -> m.put(l.getId(), l));
        return m;
    }

    private Map<Long, BigDecimal> accepted(PurchaseOrder po) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (GoodsReceipt g : receipts.findByPurchaseOrderIdOrderByIdAsc(po.getId())) {
            g.getLines().forEach(l -> m.merge(l.getPoLineId(), l.acceptedQty(), BigDecimal::add));
        }
        return m;
    }

    private Map<Long, BigDecimal> dispatched(PurchaseOrder po) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Dispatch d : dispatches.findByPurchaseOrderIdOrderByIdAsc(po.getId())) {
            d.getLines().forEach(l -> m.merge(l.getPoLineId(), l.getQuantity(), BigDecimal::add));
        }
        return m;
    }

    private Map<Long, ThreeWayMatch.LineFacts> facts(PurchaseOrder po) {
        Map<Long, BigDecimal> accepted = accepted(po);
        Map<Long, BigDecimal> invoiced = new HashMap<>();
        for (SupplierInvoice i : invoices.findByPurchaseOrderIdOrderByIdAsc(po.getId())) {
            if (i.isLive()) {
                i.getLines().forEach(l -> invoiced.merge(l.getPoLineId(), l.getQuantity(), BigDecimal::add));
            }
        }
        Map<Long, ThreeWayMatch.LineFacts> facts = new HashMap<>();
        for (PurchaseOrderLine l : po.getLines()) {
            facts.put(l.getId(), new ThreeWayMatch.LineFacts(l.getLineNo(), l.getUnitPrice(), l.getTaxPercent(),
                    accepted.getOrDefault(l.getId(), BigDecimal.ZERO), invoiced.getOrDefault(l.getId(), BigDecimal.ZERO)));
        }
        return facts;
    }

    private PoDetail detail(PurchaseOrder po, Actor actor) {
        Map<Long, OrgMember> mine = memberships.byOrganization(actor);
        Map<Long, String> names = organizations.names(List.of(po.getBuyerOrgId(), po.getSupplierOrgId()));
        List<GoodsReceipt> grns = receipts.findByPurchaseOrderIdOrderByIdAsc(po.getId());
        List<SupplierInvoice> invs = invoices.findByPurchaseOrderIdOrderByIdAsc(po.getId());
        List<Dispatch> sent = dispatches.findByPurchaseOrderIdOrderByIdAsc(po.getId());
        Map<Long, BigDecimal> dispatchedQty = dispatched(po);
        Map<Long, BigDecimal> received = new HashMap<>();
        Map<Long, BigDecimal> accepted = new HashMap<>();
        Map<Long, BigDecimal> invoiced = new HashMap<>();
        Map<Long, Long> receiptOfDispatch = new HashMap<>();
        grns.forEach(g -> {
            if (g.getDispatchId() != null) {
                receiptOfDispatch.put(g.getDispatchId(), g.getId());
            }
            g.getLines().forEach(l -> {
                received.merge(l.getPoLineId(), l.getReceivedQty(), BigDecimal::add);
                accepted.merge(l.getPoLineId(), l.acceptedQty(), BigDecimal::add);
            });
        });
        invs.stream().filter(SupplierInvoice::isLive)
                .forEach(i -> i.getLines().forEach(l -> invoiced.merge(l.getPoLineId(), l.getQuantity(), BigDecimal::add)));

        OrgMember buyerMember = mine.get(po.getBuyerOrgId());
        boolean canApprove = po.getStatus() == PurchaseOrderStatus.PENDING_APPROVAL && buyerMember != null
                && buyerMember.canApprove() && !Objects.equals(actor.userId(), po.getCreatedBy());
        BigDecimal zero = BigDecimal.ZERO;
        return new PoDetail(po.getId(), po.getNumber(), po.getRfqId(),
                new OrgRef(po.getBuyerOrgId(), names.get(po.getBuyerOrgId())),
                new OrgRef(po.getSupplierOrgId(), names.get(po.getSupplierOrgId())),
                po.getStatus(), po.getSubtotal(), po.getTaxTotal(), po.getTotal(),
                organizations.approvalThreshold(organizations.find(po.getBuyerOrgId())),
                po.getPaymentTermsDays(), po.getContractId(),
                po.getDeliverySite(), po.getReference(),
                po.getLines().stream().map(l -> new PoLineView(l.getId(), l.getLineNo(), l.getDescription(), l.getQuantity(),
                        l.getUom(), l.getUnitPrice(), l.getTaxPercent(), Money.amount(l.getQuantity(), l.getUnitPrice()),
                        dispatchedQty.getOrDefault(l.getId(), zero),
                        received.getOrDefault(l.getId(), zero), accepted.getOrDefault(l.getId(), zero),
                        invoiced.getOrDefault(l.getId(), zero))).toList(),
                sent.stream().map(d -> new DispatchView(d.getId(), d.getNumber(), d.getVehicleNumber(), d.getTransporter(),
                        d.getEwayBillNumber(), d.getConsignmentValue(),
                        d.getLines().stream().map(l -> new DispatchLineView(l.getPoLineId(), l.getQuantity())).toList(),
                        receiptOfDispatch.get(d.getId()), d.getCreatedAt())).toList(),
                grns.stream().map(g -> new ReceiptView(g.getId(), g.getNumber(), g.getDispatchId(), g.getNotes(),
                        g.getLines().stream().map(l -> new ReceiptLineView(l.getPoLineId(), l.getReceivedQty(), l.getRejectedQty())).toList(),
                        g.getCreatedAt())).toList(),
                invs.stream().map(i -> new InvoiceView(i.getId(), i.getInvoiceNumber(), i.getStatus(), i.getSubtotal(),
                        i.getTaxTotal(), i.getTotal(),
                        i.getMatchIssues() == null ? List.of() : List.of(i.getMatchIssues().split("\n")),
                        i.getLines().stream().map(l -> new InvoiceLineView(l.getPoLineId(), l.getQuantity(), l.getUnitPrice(), l.getTaxPercent())).toList(),
                        i.getDecisionNote(), i.getDueDate(), i.getPaidAt(), i.getPaymentReference(), i.getCreatedAt())).toList(),
                roles(po, mine.keySet()), canApprove, po.getCancelReason(), po.getApprovedAt(), po.getAcknowledgedAt(),
                po.getCreatedAt());
    }
}
