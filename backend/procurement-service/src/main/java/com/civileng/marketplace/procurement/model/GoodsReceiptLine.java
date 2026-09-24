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

@Entity
@Table(name = "goods_receipt_lines")
@Getter
@Setter
@NoArgsConstructor
public class GoodsReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id")
    private GoodsReceipt goodsReceipt;

    @Column(name = "po_line_id", nullable = false)
    private Long poLineId;

    @Column(name = "received_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal receivedQty;

    @Column(name = "rejected_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal rejectedQty;

    public BigDecimal acceptedQty() {
        return receivedQty.subtract(rejectedQty);
    }
}
