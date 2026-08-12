package com.civileng.marketplace.user.repository;

import com.civileng.marketplace.user.model.KycDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    List<KycDocument> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<KycDocument> findByIdAndUserId(Long id, Long userId);

    Page<KycDocument> findByStatus(KycDocument.KycStatus status, Pageable pageable);

    boolean existsByUserIdAndDocumentTypeAndStatus(
            Long userId, KycDocument.DocumentType documentType, KycDocument.KycStatus status);

    long countByStatus(KycDocument.KycStatus status);
}
