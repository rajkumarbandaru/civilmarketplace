package com.civileng.marketplace.procurement.repository;

import com.civileng.marketplace.procurement.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrgMemberRepository extends JpaRepository<OrgMember, Long> {

    List<OrgMember> findByOrganizationIdOrderByIdAsc(Long organizationId);

    Optional<OrgMember> findByOrganizationIdAndEmailIgnoreCase(Long organizationId, String email);

    List<OrgMember> findByUserId(Long userId);

    /** Rows added by email that nobody has claimed yet. */
    List<OrgMember> findByUserIdIsNullAndEmailIgnoreCase(String email);

    long countByOrganizationIdAndRole(Long organizationId, MemberRole role);
}
