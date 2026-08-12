package com.civileng.marketplace.project.repository;

import com.civileng.marketplace.project.model.Project;
import com.civileng.marketplace.project.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndIsDeletedFalse(Long id);

    Page<Project> findByOwnerIdAndIsDeletedFalseOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    Page<Project> findByOwnerIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
            Long ownerId, ProjectStatus status, Pageable pageable);

    Page<Project> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<Project> findByStatusAndIsDeletedFalseOrderByCreatedAtDesc(
            ProjectStatus status, Pageable pageable);
}
