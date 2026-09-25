package com.civileng.marketplace.procurement.repository;

import com.civileng.marketplace.procurement.model.Dispatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispatchRepository extends JpaRepository<Dispatch, Long> {

    List<Dispatch> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);
}
