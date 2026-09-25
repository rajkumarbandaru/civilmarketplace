package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.PriceListDtos.*;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.OrgMemberRepository;
import com.civileng.marketplace.procurement.repository.PriceListRepository;
import com.civileng.marketplace.procurement.repository.PurchaseOrderRepository;
import com.civileng.marketplace.procurement.repository.SupplierInvoiceRepository;
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

class PriceListServiceTest {

    // Buyer org 1 (user 10 owner, 12 member); supplier org 2 (user 20 owner).
    private static final Actor BUYER = new Actor(10L, "u10@example.com");
    private static final Actor BUYER_STAFF = new Actor(12L, "u12@example.com");
    private static final Actor SUPPLIER = new Actor(20L, "u20@example.com");

    private final PriceListRepository repo = mock(PriceListRepository.class);
    private final PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
    private final SupplierInvoiceRepository invoices = mock(SupplierInvoiceRepository.class);
    private final OrganizationService organizations = mock(OrganizationService.class);
    private final OrgMemberRepository memberRepo = mock(OrgMemberRepository.class);
    private final Notifier notifier = mock(Notifier.class);
    private final List<PriceList> saved = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private PriceListService service;

    @BeforeEach
    void setUp() {
        Map<Long, List<OrgMember>> byUser = Map.of(
                10L, List.of(member(1, 10, MemberRole.OWNER)),
                12L, List.of(member(1, 12, MemberRole.MEMBER)),
                20L, List.of(member(2, 20, MemberRole.OWNER)));
        when(memberRepo.findByUserId(anyLong())).thenAnswer(i -> byUser.getOrDefault(i.getArgument(0, Long.class), List.of()));
        Map<Long, Organization> orgs = Map.of(1L, org(1, "BuildCo", Capability.BUYER), 2L, org(2, "CementCo", Capability.SUPPLIER));
        when(organizations.find(anyLong())).thenAnswer(i -> orgs.get(i.getArgument(0, Long.class)));
        when(organizations.names(any())).thenReturn(Map.of(1L, "BuildCo", 2L, "CementCo"));
        when(repo.save(any())).thenAnswer(i -> {
            PriceList p = i.getArgument(0);
            if (p.getId() == null) {
                p.setId(ids.incrementAndGet());
                saved.add(p);
            }
            return p;
        });
        when(repo.findById(anyLong())).thenAnswer(i -> saved.stream().filter(p -> p.getId().equals(i.getArgument(0, Long.class))).findFirst());
        when(repo.findBySupplierOrgIdAndBuyerOrgIdAndStatus(anyLong(), anyLong(), any())).thenAnswer(i -> saved.stream()
                .filter(p -> p.getSupplierOrgId().equals(i.getArgument(0)) && Objects.equals(p.getBuyerOrgId(), i.getArgument(1))
                        && p.getStatus() == i.getArgument(2)).toList());
        when(repo.findBySupplierOrgIdAndBuyerOrgIdIsNullAndStatus(anyLong(), any())).thenAnswer(i -> saved.stream()
                .filter(p -> p.getSupplierOrgId().equals(i.getArgument(0)) && p.getBuyerOrgId() == null
                        && p.getStatus() == i.getArgument(1)).toList());
        service = new PriceListService(repo, orders, invoices, organizations, new Memberships(memberRepo), notifier,
                mock(Audit.class), Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));
    }

    private ContractRequest contract(String cement, int net, String limit) {
        return new ContractRequest(2L, 1L, "Cement 2026-27", net, limit == null ? null : new BigDecimal(limit),
                LocalDate.parse("2026-09-01"), LocalDate.parse("2027-03-31"),
                List.of(new ItemRequest("OPC 53 cement", "bag", new BigDecimal(cement), new BigDecimal("28"))));
    }

    @Test
    void theSupplierProposesAndOnlyTheBuyersManagersCanAccept() {
        assertThatThrownBy(() -> service.propose(BUYER, contract("380", 30, null))).isInstanceOf(AccessDeniedException.class);
        PriceListView v = service.propose(SUPPLIER, contract("380", 30, "500000"));
        assertThat(v.status()).isEqualTo(PriceListStatus.PROPOSED);
        verify(notifier).toOrg(eq(1L), same(Notifier.APPROVERS), eq(20L), eq("PROCUREMENT_CONTRACT_PROPOSED"), any(), any(),
                any(), any(), any());
        assertThat(service.contractFor(1L, 2L)).isEmpty();

        assertThatThrownBy(() -> service.accept(SUPPLIER, v.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.accept(BUYER_STAFF, v.id())).isInstanceOf(AccessDeniedException.class);
        assertThat(service.accept(BUYER, v.id()).status()).isEqualTo(PriceListStatus.ACTIVE);
        assertThat(service.contractFor(1L, 2L)).get().extracting(PriceList::getPaymentTermsDays).isEqualTo(30);
        assertThatThrownBy(() -> service.accept(BUYER, v.id())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aNewContractSupersedesTheOldOneAndEitherSideCanEndIt() {
        Long first = service.propose(SUPPLIER, contract("380", 30, null)).id();
        service.accept(BUYER, first);
        Long second = service.propose(SUPPLIER, contract("370", 45, null)).id();
        service.accept(BUYER, second);
        assertThat(saved.get(0).getStatus()).isEqualTo(PriceListStatus.TERMINATED);
        assertThat(service.contractFor(1L, 2L)).get().extracting(PriceList::getId).isEqualTo(second);
        service.terminate(BUYER, second);
        assertThat(service.contractFor(1L, 2L)).isEmpty();
    }

    @Test
    void aContractOutsideItsDatesIsNotInForce() {
        ContractRequest ended = new ContractRequest(2L, 1L, "Old", 30, null, LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-09-01"), contract("380", 30, null).items());
        assertThatThrownBy(() -> service.propose(SUPPLIER, ended)).hasMessageContaining("end date has passed");
        Long id = service.propose(SUPPLIER, new ContractRequest(2L, 1L, "Next year", 30, null, LocalDate.parse("2027-04-01"),
                null, contract("380", 30, null).items())).id();
        service.accept(BUYER, id);
        assertThat(service.contractFor(1L, 2L)).isEmpty();
    }

    @Test
    void theCatalogueIsReplacedWholesale() {
        service.saveCatalogue(SUPPLIER, new CatalogueRequest(2L, null, List.of(
                new ItemRequest("OPC 53 cement", "bag", new BigDecimal("410"), new BigDecimal("28")),
                new ItemRequest("River sand", "cft", new BigDecimal("60"), new BigDecimal("5")))));
        PriceListView v = service.saveCatalogue(SUPPLIER, new CatalogueRequest(2L, "Monsoon rates", List.of(
                new ItemRequest("OPC 53 cement", "bag", new BigDecimal("405"), new BigDecimal("28")))));
        assertThat(saved).hasSize(1);
        assertThat(v.name()).isEqualTo("Monsoon rates");
        assertThat(v.items()).singleElement().extracting(ItemView::unitPrice).isEqualTo(new BigDecimal("405"));
        assertThat(v.contract()).isFalse();
        assertThatThrownBy(() -> service.saveCatalogue(BUYER, new CatalogueRequest(1L, null, List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a supplier");
    }

    @Test
    void exposureIsOpenOrdersLessWhatHasBeenPaid() {
        PurchaseOrder open = new PurchaseOrder();
        open.setId(1L);
        open.setStatus(PurchaseOrderStatus.RECEIVED);
        open.setTotal(new BigDecimal("100000"));
        PurchaseOrder cancelled = new PurchaseOrder();
        cancelled.setId(2L);
        cancelled.setStatus(PurchaseOrderStatus.CANCELLED);
        cancelled.setTotal(new BigDecimal("999999"));
        when(orders.findByBuyerOrgIdAndSupplierOrgId(1L, 2L)).thenReturn(List.of(open, cancelled));
        SupplierInvoice paid = new SupplierInvoice();
        paid.setStatus(InvoiceStatus.PAID);
        paid.setTotal(new BigDecimal("40000"));
        SupplierInvoice approved = new SupplierInvoice();
        approved.setStatus(InvoiceStatus.APPROVED);
        approved.setTotal(new BigDecimal("30000"));
        when(invoices.findByPurchaseOrderIdOrderByIdAsc(1L)).thenReturn(List.of(paid, approved));
        assertThat(service.exposure(1L, 2L)).isEqualByComparingTo("60000");
    }
}
