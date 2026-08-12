package com.civileng.marketplace.project.repository;

import com.civileng.marketplace.project.model.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByProjectIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(Long projectId);

    Optional<Milestone> findByIdAndIsDeletedFalse(Long id);
}
