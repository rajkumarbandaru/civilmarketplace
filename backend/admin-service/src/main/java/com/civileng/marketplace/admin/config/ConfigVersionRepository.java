package com.civileng.marketplace.admin.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Long> {

    List<ConfigVersion> findByReleaseId(Long releaseId);

    /** The version of a document that was live once release {@code releaseId} had been published. */
    Optional<ConfigVersion> findFirstByScopeAndDocumentAndReleaseIdLessThanEqualOrderByIdDesc(
            String scope, String document, Long releaseId);

    @Query("select coalesce(max(v.versionNo), 0) from ConfigVersion v where v.scope = ?1 and v.document = ?2")
    int maxVersionNo(String scope, String document);
}
