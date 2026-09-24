package com.civileng.marketplace.procurement.repository;

import com.civileng.marketplace.procurement.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {

    List<SupplierInvoice> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);
}
