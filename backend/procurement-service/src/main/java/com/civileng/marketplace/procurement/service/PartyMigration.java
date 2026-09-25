package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.procurement.client.AccountsClient;
import com.civileng.marketplace.procurement.client.MaterialPricesClient;
import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.MigrationDtos.MigrationEntry;
import com.civileng.marketplace.procurement.dto.MigrationDtos.MigrationReport;
import com.civileng.marketplace.procurement.dto.OrganizationDtos.OrganizationView;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.OrgMemberRepository;
import com.civileng.marketplace.procurement.repository.OrganizationRepository;
import com.civileng.marketplace.procurement.repository.PriceListRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import com.civileng.marketplace.web.common.StaffRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * The party-model migration (architecture 07 §5.1): the marketplace's existing trade accounts —
 * material suppliers, labour contractors, equipment rental — become organizations, so they can
 * trade B2B without re-registering. Each becomes the owner of an organization named after them,
 * with capabilities from their role, and a supplier's already-published material rates become its
 * catalogue.
 *
 * <p>Two ways in: each person for themselves ("set up from my profile"), or staff for everyone at
 * once, with a dry run first. Both are idempotent: an account is migrated at most once, which the
 * unique {@code source_user_id} enforces.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PartyMigration {

    /** Default GST on a migrated rate: the published rates carry no tax, and 18% is the common slab. */
    static final BigDecimal DEFAULT_TAX = new BigDecimal("18");

    public static final Map<String, Set<Capability>> CAPABILITIES_BY_ROLE = Map.of(
            "MATERIAL_SUPPLIER", EnumSet.of(Capability.SUPPLIER),
            "LABOUR_CONTRACTOR", EnumSet.of(Capability.CONTRACTOR, Capability.BUYER),
            "EQUIPMENT_RENTAL", EnumSet.of(Capability.EQUIPMENT_PROVIDER));

    private final OrganizationRepository organizations;
    private final OrgMemberRepository members;
    private final PriceListRepository priceLists;
    private final AccountsClient accounts;
    private final MaterialPricesClient materialPrices;
    private final OrganizationService organizationService;
    private final Audit audit;

    /** The caller's own account becomes an organization (or the one it already became is returned). */
    @Transactional
    public OrganizationView fromProfile(Actor actor, String role, String name) {
        if (actor.userId() == null || actor.email() == null) {
            throw new AccessDeniedException("Sign in to use procurement");
        }
        Set<Capability> caps = CAPABILITIES_BY_ROLE.get(role == null ? "" : role.toUpperCase());
        if (caps == null) {
            throw new IllegalArgumentException("Your account type has no organization to set up; create one instead");
        }
        MigrationEntry entry = migrate(actor.userId(), actor.email(), name, role.toUpperCase(), caps, actor.userId(), false);
        return organizationService.mine(actor).stream().filter(o -> o.id().equals(entry.organizationId())).findFirst()
                .orElseThrow();
    }

    /** Every trade account in the workspace. Staff only; {@code dryRun} writes nothing. */
    @Transactional
    public MigrationReport migrateAll(Actor actor, String callerRole, boolean dryRun) {
        if (!StaffRoles.isStaff(callerRole)) {
            throw new AccessDeniedException("Only staff can migrate accounts");
        }
        List<MigrationEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Set<Capability>> e : new TreeMap<>(CAPABILITIES_BY_ROLE).entrySet()) {
            for (Map<String, Object> user : accountsWithRole(e.getKey())) {
                if (!"ACTIVE".equals(String.valueOf(user.get("status")))) {
                    continue;
                }
                Long userId = ((Number) user.get("id")).longValue();
                String email = String.valueOf(user.get("email")).trim().toLowerCase();
                entries.add(migrate(userId, email, (String) user.get("name"), e.getKey(), e.getValue(), actor.userId(), dryRun));
            }
        }
        int created = (int) entries.stream().filter(x -> !"ALREADY_MIGRATED".equals(x.outcome())).count();
        if (!dryRun) {
            audit.record(actor.userId(), AuditAction.CREATE, "ORGANIZATION", "migration",
                    "party migration: " + created + " created of " + entries.size());
        }
        return new MigrationReport(dryRun, entries.size(), created, entries.size() - created, entries);
    }

    private MigrationEntry migrate(Long userId, String email, String name, String role, Set<Capability> caps,
                                   Long actorId, boolean dryRun) {
        Optional<Organization> existing = organizations.findBySourceUserId(userId);
        if (existing.isPresent()) {
            return new MigrationEntry(userId, email, name, role, existing.get().getCapabilities(), "ALREADY_MIGRATED",
                    existing.get().getId(), 0);
        }
        List<MaterialPricesClient.MaterialPrice> rates = caps.contains(Capability.SUPPLIER) ? ratesOf(userId) : List.of();
        String orgName = uniqueName(name == null || name.isBlank() ? email : name.trim(), email);
        if (dryRun) {
            return new MigrationEntry(userId, email, orgName, role, caps, "WOULD_CREATE", null, rates.size());
        }
        Organization org = new Organization();
        org.setName(orgName);
        org.setCapabilities(EnumSet.copyOf(caps));
        org.setSourceUserId(userId);
        org.setCreatedBy(actorId);
        organizations.save(org);

        OrgMember owner = members.findByOrganizationIdAndEmailIgnoreCase(org.getId(), email).orElseGet(OrgMember::new);
        owner.setOrganizationId(org.getId());
        owner.setEmail(email);
        owner.setUserId(userId);
        owner.setRole(MemberRole.OWNER);
        owner.setAddedBy(actorId);
        members.save(owner);

        if (!rates.isEmpty()) {
            PriceList catalogue = new PriceList();
            catalogue.setSupplierOrgId(org.getId());
            catalogue.setName(orgName + " catalogue");
            catalogue.setStatus(PriceListStatus.ACTIVE);
            catalogue.setCreatedBy(actorId);
            for (MaterialPricesClient.MaterialPrice r : rates) {
                PriceListItem item = new PriceListItem();
                item.setDescription(r.brand() == null || r.brand().isBlank() ? r.material() : r.material() + " (" + r.brand() + ")");
                item.setUom(r.unit());
                item.setUnitPrice(r.price());
                item.setTaxPercent(DEFAULT_TAX);
                catalogue.addItem(item);
            }
            priceLists.save(catalogue);
        }
        audit.record(actorId, AuditAction.CREATE, "ORGANIZATION", org.getId(), "migrated from account " + userId + " (" + role + ")");
        return new MigrationEntry(userId, email, orgName, role, caps, "CREATED", org.getId(), rates.size());
    }

    private String uniqueName(String name, String email) {
        if (!organizations.existsByNameIgnoreCase(name)) {
            return name;
        }
        String withEmail = name + " (" + email + ")";
        return withEmail.length() > 160 ? withEmail.substring(0, 160) : withEmail;
    }

    private List<MaterialPricesClient.MaterialPrice> ratesOf(Long userId) {
        try {
            return materialPrices.pricesOf(userId).stream()
                    .filter(r -> r.material() != null && r.unit() != null && r.price() != null).toList();
        } catch (RuntimeException e) {
            // The organization is worth creating even if its catalogue cannot be seeded now.
            log.warn("Could not read material rates of user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> accountsWithRole(String role) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (int page = 0; page < 100; page++) {
            Map<String, Object> body = accounts.users(role, page, 100);
            List<Map<String, Object>> data = (List<Map<String, Object>>) body.getOrDefault("data", List.of());
            all.addAll(data);
            Object pages = body.get("totalPages");
            if (data.isEmpty() || pages == null || page + 1 >= ((Number) pages).intValue()) {
                break;
            }
        }
        return all;
    }
}
