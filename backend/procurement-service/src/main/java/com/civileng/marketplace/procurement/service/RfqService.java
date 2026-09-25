package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.PurchaseOrderDtos.PoDetail;
import com.civileng.marketplace.procurement.dto.RfqDtos.*;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.PurchaseOrderRepository;
import com.civileng.marketplace.procurement.repository.QuotationRepository;
import com.civileng.marketplace.procurement.repository.RfqRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Requests for quotation and the quotations that answer them, up to the accepted one becoming a
 * purchase order.
 */
@Service
@RequiredArgsConstructor
public class RfqService {

    static final String BUYER = "BUYER";
    static final String SUPPLIER = "SUPPLIER";
    private static final String ENTITY = "RFQ";

    private final RfqRepository rfqs;
    private final QuotationRepository quotations;
    private final PurchaseOrderRepository orders;
    private final OrganizationService organizations;
    private final Memberships memberships;
    private final PurchaseOrderService purchaseOrders;
    private final PriceListService priceLists;
    private final Notifier notifier;
    private final Audit audit;
    private final Clock clock;

    @Transactional
    public RfqDetail create(Actor actor, CreateRfqRequest request) {
        memberships.require(actor, request.buyerOrgId());
        Organization buyer = organizations.find(request.buyerOrgId());
        if (!buyer.can(Capability.BUYER)) {
            throw new IllegalArgumentException(buyer.getName() + " is not set up as a buyer");
        }
        if (request.neededBy() != null && request.neededBy().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("The needed-by date has passed");
        }
        for (Long supplierId : request.supplierOrgIds()) {
            Organization supplier = organizations.find(supplierId);
            if (supplierId.equals(buyer.getId())) {
                throw new IllegalArgumentException("An organization cannot quote to itself");
            }
            if (!supplier.can(Capability.SUPPLIER)) {
                throw new IllegalArgumentException(supplier.getName() + " is not a supplier");
            }
            if (organizations.blocked(buyer.getId(), supplierId)) {
                throw new IllegalArgumentException(supplier.getName() + " cannot be invited");
            }
        }
        Rfq rfq = new Rfq();
        rfq.setBuyerOrgId(buyer.getId());
        rfq.setTitle(request.title().trim());
        rfq.setDeliverySite(blankToNull(request.deliverySite()));
        rfq.setNeededBy(request.neededBy());
        rfq.setReference(blankToNull(request.reference()));
        rfq.setStatus(RfqStatus.OPEN);
        rfq.setCreatedBy(actor.userId());
        rfq.getInvitedSupplierIds().addAll(request.supplierOrgIds());
        for (RfqLineRequest l : request.lines()) {
            RfqLine line = new RfqLine();
            line.setDescription(l.description().trim());
            line.setQuantity(l.quantity());
            line.setUom(l.uom().trim());
            rfq.addLine(line);
        }
        rfqs.save(rfq);
        rfq.setNumber("RFQ-%05d".formatted(rfq.getId()));
        audit.record(actor.userId(), AuditAction.CREATE, ENTITY, rfq.getId(),
                rfq.getNumber() + " to " + request.supplierOrgIds().size() + " supplier(s)");
        for (Long supplierId : request.supplierOrgIds()) {
            notifier.toOrg(supplierId, Notifier.EVERYONE, actor.userId(), "PROCUREMENT_RFQ_INVITED",
                    buyer.getName() + " asks for your quotation",
                    "%s: %s (%d item%s)%s. Quote from the Procurement workspace.".formatted(rfq.getNumber(), rfq.getTitle(),
                            rfq.getLines().size(), rfq.getLines().size() == 1 ? "" : "s",
                            rfq.getNeededBy() == null ? "" : ", needed by " + rfq.getNeededBy()),
                    ENTITY, rfq.getId(), "/procurement/rfqs/" + rfq.getId());
        }
        return detail(rfq, memberships.byOrganization(actor).keySet());
    }

    @Transactional
    public List<RfqSummary> list(Actor actor) {
        Set<Long> mine = memberships.byOrganization(actor).keySet();
        if (mine.isEmpty()) {
            return List.of();
        }
        List<Rfq> visible = rfqs.visibleTo(mine);
        Map<Long, String> names = organizations.names(visible.stream().map(Rfq::getBuyerOrgId).collect(Collectors.toSet()));
        return visible.stream().map(r -> {
            Set<String> roles = roles(r, mine);
            int count = roles.contains(BUYER) ? quotations.findByRfqIdOrderByTotalAsc(r.getId()).size() : 0;
            return new RfqSummary(r.getId(), r.getNumber(), r.getTitle(), new OrgRef(r.getBuyerOrgId(), names.get(r.getBuyerOrgId())),
                    r.getStatus(), r.getNeededBy(), count, roles, r.getCreatedAt());
        }).toList();
    }

    @Transactional
    public RfqDetail get(Actor actor, Long rfqId) {
        Rfq rfq = find(rfqId);
        Set<Long> mine = memberships.byOrganization(actor).keySet();
        if (roles(rfq, mine).isEmpty()) {
            throw new NoSuchElementException("No such RFQ");
        }
        return detail(rfq, mine);
    }

    /** Submit, or revise while the RFQ is still open. Every RFQ line is priced exactly once. */
    @Transactional
    public RfqDetail quote(Actor actor, Long rfqId, QuotationRequest request) {
        memberships.require(actor, request.supplierOrgId());
        Rfq rfq = find(rfqId);
        if (!rfq.getInvitedSupplierIds().contains(request.supplierOrgId())) {
            throw new AccessDeniedException("This organization was not invited to quote");
        }
        if (rfq.getStatus() != RfqStatus.OPEN) {
            throw new IllegalStateException("This RFQ is no longer taking quotations");
        }
        if (organizations.blocked(rfq.getBuyerOrgId(), request.supplierOrgId())) {
            throw new AccessDeniedException("This organization cannot quote to this buyer");
        }
        if (request.validUntil() != null && request.validUntil().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("The validity date has passed");
        }
        Map<Long, RfqLine> rfqLines = rfq.getLines().stream().collect(Collectors.toMap(RfqLine::getId, Function.identity()));
        Set<Long> priced = new HashSet<>();
        for (QuoteLineRequest l : request.lines()) {
            if (!rfqLines.containsKey(l.rfqLineId()) || !priced.add(l.rfqLineId())) {
                throw new IllegalArgumentException("Price each line of the RFQ exactly once");
            }
        }
        if (!priced.equals(rfqLines.keySet())) {
            throw new IllegalArgumentException("Price each line of the RFQ exactly once");
        }
        // A contract's rates are a ceiling: the supplier agreed to them for this buyer.
        Optional<PriceList> contract = priceLists.contractFor(rfq.getBuyerOrgId(), request.supplierOrgId());
        if (contract.isPresent()) {
            for (QuoteLineRequest l : request.lines()) {
                RfqLine line = rfqLines.get(l.rfqLineId());
                contract.get().getItems().stream()
                        .filter(i -> i.matches(line.getDescription(), line.getUom()))
                        .findFirst()
                        .filter(i -> l.unitPrice().compareTo(i.getUnitPrice()) > 0)
                        .ifPresent(i -> {
                            throw new IllegalArgumentException("Line %d: your contract rate with this buyer is %s per %s"
                                    .formatted(line.getLineNo(), i.getUnitPrice().toPlainString(), i.getUom()));
                        });
            }
        }

        Quotation q = quotations.findByRfqIdAndSupplierOrgId(rfqId, request.supplierOrgId()).orElseGet(Quotation::new);
        boolean revision = q.getId() != null;
        q.setRfqId(rfqId);
        q.setSupplierOrgId(request.supplierOrgId());
        q.setStatus(QuotationStatus.SUBMITTED);
        q.setValidUntil(request.validUntil());
        q.setNotes(blankToNull(request.notes()));
        q.setSubmittedBy(actor.userId());
        q.getLines().clear();
        Money.Totals totals = Money.Totals.ZERO;
        for (QuoteLineRequest l : request.lines()) {
            QuotationLine line = new QuotationLine();
            line.setRfqLineId(l.rfqLineId());
            line.setUnitPrice(l.unitPrice());
            line.setTaxPercent(l.taxPercent());
            q.addLine(line);
            totals = totals.plus(rfqLines.get(l.rfqLineId()).getQuantity(), l.unitPrice(), l.taxPercent());
        }
        q.setSubtotal(totals.subtotal());
        q.setTaxTotal(totals.tax());
        q.setTotal(totals.total());
        quotations.save(q);
        audit.record(actor.userId(), revision ? AuditAction.UPDATE : AuditAction.CREATE, "QUOTATION", q.getId(),
                rfq.getNumber() + " total " + q.getTotal());
        String supplierName = organizations.find(request.supplierOrgId()).getName();
        notifier.toOrg(rfq.getBuyerOrgId(), Notifier.EVERYONE, actor.userId(), "PROCUREMENT_QUOTATION_RECEIVED",
                supplierName + (revision ? " revised its quotation" : " sent a quotation"),
                "%s for %s: total %s incl. tax.".formatted(supplierName, rfq.getNumber(), q.getTotal().toPlainString()),
                ENTITY, rfq.getId(), "/procurement/rfqs/" + rfq.getId());
        return detail(rfq, memberships.byOrganization(actor).keySet());
    }

    /** The buyer takes one quotation: the RFQ is awarded, the others are declined, and an order is raised. */
    @Transactional
    public PoDetail accept(Actor actor, Long rfqId, Long quotationId) {
        Rfq rfq = find(rfqId);
        memberships.require(actor, rfq.getBuyerOrgId());
        if (rfq.getStatus() != RfqStatus.OPEN) {
            throw new IllegalStateException("This RFQ has already been " + rfq.getStatus().name().toLowerCase());
        }
        List<Quotation> all = quotations.findByRfqIdOrderByTotalAsc(rfqId);
        Quotation chosen = all.stream().filter(q -> q.getId().equals(quotationId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No such quotation on this RFQ"));
        if (chosen.getValidUntil() != null && chosen.getValidUntil().isBefore(LocalDate.now(clock))) {
            throw new IllegalStateException("This quotation expired on " + chosen.getValidUntil());
        }
        if (organizations.blocked(rfq.getBuyerOrgId(), chosen.getSupplierOrgId())) {
            throw new IllegalStateException("This supplier can no longer be traded with");
        }
        for (Quotation q : all) {
            q.setStatus(q == chosen ? QuotationStatus.ACCEPTED : QuotationStatus.REJECTED);
        }
        rfq.setStatus(RfqStatus.AWARDED);
        audit.record(actor.userId(), AuditAction.APPROVE, "QUOTATION", chosen.getId(), rfq.getNumber());
        for (Quotation q : all) {
            if (q != chosen) {
                notifier.toOrg(q.getSupplierOrgId(), Notifier.EVERYONE, null, "PROCUREMENT_QUOTATION_DECLINED",
                        "Quotation not selected: " + rfq.getNumber(),
                        "The buyer chose another supplier for " + rfq.getTitle() + ". Thank you for quoting.",
                        ENTITY, rfq.getId(), "/procurement/rfqs/" + rfq.getId());
            }
        }
        return purchaseOrders.raise(actor, rfq, chosen);
    }

    @Transactional
    public RfqDetail cancel(Actor actor, Long rfqId) {
        Rfq rfq = find(rfqId);
        memberships.require(actor, rfq.getBuyerOrgId());
        if (rfq.getStatus() != RfqStatus.OPEN) {
            throw new IllegalStateException("Only an open RFQ can be cancelled");
        }
        rfq.setStatus(RfqStatus.CANCELLED);
        audit.record(actor.userId(), AuditAction.DELETE, ENTITY, rfqId, rfq.getNumber());
        return detail(rfq, memberships.byOrganization(actor).keySet());
    }

    private Rfq find(Long id) {
        return rfqs.findById(id).orElseThrow(() -> new NoSuchElementException("No such RFQ"));
    }

    static Set<String> roles(Rfq rfq, Set<Long> myOrgs) {
        Set<String> roles = new TreeSet<>();
        if (myOrgs.contains(rfq.getBuyerOrgId())) {
            roles.add(BUYER);
        }
        if (rfq.getInvitedSupplierIds().stream().anyMatch(myOrgs::contains)) {
            roles.add(SUPPLIER);
        }
        return roles;
    }

    private RfqDetail detail(Rfq rfq, Set<Long> myOrgs) {
        Set<String> roles = roles(rfq, myOrgs);
        Set<Long> ids = new HashSet<>(rfq.getInvitedSupplierIds());
        ids.add(rfq.getBuyerOrgId());
        Map<Long, String> names = organizations.names(ids);
        Map<Long, RfqLine> lines = rfq.getLines().stream().collect(Collectors.toMap(RfqLine::getId, Function.identity()));

        List<Quotation> visible = quotations.findByRfqIdOrderByTotalAsc(rfq.getId());
        if (!roles.contains(BUYER)) {
            // A supplier sees only its own quotation, never a competitor's price.
            visible = visible.stream().filter(q -> myOrgs.contains(q.getSupplierOrgId())).toList();
        }
        List<QuotationView> quoteViews = visible.stream().map(q -> new QuotationView(q.getId(),
                new OrgRef(q.getSupplierOrgId(), names.get(q.getSupplierOrgId())), q.getStatus(), q.getValidUntil(),
                q.getNotes(), q.getSubtotal(), q.getTaxTotal(), q.getTotal(),
                q.getLines().stream().map(l -> new QuoteLineView(l.getRfqLineId(), l.getUnitPrice(), l.getTaxPercent(),
                        Money.amount(lines.get(l.getRfqLineId()).getQuantity(), l.getUnitPrice()))).toList(),
                q.getCreatedAt())).toList();

        List<PriceHint> hints = new ArrayList<>();
        if (roles.contains(SUPPLIER)) {
            for (Long supplierId : rfq.getInvitedSupplierIds()) {
                if (myOrgs.contains(supplierId)) {
                    hints.addAll(priceHints(rfq, supplierId));
                }
            }
        }
        Long poId = rfq.getStatus() == RfqStatus.AWARDED ? orders.findByRfqId(rfq.getId()).map(PurchaseOrder::getId).orElse(null) : null;
        return new RfqDetail(rfq.getId(), rfq.getNumber(), rfq.getTitle(), new OrgRef(rfq.getBuyerOrgId(), names.get(rfq.getBuyerOrgId())),
                rfq.getStatus(), rfq.getDeliverySite(), rfq.getNeededBy(), rfq.getReference(),
                rfq.getLines().stream().map(l -> new RfqLineView(l.getId(), l.getLineNo(), l.getDescription(), l.getQuantity(), l.getUom())).toList(),
                rfq.getInvitedSupplierIds().stream().map(id -> new OrgRef(id, names.get(id))).toList(),
                quoteViews, roles, poId, hints, rfq.getCreatedAt());
    }

    /**
     * For each RFQ line the supplier has a price for: its contract rate with this buyer (a
     * ceiling), else its catalogue price (a suggestion).
     */
    private List<PriceHint> priceHints(Rfq rfq, Long supplierId) {
        Optional<PriceList> contract = priceLists.contractFor(rfq.getBuyerOrgId(), supplierId);
        Optional<PriceList> catalogue = priceLists.catalogueOf(supplierId);
        List<PriceHint> hints = new ArrayList<>();
        for (RfqLine line : rfq.getLines()) {
            Optional<PriceHint> hint = contract.flatMap(c -> match(c, line))
                    .map(i -> new PriceHint(line.getId(), supplierId, i.getUnitPrice(), i.getTaxPercent(), "CONTRACT"))
                    .or(() -> catalogue.flatMap(c -> match(c, line))
                            .map(i -> new PriceHint(line.getId(), supplierId, i.getUnitPrice(), i.getTaxPercent(), "CATALOGUE")));
            hint.ifPresent(hints::add);
        }
        return hints;
    }

    private static Optional<PriceListItem> match(PriceList list, RfqLine line) {
        return list.getItems().stream().filter(i -> i.matches(line.getDescription(), line.getUom())).findFirst();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
