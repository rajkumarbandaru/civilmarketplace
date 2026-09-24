package com.civileng.marketplace.tenant.repository;

import com.civileng.marketplace.tenant.model.TenantDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantDraftRepository extends JpaRepository<TenantDraft, Long> {
    List<TenantDraft> findByStatusOrderByUpdatedAtDesc(TenantDraft.Status status);
}
