package com.civileng.marketplace.auditservice.repository;

import com.civileng.marketplace.auditservice.model.ErasureRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ErasureRequestRepository extends JpaRepository<ErasureRequest, Long> {

    List<ErasureRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<ErasureRequest> findByStatus(String status, Pageable pageable);
}
