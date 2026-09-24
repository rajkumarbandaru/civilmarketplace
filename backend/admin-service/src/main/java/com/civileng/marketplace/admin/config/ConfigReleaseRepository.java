package com.civileng.marketplace.admin.config;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ConfigReleaseRepository extends JpaRepository<ConfigRelease, Long> {

    List<ConfigRelease> findByScopeOrderByIdDesc(String scope, Pageable page);

    boolean existsByScopeAndSourceIn(String scope, Collection<ConfigRelease.Source> sources);

    long countByScope(String scope);
}
