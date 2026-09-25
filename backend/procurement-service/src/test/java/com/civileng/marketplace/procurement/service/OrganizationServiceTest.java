package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.config.ProcurementProperties;
import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.OrganizationDtos.*;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.OrgMemberRepository;
import com.civileng.marketplace.procurement.repository.OrgRelationshipRepository;
import com.civileng.marketplace.procurement.repository.OrganizationRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.civileng.marketplace.procurement.service.Fixtures.member;
import static com.civileng.marketplace.procurement.service.Fixtures.org;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrganizationServiceTest {

    private static final Actor OWNER = new Actor(10L, "Owner@Example.com");

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OrgMemberRepository members = mock(OrgMemberRepository.class);
    private final OrgRelationshipRepository relationships = mock(OrgRelationshipRepository.class);
    private final com.civileng.marketplace.procurement.repository.PriceListRepository priceLists =
            mock(com.civileng.marketplace.procurement.repository.PriceListRepository.class);
    private final com.civileng.marketplace.procurement.client.AccountLookupClient accounts =
            mock(com.civileng.marketplace.procurement.client.AccountLookupClient.class);
    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizations, members, relationships, priceLists, new Memberships(members),
                new ProcurementProperties(null, null), accounts, mock(Audit.class));
    }

    private static OrgRelationship rel(long from, long to, RelationshipType type) {
        OrgRelationship r = new OrgRelationship();
        r.setFromOrgId(from);
        r.setToOrgId(to);
        r.setType(type);
        return r;
    }

    @Test
    void theCreatorBecomesOwnerAndNamesAreUnique() {
        when(organizations.save(any())).thenAnswer(i -> { Organization o = i.getArgument(0); o.setId(1L); return o; });
        OrganizationView v = service.create(OWNER, new OrganizationRequest(" BuildCo ", "", Set.of(Capability.BUYER), null));
        assertThat(v.myRole()).isEqualTo(MemberRole.OWNER);
        assertThat(v.name()).isEqualTo("BuildCo");
        assertThat(v.effectiveApprovalThreshold()).isEqualByComparingTo("100000");
        verify(members).save(argThat(m -> m.getRole() == MemberRole.OWNER && m.getUserId() == 10L
                && m.getEmail().equals("owner@example.com")));

        when(organizations.existsByNameIgnoreCase("buildco")).thenReturn(true);
        assertThatThrownBy(() -> service.create(OWNER, new OrganizationRequest("buildco", null, Set.of(Capability.BUYER), null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theDirectoryHidesSelfAndBlockedEitherWayAndPutsPreferredFirst() {
        when(members.findByUserId(10L)).thenReturn(List.of(member(1, 10, MemberRole.OWNER)));
        when(organizations.findAllByOrderByNameAsc()).thenReturn(List.of(
                org(1, "BuildCo", Capability.BUYER, Capability.SUPPLIER),
                org(2, "Alpha Cement", Capability.SUPPLIER),
                org(3, "Blocked Steel", Capability.SUPPLIER),
                org(4, "Blocker Bricks", Capability.SUPPLIER),
                org(5, "Zed Sand", Capability.SUPPLIER),
                org(6, "Digger", Capability.EQUIPMENT_PROVIDER)));
        when(relationships.blocksInvolving(1L)).thenReturn(List.of(
                rel(1, 3, RelationshipType.BLOCKED), rel(4, 1, RelationshipType.BLOCKED)));
        when(relationships.findByFromOrgId(1L)).thenReturn(List.of(
                rel(1, 3, RelationshipType.BLOCKED), rel(1, 5, RelationshipType.PREFERRED_SUPPLIER)));

        assertThat(service.directory(OWNER, Capability.SUPPLIER, 1L))
                .extracting(DirectoryEntry::name, DirectoryEntry::preferred)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Zed Sand", true),
                        org.assertj.core.groups.Tuple.tuple("Alpha Cement", false));
        assertThatThrownBy(() -> service.directory(OWNER, Capability.SUPPLIER, 2L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void someoneAddedByEmailIsLinkedTheFirstTimeTheyArrive() {
        OrgMember pending = member(7, 0, MemberRole.APPROVER);
        pending.setUserId(null);
        pending.setEmail("owner@example.com");
        when(members.findByUserIdIsNullAndEmailIgnoreCase("owner@example.com")).thenReturn(List.of(pending));
        new Memberships(members).of(OWNER);
        assertThat(pending.getUserId()).isEqualTo(10L);
    }

    @Test
    void onlyOwnersManageMembersAndTheLastOwnerStays() {
        OrgMember me = member(1, 10, MemberRole.OWNER);
        when(members.findByUserId(10L)).thenReturn(List.of(me));
        when(members.findById(me.getId())).thenReturn(Optional.of(me));
        when(members.countByOrganizationIdAndRole(1L, MemberRole.OWNER)).thenReturn(1L);
        assertThatThrownBy(() -> service.removeMember(OWNER, 1L, me.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least one owner");

        when(members.findByUserId(11L)).thenReturn(List.of(member(1, 11, MemberRole.APPROVER)));
        assertThatThrownBy(() -> service.addMember(new Actor(11L, "a@example.com"), 1L,
                new MemberRequest("new@example.com", MemberRole.MEMBER))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlySuppliersCanBePreferred() {
        when(members.findByUserId(10L)).thenReturn(List.of(member(1, 10, MemberRole.OWNER)));
        when(organizations.findById(6L)).thenReturn(Optional.of(org(6, "Digger", Capability.EQUIPMENT_PROVIDER)));
        assertThatThrownBy(() -> service.setRelationship(OWNER, 1L, new RelationshipRequest(6L, RelationshipType.PREFERRED_SUPPLIER)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setRelationship(OWNER, 1L, new RelationshipRequest(1L, RelationshipType.BLOCKED)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suppliersUnderContractComeFirstThenPreferred() {
        when(members.findByUserId(10L)).thenReturn(List.of(member(1, 10, MemberRole.OWNER)));
        when(organizations.findAllByOrderByNameAsc()).thenReturn(List.of(
                org(2, "Alpha Cement", Capability.SUPPLIER), org(3, "Beta Steel", Capability.SUPPLIER),
                org(4, "Gamma Sand", Capability.SUPPLIER)));
        when(relationships.findByFromOrgId(1L)).thenReturn(List.of(rel(1, 3, RelationshipType.PREFERRED_SUPPLIER)));
        PriceList contract = new PriceList();
        contract.setSupplierOrgId(4L);
        contract.setBuyerOrgId(1L);
        contract.setStatus(PriceListStatus.ACTIVE);
        PriceList expired = new PriceList();
        expired.setSupplierOrgId(2L);
        expired.setBuyerOrgId(1L);
        expired.setStatus(PriceListStatus.ACTIVE);
        expired.setValidUntil(java.time.LocalDate.now().minusDays(1));
        when(priceLists.findByBuyerOrgIdAndStatus(1L, PriceListStatus.ACTIVE)).thenReturn(List.of(contract, expired));

        assertThat(service.directory(OWNER, Capability.SUPPLIER, 1L))
                .extracting(DirectoryEntry::name, DirectoryEntry::contracted, DirectoryEntry::preferred)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Gamma Sand", true, false),
                        org.assertj.core.groups.Tuple.tuple("Beta Steel", false, true),
                        org.assertj.core.groups.Tuple.tuple("Alpha Cement", false, false));
    }

    @Test
    void aColleagueWithAnAccountIsLinkedWhenAddedAndOneWithoutIsLinkedLater() {
        when(members.findByUserId(10L)).thenReturn(List.of(member(1, 10, MemberRole.OWNER)));
        when(organizations.findById(1L)).thenReturn(Optional.of(org(1, "BuildCo", Capability.BUYER)));
        when(accounts.byEmail("anita@example.com"))
                .thenReturn(new com.civileng.marketplace.procurement.client.AccountLookupClient.AccountRef(11L, "anita@example.com", "Anita"));
        when(accounts.byEmail("new@example.com")).thenThrow(new RuntimeException("404"));
        service.addMember(OWNER, 1L, new MemberRequest("Anita@Example.com", MemberRole.APPROVER));
        service.addMember(OWNER, 1L, new MemberRequest("new@example.com", MemberRole.MEMBER));
        verify(members).save(argThat(m -> "anita@example.com".equals(m.getEmail()) && Long.valueOf(11L).equals(m.getUserId())));
        verify(members).save(argThat(m -> "new@example.com".equals(m.getEmail()) && m.getUserId() == null));
    }
}
