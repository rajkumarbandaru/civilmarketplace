package com.civileng.marketplace.tenant.common;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TenantPlacementsTest {

    private static final String URL = "jdbc:mysql://mysql:3306/civil_engineer_users?useSSL=false&serverTimezone=UTC";

    @Test
    void theServiceUrlIsPointedAtAnotherClusterKeepingItsOptions() {
        assertThat(ClusterDataSources.urlFor(URL, "mysql-b", 3307))
                .isEqualTo("jdbc:mysql://mysql-b:3307/civil_engineer_users?useSSL=false&serverTimezone=UTC");
        assertThat(ClusterDataSources.urlFor("jdbc:mysql://mysql/db", "mysql-b", 3306)).isEqualTo("jdbc:mysql://mysql-b:3306/db");
    }

    @Test
    void theClusterInTheServiceUrlIsTheServicesOwnPool() {
        DataSource primary = mock(DataSource.class);
        try (ClusterDataSources clusters = new ClusterDataSources(primary, URL, "u", "p", 2)) {
            assertThat(clusters.forCluster("mysql", 3306)).isSameAs(primary);
            assertThat(clusters.forCluster("MYSQL", 3306)).isSameAs(primary);
            assertThat(clusters.forCluster(null, 0)).isSameAs(primary);
            DataSource b = clusters.forCluster("mysql-b", 3306);
            assertThat(b).isNotSameAs(primary).isSameAs(clusters.forCluster("mysql-b", 3306));
        }
    }

    @Test
    void aTenantIsRoutedToItsClusterAndAMoveIsAcknowledgedOnce() {
        DataSource primary = mock(DataSource.class);
        DataSource clusterB = mock(DataSource.class);
        ClusterDataSources clusters = mock(ClusterDataSources.class);
        when(clusters.primary()).thenReturn(primary);
        when(clusters.forCluster("mysql", 3306)).thenReturn(primary);
        when(clusters.forCluster("mysql-b", 3306)).thenReturn(clusterB);
        TenantRegistry registry = mock(TenantRegistry.class);
        when(registry.placements()).thenReturn(Map.of(
                "acme", new TenantPlacement("acme", "cluster-a", "mysql", 3306, 0, "ACTIVE")));
        List<TenantPlacement> acks = new ArrayList<>();

        TenantPlacements placements = new TenantPlacements(registry, clusters, acks::add, 0);
        assertThat(placements.dataSourceFor("acme")).isSameAs(primary);
        assertThat(placements.dataSourceFor("unplaced")).isSameAs(primary);

        when(registry.placements()).thenReturn(Map.of(
                "acme", new TenantPlacement("acme", "cluster-b", "mysql-b", 3306, 1, "ACTIVE")));
        placements.refresh();
        placements.refresh();   // re-reading the same epoch is not another move
        assertThat(placements.dataSourceFor("acme")).isSameAs(clusterB);
        assertThat(acks).extracting(TenantPlacement::clusterId, TenantPlacement::epoch)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("cluster-b", 1L));

        when(registry.placements()).thenReturn(Map.of());   // map unreadable: keep routing as before
        placements.refresh();
        assertThat(placements.dataSourceFor("acme")).isSameAs(clusterB);
    }

    @Test
    void hibernateConnectionsForATenantComeFromItsClustersPool() throws Exception {
        DataSource primary = mock(DataSource.class);
        DataSource clusterB = mock(DataSource.class);
        Connection c = mock(Connection.class);
        when(clusterB.getConnection()).thenReturn(c);
        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(primary,
                new TenantSchemas("civil_engineer_users"), "civil_engineer_users", t -> t.equals("acme") ? clusterB : primary);
        assertThat(provider.getConnection("acme")).isSameAs(c);
        verify(c).setCatalog("civil_engineer_users_acme");
        verify(primary, never()).getConnection();
        provider.releaseConnection("acme", c);
        verify(c).setCatalog("civil_engineer_users");
    }

    @Test
    void aPlacementEventReReadsTheMapAndProvisionsNothing() {
        TenantPlacements placements = mock(TenantPlacements.class);
        TenantSchemaMigrator migrator = mock(TenantSchemaMigrator.class);
        TenantProvisioningAcks acks = mock(TenantProvisioningAcks.class);
        TenantProvisioningListener listener = new TenantProvisioningListener(migrator, List.of(), acks, () -> placements);
        listener.onTenantEvent(TenantEventMessage.builder().tenantKey("acme").status("MAINTENANCE").placementChanged(true).build());
        verify(placements).refresh();
        verifyNoInteractions(migrator, acks);
        // A tenant paused for maintenance is still a live one: storage stays provisioned.
        listener.onTenantEvent(TenantEventMessage.builder().tenantKey("acme").status("MAINTENANCE").build());
        verify(migrator).migrate("acme");
        verify(acks).send(eq("acme"), eq(true), any());
    }

    @Test
    void pausingWritesIsReportedAndRefusedButReadsGoOn() throws Exception {
        ClusterDataSources clusters = mock(ClusterDataSources.class);
        TenantRegistry registry = mock(TenantRegistry.class);
        when(registry.placements()).thenReturn(Map.of("acme", new TenantPlacement("acme", "cluster-a", "mysql", 3306, 0, "ACTIVE")));
        List<TenantPlacement> acks = new ArrayList<>();
        TenantPlacements placements = new TenantPlacements(registry, clusters, acks::add, 0);
        when(registry.placements()).thenReturn(Map.of("acme", new TenantPlacement("acme", "cluster-a", "mysql", 3306, 0, "MAINTENANCE")));
        placements.refresh();
        assertThat(acks).extracting(TenantPlacement::status).containsExactly("MAINTENANCE");
        assertThat(placements.inMaintenance("acme")).isTrue();

        TenantHeaderFilter filter = new TenantHeaderFilter(new TenantProperties(), () -> placements);
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        org.springframework.mock.web.MockHttpServletRequest write = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/bookings");
        write.addHeader("X-Tenant-Id", "acme");
        org.springframework.mock.web.MockHttpServletResponse refused = new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(write, refused, chain);
        assertThat(refused.getStatus()).isEqualTo(503);
        assertThat(refused.getHeader("Retry-After")).isEqualTo("5");
        assertThat(refused.getContentAsString()).contains("TENANT_MAINTENANCE");
        verifyNoInteractions(chain);

        org.springframework.mock.web.MockHttpServletRequest read = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/bookings");
        read.addHeader("X-Tenant-Id", "acme");
        org.springframework.mock.web.MockHttpServletResponse served = new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(read, served, chain);
        assertThat(served.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static String eq(String s) {
        return org.mockito.ArgumentMatchers.eq(s);
    }

    private static boolean eq(boolean b) {
        return org.mockito.ArgumentMatchers.eq(b);
    }
}
