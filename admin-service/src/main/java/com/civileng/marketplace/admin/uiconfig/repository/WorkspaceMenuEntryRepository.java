package com.civileng.marketplace.admin.uiconfig.repository;

import com.civileng.marketplace.admin.uiconfig.model.WorkspaceMenuEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMenuEntryRepository extends JpaRepository<WorkspaceMenuEntry, Long> {

    List<WorkspaceMenuEntry> findByRole(String role);

    Optional<WorkspaceMenuEntry> findByRoleAndItemKey(String role, String itemKey);

    void deleteByRole(String role);
}
