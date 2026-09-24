package com.civileng.marketplace.procurement.repository;

import com.civileng.marketplace.procurement.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrgRelationshipRepository extends JpaRepository<OrgRelationship, Long> {

    List<OrgRelationship> findByFromOrgId(Long fromOrgId);

    Optional<OrgRelationship> findByFromOrgIdAndToOrgId(Long fromOrgId, Long toOrgId);

    /** Blocks declared by either side between the two. */
    @Query("select r from OrgRelationship r where r.type = com.civileng.marketplace.procurement.model.RelationshipType.BLOCKED "
            + "and ((r.fromOrgId = :a and r.toOrgId = :b) or (r.fromOrgId = :b and r.toOrgId = :a))")
    List<OrgRelationship> blocksBetween(@Param("a") Long a, @Param("b") Long b);

    @Query("select r from OrgRelationship r where r.type = com.civileng.marketplace.procurement.model.RelationshipType.BLOCKED "
            + "and (r.fromOrgId = :org or r.toOrgId = :org)")
    List<OrgRelationship> blocksInvolving(@Param("org") Long org);
}
