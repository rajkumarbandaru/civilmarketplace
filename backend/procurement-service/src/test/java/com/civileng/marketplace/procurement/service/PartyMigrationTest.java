package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.client.AccountsClient;
import com.civileng.marketplace.procurement.client.MaterialPricesClient;
import com.civileng.marketplace.procurement.client.MaterialPricesClient.MaterialPrice;
import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.MigrationDtos.MigrationReport;
import com.civileng.marketplace.procurement.dto.OrganizationDtos.OrganizationView;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.OrgMemberRepository;
import com.civileng.marketplace.procurement.repository.OrganizationRepository;
import com.civileng.marketplace.procurement.repository.PriceListRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PartyMigrationTest {

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OrgMemberRepository members = mock(OrgMemberRepository.class);
    private final PriceListRepository priceLists = mock(PriceListRepository.class);
    private final AccountsClient accounts = mock(AccountsClient.class);
    private final MaterialPricesClient rates = mock(MaterialPricesClient.class);
    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final Map<Long, Organization> bySource = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(100);
    private PartyMigration migration;

    @BeforeEach
    void setUp() {
        when(organizations.save(any())).thenAnswer(i -> {
            Organization o = i.getArgument(0);
            o.setId(ids.incrementAndGet());
            bySource.put(o.getSourceUserId(), o);
            return o;
        });
        when(organizations.findBySourceUserId(anyLong())).thenAnswer(i -> Optional.ofNullable(bySource.get(i.getArgument(0, Long.class))));
        when(members.findByOrganizationIdAndEmailIgnoreCase(anyLong(), any())).thenReturn(Optional.empty());
        migration = new PartyMigration(organizations, members, priceLists, accounts, rates, organizationService, mock(Audit.class));
    }

    private static Map<String, Object> account(long id, String email, String name, String role) {
        return Map.of("id", id, "email", email, "name", name, "role", role, "status", "ACTIVE");
    }

    @Test
    void aSupplierAccountBecomesASupplierOrganizationWithItsPublishedRates() {
        when(rates.pricesOf(20L)).thenReturn(List.of(new MaterialPrice("OPC 53 Cement", "bag", new BigDecimal("395"), "UltraTech"),
                new MaterialPrice("River Sand", "cft", new BigDecimal("58"), null)));
        Actor deepak = new Actor(20L, "supplier@example.com");
        when(organizationService.mine(deepak)).thenAnswer(i -> bySource.values().stream().map(o -> new OrganizationView(
                o.getId(), o.getName(), null, o.getCapabilities(), null, BigDecimal.ONE, MemberRole.OWNER)).toList());

        OrganizationView made = migration.fromProfile(deepak, "material_supplier", "Deepak Supplier");
        assertThat(made.name()).isEqualTo("Deepak Supplier");
        assertThat(made.capabilities()).containsExactly(Capability.SUPPLIER);
        verify(members).save(argThat(m -> m.getRole() == MemberRole.OWNER && m.getUserId() == 20L));
        ArgumentCaptor<PriceList> catalogue = ArgumentCaptor.forClass(PriceList.class);
        verify(priceLists).save(catalogue.capture());
        assertThat(catalogue.getValue().getBuyerOrgId()).isNull();
        assertThat(catalogue.getValue().getItems()).extracting(PriceListItem::getDescription, PriceListItem::getUom)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("OPC 53 Cement (UltraTech)", "bag"),
                        org.assertj.core.groups.Tuple.tuple("River Sand", "cft"));
        assertThat(catalogue.getValue().getItems()).allMatch(i -> i.getTaxPercent().compareTo(new BigDecimal("18")) == 0);

        // Again: the same organization, nothing new.
        assertThat(migration.fromProfile(deepak, "MATERIAL_SUPPLIER", "Deepak Supplier").id()).isEqualTo(made.id());
        verify(organizations, times(1)).save(any());
    }

    @Test
    void onlyTradeAccountsHaveAnOrganizationToSetUp() {
        assertThatThrownBy(() -> migration.fromProfile(new Actor(7L, "c@example.com"), "CUSTOMER", "Ravi"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staffPreviewThenMigrateEveryTradeAccountOnce() {
        when(accounts.users(eq("LABOUR_CONTRACTOR"), anyInt(), anyInt())).thenReturn(Map.of(
                "data", List.of(account(30, "kiran@example.com", "Kiran Contractor", "LABOUR_CONTRACTOR")), "totalPages", 1));
        when(accounts.users(eq("MATERIAL_SUPPLIER"), anyInt(), anyInt())).thenReturn(Map.of(
                "data", List.of(account(20, "deepak@example.com", "Deepak", "MATERIAL_SUPPLIER"),
                        Map.of("id", 21, "email", "gone@example.com", "name", "Gone", "role", "MATERIAL_SUPPLIER",
                                "status", "SUSPENDED")), "totalPages", 1));
        when(accounts.users(eq("EQUIPMENT_RENTAL"), anyInt(), anyInt())).thenReturn(Map.of("data", List.of(), "totalPages", 0));
        when(rates.pricesOf(anyLong())).thenThrow(new RuntimeException("user-service down"));
        when(organizations.existsByNameIgnoreCase("Deepak")).thenReturn(true);

        assertThatThrownBy(() -> migration.migrateAll(new Actor(1L, "a@example.com"), "CUSTOMER", true))
                .isInstanceOf(AccessDeniedException.class);

        MigrationReport preview = migration.migrateAll(new Actor(1L, "a@example.com"), "ADMIN", true);
        assertThat(preview.dryRun()).isTrue();
        assertThat(preview.entries()).extracting(e -> e.outcome()).containsOnly("WOULD_CREATE");
        assertThat(preview.accounts()).isEqualTo(2);
        verify(organizations, never()).save(any());

        MigrationReport done = migration.migrateAll(new Actor(1L, "a@example.com"), "ADMIN", false);
        assertThat(done.created()).isEqualTo(2);
        assertThat(bySource.get(30L).getCapabilities()).containsExactlyInAnyOrder(Capability.CONTRACTOR, Capability.BUYER);
        assertThat(bySource.get(20L).getName()).isEqualTo("Deepak (deepak@example.com)");
        verify(priceLists, never()).save(any());   // rates unavailable: organization still made

        MigrationReport again = migration.migrateAll(new Actor(1L, "a@example.com"), "ADMIN", false);
        assertThat(again.created()).isZero();
        assertThat(again.alreadyMigrated()).isEqualTo(2);
    }
}
