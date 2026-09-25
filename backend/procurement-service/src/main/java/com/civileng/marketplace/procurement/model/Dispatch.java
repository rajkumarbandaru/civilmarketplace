package com.civileng.marketplace.procurement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** A consignment the supplier sent against an order: which quantities, on which vehicle, under which e-way bill. */
@Entity
@Table(name = "dispatches")
@Getter
@Setter
@NoArgsConstructor
public class Dispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String number;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "vehicle_number", nullable = false, length = 20)
    private String vehicleNumber;

    @Column(length = 120)
    private String transporter;

    @Column(name = "eway_bill_number", length = 12)
    private String ewayBillNumber;

    @Column(name = "consignment_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal consignmentValue;

    @Column(name = "dispatched_by", nullable = false)
    private Long dispatchedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "dispatch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DispatchLine> lines = new ArrayList<>();

    public void addLine(DispatchLine line) {
        line.setDispatch(this);
        lines.add(line);
    }
}
