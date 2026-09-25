package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.PriceListDtos.*;
import com.civileng.marketplace.procurement.dto.RfqDtos.OrgRef;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.PriceListRepository;
import com.civileng.marketplace.procurement.repository.PurchaseOrderRepository;
import com.civileng.marketplace.procurement.repository.SupplierInvoiceRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Supplier catalogues and buyer contracts. A contract is proposed by the supplier and binds once
 * the buyer accepts it: its rates cap what the supplier may quote that buyer, its payment terms
 * go onto the buyer's orders, and its credit limit caps what the buyer may have open at once.
 */
@Service
@RequiredArgsConstructor
public class PriceListService {

    private static final String ENTITY = "PRICE_LIST";

    private final PriceListRepository priceLists;
    private final PurchaseOrderRepository orders;
    private final SupplierInvoiceRepository invoices;
    private final OrganizationService organizations;
    private final Memberships memberships;
    private final Notifier notifier;
    private final Audit audit;
    private final Clock clock;

    @Transactional
    public List<PriceListView> list(Actor actor) {
        Set<Long> mine = memberships.byOrganization(actor).keySet();
        if (mine.isEmpty()) {
            return List.of();
        }
        return priceLists.involving(mine).stream()
                // A buyer never sees another buyer's contract, nor a declined proposal it already answered.
                .filter(p -> mine.contains(p.getSupplierOrgId()) || p.getStatus() != PriceListStatus.DECLINED)
                .map(p -> view(p, mine)).toList();
    }

    /** Replaces the supplier's standard catalogue, creating it the first time. */
    @Transactional
    public PriceListView saveCatalogue(Actor actor, CatalogueRequest request) {
        requireManager(actor, request.supplierOrgId());
        Organization supplier = organizations.find(request.supplierOrgId());
        if (!supplier.can(Capability.SUPPLIER)) {
            throw new IllegalArgumentException(supplier.getName() + " is not a supplier");
        }
        PriceList list = catalogueOf(supplier.getId()).orElseGet(() -> {
            PriceList n = new PriceList();
            n.setSupplierOrgId(supplier.getId());
            n.setStatus(PriceListStatus.ACTIVE);
            n.setCreatedBy(actor.userId());
            return n;
        });
        list.setName(request.name() == null || request.name().isBlank() ? supplier.getName() + " catalogue" : request.name().trim());
        list.getItems().clear();
        request.items().forEach(i -> list.addItem(item(i)));
        priceLists.save(list);
        audit.record(actor.userId(), AuditAction.UPDATE, ENTITY, list.getId(), "catalogue: " + list.getItems().size() + " items");
        return view(list, Set.of(supplier.getId()));
    }

    @Transactional
    public PriceListView propose(Actor actor, ContractRequest request) {
        requireManager(actor, request.supplierOrgId());
        Organization supplier = organizations.find(request.supplierOrgId());
        Organization buyer = organizations.find(request.buyerOrgId());
        if (!supplier.can(Capability.SUPPLIER)) {
            throw new IllegalArgumentException(supplier.getName() + " is not a supplier");
        }
        if (!buyer.can(Capability.BUYER) || buyer.getId().equals(supplier.getId())) {
            throw new IllegalArgumentException(buyer.getName() + " cannot be offered a contract");
        }
        if (organizations.blocked(buyer.getId(), supplier.getId())) {
            throw new IllegalArgumentException(buyer.getName() + " cannot be offered a contract");
        }
        if (request.validFrom() != null && request.validUntil() != null && request.validUntil().isBefore(request.validFrom())) {
            throw new IllegalArgumentException("The contract ends before it starts");
        }
        if (request.validUntil() != null && request.validUntil().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("The contract's end date has passed");
        }
        PriceList c = new PriceList();
        c.setSupplierOrgId(supplier.getId());
        c.setBuyerOrgId(buyer.getId());
        c.setName(request.name().trim());
        c.setStatus(PriceListStatus.PROPOSED);
        c.setPaymentTermsDays(request.paymentTermsDays());
        c.setCreditLimit(request.creditLimit());
        c.setValidFrom(request.validFrom());
        c.setValidUntil(request.validUntil());
        c.setCreatedBy(actor.userId());
        request.items().forEach(i -> c.addItem(item(i)));
        priceLists.save(c);
        audit.record(actor.userId(), AuditAction.CREATE, ENTITY, c.getId(), "contract proposed to " + buyer.getName());
        notifier.toOrg(buyer.getId(), Notifier.APPROVERS, actor.userId(), "PROCUREMENT_CONTRACT_PROPOSED",
                "Contract offered by " + supplier.getName(),
                supplier.getName() + " offers " + c.getName() + ": Net " + c.getPaymentTermsDays() + ", "
                        + c.getItems().size() + " contract rates. Accept it to buy on these terms.",
                "PRICE_LIST", c.getId(), "/procurement?tab=prices");
        return view(c, Set.of(supplier.getId()));
    }

    /** The buyer accepts: the contract replaces any earlier one between the two. */
    @Transactional
    public PriceListView accept(Actor actor, Long id) {
        PriceList c = findContract(id);
        requireManager(actor, c.getBuyerOrgId());
        if (c.getStatus() != PriceListStatus.PROPOSED) {
            throw new IllegalStateException("Only a proposed contract can be accepted");
        }
        for (PriceList earlier : priceLists.findBySupplierOrgIdAndBuyerOrgIdAndStatus(
                c.getSupplierOrgId(), c.getBuyerOrgId(), PriceListStatus.ACTIVE)) {
            earlier.setStatus(PriceListStatus.TERMINATED);
        }
        decide(c, actor, PriceListStatus.ACTIVE);
        audit.record(actor.userId(), AuditAction.APPROVE, ENTITY, id, "contract accepted");
        notifyDecision(c, actor, "PROCUREMENT_CONTRACT_ACCEPTED", "accepted");
        return view(c, Set.of(c.getBuyerOrgId()));
    }

    @Transactional
    public PriceListView decline(Actor actor, Long id) {
        PriceList c = findContract(id);
        requireManager(actor, c.getBuyerOrgId());
        if (c.getStatus() != PriceListStatus.PROPOSED) {
            throw new IllegalStateException("Only a proposed contract can be declined");
        }
        decide(c, actor, PriceListStatus.DECLINED);
        audit.record(actor.userId(), AuditAction.REJECT, ENTITY, id, "contract declined");
        notifyDecision(c, actor, "PROCUREMENT_CONTRACT_DECLINED", "declined");
        return view(c, Set.of(c.getBuyerOrgId()));
    }

    /** Either side ends an active contract (or the supplier withdraws a proposal). Open orders keep their terms. */
    @Transactional
    public PriceListView terminate(Actor actor, Long id) {
        PriceList c = findContract(id);
        Set<Long> mine = memberships.byOrganization(actor).keySet();
        Long side = mine.contains(c.getSupplierOrgId()) ? c.getSupplierOrgId() : c.getBuyerOrgId();
        requireManager(actor, side);
        if (c.getStatus() != PriceListStatus.ACTIVE && c.getStatus() != PriceListStatus.PROPOSED) {
            throw new IllegalStateException("This contract has already ended");
        }
        decide(c, actor, PriceListStatus.TERMINATED);
        audit.record(actor.userId(), AuditAction.DELETE, ENTITY, id, "contract terminated");
        return view(c, mine);
    }

    /** The contract in force today between a buyer and a supplier, the most recent if somehow several. */
    public Optional<PriceList> contractFor(Long buyerOrgId, Long supplierOrgId) {
        LocalDate today = LocalDate.now(clock);
        return priceLists.findBySupplierOrgIdAndBuyerOrgIdAndStatus(supplierOrgId, buyerOrgId, PriceListStatus.ACTIVE)
                .stream().filter(p -> p.inForce(today)).max(Comparator.comparing(PriceList::getId));
    }

    public Optional<PriceList> catalogueOf(Long supplierOrgId) {
        return priceLists.findBySupplierOrgIdAndBuyerOrgIdIsNullAndStatus(supplierOrgId, PriceListStatus.ACTIVE)
                .stream().max(Comparator.comparing(PriceList::getId));
    }

    /** Suppliers the buyer has a contract in force with. */
    public Set<Long> contractedSuppliers(Long buyerOrgId) {
        LocalDate today = LocalDate.now(clock);
        Set<Long> ids = new HashSet<>();
        priceLists.findByBuyerOrgIdAndStatus(buyerOrgId, PriceListStatus.ACTIVE).stream()
                .filter(p -> p.inForce(today)).forEach(p -> ids.add(p.getSupplierOrgId()));
        return ids;
    }

    /**
     * What the buyer has open with the supplier: every order not cancelled, less what has been
     * paid against it.
     */
    public BigDecimal exposure(Long buyerOrgId, Long supplierOrgId) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrder po : orders.findByBuyerOrgIdAndSupplierOrgId(buyerOrgId, supplierOrgId)) {
            if (po.getStatus() == PurchaseOrderStatus.CANCELLED) {
                continue;
            }
            total = total.add(po.getTotal());
            for (SupplierInvoice i : invoices.findByPurchaseOrderIdOrderByIdAsc(po.getId())) {
                if (i.getStatus() == InvoiceStatus.PAID) {
                    total = total.subtract(i.getTotal());
                }
            }
        }
        return total.max(BigDecimal.ZERO);
    }

    private void decide(PriceList c, Actor actor, PriceListStatus status) {
        c.setStatus(status);
        c.setDecidedBy(actor.userId());
        c.setDecidedAt(LocalDateTime.now(clock));
    }

    private void notifyDecision(PriceList c, Actor actor, String type, String verb) {
        String buyer = organizations.find(c.getBuyerOrgId()).getName();
        notifier.toOrg(c.getSupplierOrgId(), Notifier.APPROVERS, actor.userId(), type,
                "Contract " + verb + " by " + buyer, buyer + " " + verb + " " + c.getName() + ".",
                "PRICE_LIST", c.getId(), "/procurement?tab=prices");
    }

    private PriceList findContract(Long id) {
        return priceLists.findById(id).filter(PriceList::isContract)
                .orElseThrow(() -> new NoSuchElementException("No such contract"));
    }

    private void requireManager(Actor actor, Long orgId) {
        if (!memberships.require(actor, orgId).canApprove()) {
            throw new AccessDeniedException("Only an owner or approver can change prices and contracts");
        }
    }

    private static PriceListItem item(ItemRequest i) {
        PriceListItem item = new PriceListItem();
        item.setDescription(i.description().trim());
        item.setUom(i.uom().trim());
        item.setUnitPrice(i.unitPrice());
        item.setTaxPercent(i.taxPercent());
        return item;
    }

    private PriceListView view(PriceList p, Set<Long> mine) {
        Set<Long> ids = new HashSet<>();
        ids.add(p.getSupplierOrgId());
        if (p.getBuyerOrgId() != null) {
            ids.add(p.getBuyerOrgId());
        }
        Map<Long, String> names = organizations.names(ids);
        List<String> roles = new ArrayList<>();
        if (mine.contains(p.getSupplierOrgId())) {
            roles.add(RfqService.SUPPLIER);
        }
        if (p.getBuyerOrgId() != null && mine.contains(p.getBuyerOrgId())) {
            roles.add(RfqService.BUYER);
        }
        BigDecimal exposure = p.isContract() && p.getStatus() == PriceListStatus.ACTIVE
                ? exposure(p.getBuyerOrgId(), p.getSupplierOrgId()) : null;
        return new PriceListView(p.getId(), new OrgRef(p.getSupplierOrgId(), names.get(p.getSupplierOrgId())),
                p.getBuyerOrgId() == null ? null : new OrgRef(p.getBuyerOrgId(), names.get(p.getBuyerOrgId())),
                p.getName(), p.getStatus(), p.isContract(), p.getPaymentTermsDays(), p.getCreditLimit(), exposure,
                p.getValidFrom(), p.getValidUntil(),
                p.getItems().stream().map(i -> new ItemView(i.getId(), i.getDescription(), i.getUom(), i.getUnitPrice(),
                        i.getTaxPercent())).toList(),
                roles, p.getCreatedAt(), p.getDecidedAt());
    }
}
