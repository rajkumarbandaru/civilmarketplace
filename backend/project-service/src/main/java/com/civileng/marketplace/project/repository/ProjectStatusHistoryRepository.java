package com.civileng.marketplace.project.repository;

import com.civileng.marketplace.project.model.ProjectStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectStatusHistoryRepository extends JpaRepository<ProjectStatusHistory, Long> {

    List<ProjectStatusHistory> findByProjectIdOrderByChangedAtAsc(Long projectId);
}
