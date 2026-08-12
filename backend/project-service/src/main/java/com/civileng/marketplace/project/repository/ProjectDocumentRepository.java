package com.civileng.marketplace.project.repository;

import com.civileng.marketplace.project.model.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {

    List<ProjectDocument> findByProjectIdAndIsDeletedFalseOrderByCreatedAtDesc(Long projectId);

    Optional<ProjectDocument> findByIdAndIsDeletedFalse(Long id);
}
