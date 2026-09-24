package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.procurement.config.ProcurementProperties;
import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.PurchaseOrderDtos.*;
import com.civileng.marketplace.procurement.dto.RfqDtos.OrgRef;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.GoodsReceiptRepository;
import com.civileng.marketplace.procurement.repository.PurchaseOrderRepository;
import com.civileng.marketplace.procurement.repository.SupplierInvoiceRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A purchase order's life: approval above the buyer's threshold (by someone other than who raised
 * it), the supplier's acknowledgement, goods receipts, and invoices checked by the three-way match.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final String ENTITY = "PURCHASE_ORDER";
    private static final Set<PurchaseOrderStatus> RECEIVABLE =
            EnumSet.of(PurchaseOrderStatus.ACKNOWLEDGED, PurchaseOrderStatus.PARTIALLY_RECEIVED);
    private static final Set<PurchaseOrderStatus> INVOICEABLE =
            EnumSet.of(PurchaseOrderStatus.PARTIALLY_RECEIVED, PurchaseOrderStatus.RECEIVED);

    private final PurchaseOrderRepository orders;
    private final GoodsReceiptRepository receipts;
    private final SupplierInvoiceRepository invoices;
    private final OrganizationService organizations;
    private final Memberships memberships;
    private final ProcurementProperties props;
    private final Audit audit;
    private final Clock clock;

    /** From an accepted quotation. Issued straight away unless its total is above the buyer's threshold. */
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
        po.setStatus(needsApproval(po, buyer) ? PurchaseOrderStatus.PENDING_APPROVAL : PurchaseOrderStatus.ISSUED);
        orders.save(po);
        po.setNumber("PO-%05d".formatted(po.getId()));
        audit.record(actor.userId(), AuditAction.CREATE, ENTITY, po.getId(), po.getNumber() + " " + po.getStatus()
                + " total " + po.getTotal());
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
        return detail(po, actor);
    }

    /** A delivery, whole or partial. What is accepted never exceeds what was ordered. */
    @Transactional
    public PoDetail receive(Actor actor, Long id, ReceiptRequest request) {
        PurchaseOrder po = find(id);
        memberships.require(actor, po.getBuyerOrgId());
        if (!RECEIVABLE.contains(po.getStatus())) {
            throw new IllegalStateException(po.getStatus() == PurchaseOrderStatus.RECEIVED
                    ? "Everything on this order has been received"
                    : "Goods can be received once the supplier has acknowledged the order");
        }
        Map<Long, PurchaseOrderLine> lines = lines(po);
        Map<Long, BigDecimal> accepted = accepted(po);
        GoodsReceipt grn = new GoodsReceipt();
        grn.setPurchaseOrderId(id);
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
        return detail(po, actor);
    }

    /**
     * The buyer approves a matched invoice for payment, or rejects one (a rejected invoice frees
     * its quantities to be billed again). An exception cannot be approved: the supplier issues a
     * corrected invoice. The order closes once everything received has been billed and approved.
     */
    @Transactional
    public PoDetail decideInvoice(Actor actor, Long id, Long invoiceId, boolean approve, String note) {
        PurchaseOrder po = find(id);
        OrgMember me = memberships.require(actor, po.getBuyerOrgId());
        if (!me.canApprove()) {
            throw new AccessDeniedException("Only an owner or approver can decide on invoices");
        }
        SupplierInvoice inv = invoices.findById(invoiceId).filter(i -> i.getPurchaseOrderId().equals(id))
                .orElseThrow(() -> new NoSuchElementException("No such invoice on this order"));
        if (inv.getStatus() == InvoiceStatus.APPROVED || inv.getStatus() == InvoiceStatus.REJECTED) {
            throw new IllegalStateException("This invoice has already been " + inv.getStatus().name().toLowerCase());
        }
        if (approve && inv.getStatus() == InvoiceStatus.EXCEPTION) {
            throw new IllegalStateException("An invoice that fails the three-way match cannot be approved; reject it");
        }
        inv.setStatus(approve ? InvoiceStatus.APPROVED : InvoiceStatus.REJECTED);
        inv.setDecidedBy(actor.userId());
        inv.setDecidedAt(LocalDateTime.now(clock));
        inv.setDecisionNote(note == null || note.isBlank() ? null : note.trim());
        invoices.flush();
        if (approve && po.getStatus() == PurchaseOrderStatus.RECEIVED && fullyBilled(po)) {
            po.setStatus(PurchaseOrderStatus.CLOSED);
        }
        audit.record(actor.userId(), approve ? AuditAction.APPROVE : AuditAction.REJECT, "SUPPLIER_INVOICE", invoiceId,
                po.getNumber() + " " + inv.getInvoiceNumber());
        return detail(po, actor);
    }

    boolean needsApproval(PurchaseOrder po, Organization buyer) {
        return po.getTotal().compareTo(organizations.approvalThreshold(buyer)) > 0;
    }

    private boolean fullyBilled(PurchaseOrder po) {
        Map<Long, BigDecimal> approved = new HashMap<>();
        for (SupplierInvoice i : invoices.findByPurchaseOrderIdOrderByIdAsc(po.getId())) {
            if (i.getStatus() == InvoiceStatus.APPROVED) {
                i.getLines().forEach(l -> approved.merge(l.getPoLineId(), l.getQuantity(), BigDecimal::add));
            }
        }
        return po.getLines().stream()
                .allMatch(l -> approved.getOrDefault(l.getId(), BigDecimal.ZERO).compareTo(l.getQuantity()) >= 0);
    }

    private void requireChecker(Actor actor, PurchaseOrder po) {
        OrgMember me = memberships.require(actor, po.getBuyerOrgId());
        if (!me.canApprove()) {
            throw new AccessDeniedException("Only an owner or approver can approve orders");
        }
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
        Map<Long, BigDecimal> received = new HashMap<>();
        Map<Long, BigDecimal> accepted = new HashMap<>();
        Map<Long, BigDecimal> invoiced = new HashMap<>();
        grns.forEach(g -> g.getLines().forEach(l -> {
            received.merge(l.getPoLineId(), l.getReceivedQty(), BigDecimal::add);
            accepted.merge(l.getPoLineId(), l.acceptedQty(), BigDecimal::add);
        }));
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
                po.getDeliverySite(), po.getReference(),
                po.getLines().stream().map(l -> new PoLineView(l.getId(), l.getLineNo(), l.getDescription(), l.getQuantity(),
                        l.getUom(), l.getUnitPrice(), l.getTaxPercent(), Money.amount(l.getQuantity(), l.getUnitPrice()),
                        received.getOrDefault(l.getId(), zero), accepted.getOrDefault(l.getId(), zero),
                        invoiced.getOrDefault(l.getId(), zero))).toList(),
                grns.stream().map(g -> new ReceiptView(g.getId(), g.getNumber(), g.getNotes(),
                        g.getLines().stream().map(l -> new ReceiptLineView(l.getPoLineId(), l.getReceivedQty(), l.getRejectedQty())).toList(),
                        g.getCreatedAt())).toList(),
                invs.stream().map(i -> new InvoiceView(i.getId(), i.getInvoiceNumber(), i.getStatus(), i.getSubtotal(),
                        i.getTaxTotal(), i.getTotal(),
                        i.getMatchIssues() == null ? List.of() : List.of(i.getMatchIssues().split("\n")),
                        i.getLines().stream().map(l -> new InvoiceLineView(l.getPoLineId(), l.getQuantity(), l.getUnitPrice(), l.getTaxPercent())).toList(),
                        i.getDecisionNote(), i.getCreatedAt())).toList(),
                roles(po, mine.keySet()), canApprove, po.getCancelReason(), po.getApprovedAt(), po.getAcknowledgedAt(),
                po.getCreatedAt());
    }
}
