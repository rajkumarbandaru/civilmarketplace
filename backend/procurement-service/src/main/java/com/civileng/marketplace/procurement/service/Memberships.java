package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.dto.Actor;
import com.civileng.marketplace.procurement.model.OrgMember;
import com.civileng.marketplace.procurement.repository.OrgMemberRepository;
import com.civileng.marketplace.web.common.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which organizations the caller acts for. A person added by email is linked to their account
 * the first time they call with that (gateway-verified) email.
 */
@Component
@RequiredArgsConstructor
public class Memberships {

    private final OrgMemberRepository members;

    @Transactional
    public List<OrgMember> of(Actor actor) {
        if (actor.userId() == null) {
            throw new AccessDeniedException("Sign in to use procurement");
        }
        if (actor.email() != null) {
            for (OrgMember pending : members.findByUserIdIsNullAndEmailIgnoreCase(actor.email())) {
                pending.setUserId(actor.userId());
            }
        }
        return members.findByUserId(actor.userId());
    }

    /** Organization id → the caller's membership. */
    public Map<Long, OrgMember> byOrganization(Actor actor) {
        return of(actor).stream().collect(Collectors.toMap(OrgMember::getOrganizationId, Function.identity()));
    }

    public Optional<OrgMember> in(Actor actor, Long organizationId) {
        return Optional.ofNullable(byOrganization(actor).get(organizationId));
    }

    public OrgMember require(Actor actor, Long organizationId) {
        return in(actor, organizationId)
                .orElseThrow(() -> new AccessDeniedException("You do not act for this organization"));
    }
}
