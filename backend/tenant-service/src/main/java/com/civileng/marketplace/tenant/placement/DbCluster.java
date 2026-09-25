package com.civileng.marketplace.tenant.placement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A MySQL cluster tenants' schemas can be placed on. */
@Entity
@Table(name = "db_clusters")
@Getter
@Setter
@NoArgsConstructor
public class DbCluster {

    @Id
    @Column(name = "cluster_id", length = 40)
    private String clusterId;

    @Column(nullable = false, length = 40)
    private String cell;

    @Column(nullable = false, length = 120)
    private String host;

    @Column(nullable = false)
    private int port;

    /** SHARED or DEDICATED (one tenant, tier T2). */
    @Column(nullable = false, length = 20)
    private String kind;

    /** ACTIVE, DRAINING or RETIRED. */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int capacity;

    public boolean dedicated() {
        return "DEDICATED".equals(kind);
    }
}
