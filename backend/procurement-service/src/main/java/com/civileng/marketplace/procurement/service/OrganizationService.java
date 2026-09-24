package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.procurement.config.ProcurementProperties;
import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.dto.OrganizationDtos.*;
import com.civileng.marketplace.procurement.model.*;
import com.civileng.marketplace.procurement.repository.OrgMemberRepository;
import com.civileng.marketplace.procurement.repository.OrgRelationshipRepository;
import com.civileng.marketplace.procurement.repository.OrganizationRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Organizations, their members, and whom they prefer or refuse to trade with. */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final String ENTITY = "ORGANIZATION";

    private final OrganizationRepository organizations;
    private final OrgMemberRepository members;
    private final OrgRelationshipRepository relationships;
    private final Memberships memberships;
    private final ProcurementProperties props;
    private final Audit audit;

    @Transactional
    public List<OrganizationView> mine(Actor actor) {
        Map<Long, OrgMember> mine = memberships.byOrganization(actor);
        return organizations.findAllById(mine.keySet()).stream()
                .sorted(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER))
                .map(o -> view(o, mine.get(o.getId()).getRole()))
                .toList();
    }

    @Transactional
    public OrganizationView create(Actor actor, OrganizationRequest request) {
        if (actor.userId() == null) {
            throw new AccessDeniedException("Sign in to use procurement");
        }
        if (actor.email() == null) {
            throw new IllegalArgumentException("Your account has no email address to add you as the owner with");
        }
        String name = request.name().trim();
        if (organizations.existsByNameIgnoreCase(name)) {
            throw new IllegalStateException("An organization called '" + name + "' already exists");
        }
        Organization org = new Organization();
        apply(org, request);
        org.setCreatedBy(actor.userId());
        organizations.save(org);

        OrgMember owner = new OrgMember();
        owner.setOrganizationId(org.getId());
        owner.setEmail(actor.email());
        owner.setUserId(actor.userId());
        owner.setRole(MemberRole.OWNER);
        owner.setAddedBy(actor.userId());
        members.save(owner);
        audit.record(actor.userId(), AuditAction.CREATE, ENTITY, org.getId(), org.getName());
        return view(org, MemberRole.OWNER);
    }

    @Transactional
    public OrganizationDetail get(Actor actor, Long orgId) {
        OrgMember me = memberships.require(actor, orgId);
        Organization org = find(orgId);
        return detail(org, me.getRole());
    }

    @Transactional
    public OrganizationDetail update(Actor actor, Long orgId, OrganizationRequest request) {
        requireOwner(actor, orgId);
        Organization org = find(orgId);
        String name = request.name().trim();
        if (!name.equalsIgnoreCase(org.getName()) && organizations.existsByNameIgnoreCase(name)) {
            throw new IllegalStateException("An organization called '" + name + "' already exists");
        }
        apply(org, request);
        audit.record(actor.userId(), AuditAction.UPDATE, ENTITY, orgId, org.getName());
        return detail(org, MemberRole.OWNER);
    }

    @Transactional
    public OrganizationDetail addMember(Actor actor, Long orgId, MemberRequest request) {
        requireOwner(actor, orgId);
        String email = request.email().trim().toLowerCase();
        if (members.findByOrganizationIdAndEmailIgnoreCase(orgId, email).isPresent()) {
            throw new IllegalStateException(email + " is already a member");
        }
        OrgMember m = new OrgMember();
        m.setOrganizationId(orgId);
        m.setEmail(email);
        m.setRole(request.role());
        m.setAddedBy(actor.userId());
        members.save(m);
        audit.record(actor.userId(), AuditAction.UPDATE, ENTITY, orgId, "member added: " + email + " as " + request.role());
        return detail(find(orgId), MemberRole.OWNER);
    }

    @Transactional
    public OrganizationDetail removeMember(Actor actor, Long orgId, Long memberId) {
        requireOwner(actor, orgId);
        OrgMember m = members.findById(memberId).filter(x -> x.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new NoSuchElementException("No such member"));
        if (m.getRole() == MemberRole.OWNER && members.countByOrganizationIdAndRole(orgId, MemberRole.OWNER) <= 1) {
            throw new IllegalStateException("An organization needs at least one owner");
        }
        members.delete(m);
        audit.record(actor.userId(), AuditAction.UPDATE, ENTITY, orgId, "member removed: " + m.getEmail());
        return detail(find(orgId), MemberRole.OWNER);
    }

    /**
     * Organizations with a capability, as {@code asOrgId} sees them: never itself, never one that
     * blocked it or that it blocked, and its preferred ones first.
     */
    @Transactional
    public List<DirectoryEntry> directory(Actor actor, Capability capability, Long asOrgId) {
        Set<Long> excluded = new HashSet<>();
        Set<Long> preferred = new HashSet<>();
        if (asOrgId != null) {
            memberships.require(actor, asOrgId);
            excluded.add(asOrgId);
            for (OrgRelationship r : relationships.blocksInvolving(asOrgId)) {
                excluded.add(r.getFromOrgId().equals(asOrgId) ? r.getToOrgId() : r.getFromOrgId());
            }
            relationships.findByFromOrgId(asOrgId).stream()
                    .filter(r -> r.getType() == RelationshipType.PREFERRED_SUPPLIER)
                    .forEach(r -> preferred.add(r.getToOrgId()));
        } else {
            memberships.of(actor);
        }
        return organizations.findAllByOrderByNameAsc().stream()
                .filter(o -> capability == null || o.can(capability))
                .filter(o -> !excluded.contains(o.getId()))
                .map(o -> new DirectoryEntry(o.getId(), o.getName(), o.getCapabilities(), preferred.contains(o.getId())))
                .sorted(Comparator.comparing((DirectoryEntry e) -> !e.preferred()))
                .toList();
    }

    @Transactional
    public OrganizationDetail setRelationship(Actor actor, Long orgId, RelationshipRequest request) {
        OrgMember me = memberships.require(actor, orgId);
        if (!me.canApprove()) {
            throw new AccessDeniedException("Only an owner or approver can change whom the organization trades with");
        }
        if (request.targetOrgId().equals(orgId)) {
            throw new IllegalArgumentException("An organization cannot have a relationship with itself");
        }
        Organization target = find(request.targetOrgId());
        if (request.type() == RelationshipType.PREFERRED_SUPPLIER && !target.can(Capability.SUPPLIER)) {
            throw new IllegalArgumentException(target.getName() + " is not a supplier");
        }
        OrgRelationship r = relationships.findByFromOrgIdAndToOrgId(orgId, target.getId()).orElseGet(() -> {
            OrgRelationship n = new OrgRelationship();
            n.setFromOrgId(orgId);
            n.setToOrgId(target.getId());
            return n;
        });
        r.setType(request.type());
        r.setCreatedBy(actor.userId());
        relationships.save(r);
        audit.record(actor.userId(), AuditAction.UPDATE, ENTITY, orgId, request.type() + " " + target.getName());
        return detail(find(orgId), me.getRole());
    }

    @Transactional
    public OrganizationDetail removeRelationship(Actor actor, Long orgId, Long relationshipId) {
        OrgMember me = memberships.require(actor, orgId);
        if (!me.canApprove()) {
            throw new AccessDeniedException("Only an owner or approver can change whom the organization trades with");
        }
        OrgRelationship r = relationships.findById(relationshipId).filter(x -> x.getFromOrgId().equals(orgId))
                .orElseThrow(() -> new NoSuchElementException("No such relationship"));
        relationships.delete(r);
        audit.record(actor.userId(), AuditAction.UPDATE, ENTITY, orgId, "relationship removed: " + r.getType());
        return detail(find(orgId), me.getRole());
    }

    public Organization find(Long id) {
        return organizations.findById(id).orElseThrow(() -> new NoSuchElementException("No such organization"));
    }

    public BigDecimal approvalThreshold(Organization org) {
        return org.getApprovalThreshold() != null ? org.getApprovalThreshold() : props.approvalThreshold();
    }

    /** Blocked by either side. */
    public boolean blocked(Long a, Long b) {
        return !relationships.blocksBetween(a, b).isEmpty();
    }

    public Map<Long, String> names(Collection<Long> ids) {
        return organizations.findAllById(ids).stream().collect(Collectors.toMap(Organization::getId, Organization::getName));
    }

    private void requireOwner(Actor actor, Long orgId) {
        if (memberships.require(actor, orgId).getRole() != MemberRole.OWNER) {
            throw new AccessDeniedException("Only an owner can change the organization");
        }
    }

    private static void apply(Organization org, OrganizationRequest request) {
        org.setName(request.name().trim());
        org.setGstin(request.gstin() == null || request.gstin().isBlank() ? null : request.gstin().trim());
        org.setCapabilities(EnumSet.copyOf(request.capabilities()));
        org.setApprovalThreshold(request.approvalThreshold());
    }

    private OrganizationView view(Organization o, MemberRole role) {
        return new OrganizationView(o.getId(), o.getName(), o.getGstin(), o.getCapabilities(),
                o.getApprovalThreshold(), approvalThreshold(o), role);
    }

    private OrganizationDetail detail(Organization org, MemberRole role) {
        List<MemberView> people = members.findByOrganizationIdOrderByIdAsc(org.getId()).stream()
                .map(m -> new MemberView(m.getId(), m.getEmail(), m.getRole(), m.getUserId() != null)).toList();
        List<OrgRelationship> rels = relationships.findByFromOrgId(org.getId());
        Map<Long, String> names = names(rels.stream().map(OrgRelationship::getToOrgId).toList());
        List<RelationshipView> relViews = rels.stream()
                .map(r -> new RelationshipView(r.getId(), r.getToOrgId(), names.get(r.getToOrgId()), r.getType())).toList();
        return new OrganizationDetail(view(org, role), people, relViews);
    }
}
