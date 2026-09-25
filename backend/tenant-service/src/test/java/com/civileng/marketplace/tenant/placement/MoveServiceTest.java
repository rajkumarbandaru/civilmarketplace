package com.civileng.marketplace.tenant.placement;

import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.model.Vertical;
import com.civileng.marketplace.tenant.placement.TenantMove.Step;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import com.civileng.marketplace.tenant.repository.TenantStatusChangeRepository;
import com.civileng.marketplace.tenant.service.TenantLifecycle;
import com.civileng.marketplace.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MoveServiceTest {

    private static final Map<String, String> PREFIXES = Map.of("civil_engineer_users", "user-service",
            "civil_engineer_bookings", "booking-service");
    private static final SchemaCopier.Endpoint A = new SchemaCopier.Endpoint("mysql", 3306);
    private static final SchemaCopier.Endpoint B = new SchemaCopier.Endpoint("mysql-b", 3306);
    private static final List<String> SCHEMAS = List.of("civil_engineer_bookings_acme", "civil_engineer_users_acme");

    private final TenantMoveRepository moves = mock(TenantMoveRepository.class);
    private final TenantMoveAckRepository acks = mock(TenantMoveAckRepository.class);
    private final DbClusterRepository clusters = mock(DbClusterRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantService tenantService = mock(TenantService.class);
    private final SchemaCopier copier = mock(SchemaCopier.class);
    private final Map<Long, TenantMove> moveRows = new HashMap<>();
    private final Map<TenantMoveAck.Key, TenantMoveAck> ackRows = new HashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private Instant now = Instant.parse("2026-09-25T10:00:00Z");
    private MoveService service;
    private Tenant acme;

    private static DbCluster cluster(String id, String host, String kind) {
        DbCluster c = new DbCluster();
        c.setClusterId(id);
        c.setCell("cell-1");
        c.setHost(host);
        c.setPort(3306);
        c.setKind(kind);
        c.setStatus("ACTIVE");
        c.setCapacity(500);
        return c;
    }

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(i -> now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        acme = Tenant.builder().tenantKey("acme").name("Acme").subdomain("acme").status(TenantStatus.ACTIVE)
                .vertical(Vertical.CIVIL_MARKETPLACE).enabledModules("auth").contactEmail("ops@acme.in").plan("professional").build();
        when(tenants.findByTenantKey("acme")).thenReturn(Optional.of(acme));
        when(tenants.findAll()).thenAnswer(i -> List.of(acme));
        Map<String, DbCluster> cs = Map.of("cluster-a", cluster("cluster-a", "mysql", "SHARED"),
                "cluster-b", cluster("cluster-b", "mysql-b", "SHARED"), "cluster-d", cluster("cluster-d", "mysql-d", "DEDICATED"));
        when(clusters.findById(anyString())).thenAnswer(i -> Optional.ofNullable(cs.get(i.<String>getArgument(0))));

        when(moves.save(any())).thenAnswer(i -> {
            TenantMove m = i.getArgument(0);
            if (m.getId() == null) m.setId(ids.incrementAndGet());
            moveRows.put(m.getId(), m);
            return m;
        });
        when(moves.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(moveRows.get(i.<Long>getArgument(0))));
        when(moves.findByStepIn(anyCollection())).thenAnswer(i -> moveRows.values().stream()
                .filter(m -> i.<Collection<?>>getArgument(0).contains(m.getStep())).toList());
        when(moves.findByTenantKeyOrderByIdDesc("acme")).thenAnswer(i -> moveRows.values().stream()
                .sorted(Comparator.comparing(TenantMove::getId).reversed()).toList());
        when(acks.save(any())).thenAnswer(i -> {
            TenantMoveAck a = i.getArgument(0);
            ackRows.put(a.getId(), a);
            return a;
        });
        when(acks.findByIdMoveId(anyLong())).thenAnswer(i -> ackRows.values().stream()
                .filter(a -> a.getId().getMoveId().equals(i.getArgument(0))).toList());
        doAnswer(i -> { ((Collection<TenantMoveAck>) i.getArgument(0)).forEach(a -> ackRows.remove(a.getId())); return null; })
                .when(acks).deleteAll(anyCollection());

        when(copier.schemasOf(A, "acme")).thenReturn(SCHEMAS);
        when(copier.copySchema(eq(A), eq(B), anyString())).thenReturn(new SchemaCopier.CopyStats(3, 120));
        when(copier.checksums(any(), anyString())).thenReturn(Map.of("bookings", 11L, "flyway_schema_history", 7L));

        service = new MoveService(moves, acks, clusters, tenants, new TenantLifecycle(mock(TenantStatusChangeRepository.class)),
                tenantService, copier, new PlacementProperties(PREFIXES, Duration.ofSeconds(3), Duration.ofMinutes(2), 500, null, "u", "p"),
                new TransactionTemplate(txm), clock);
    }

    private void ack(String svc, String cluster, long epoch, String status) {
        service.onAck("{\"tenantKey\":\"acme\",\"service\":\"" + svc + "\",\"clusterId\":\"" + cluster + "\",\"epoch\":" + epoch
                + ",\"status\":\"" + status + "\",\"at\":0}");
    }

    private TenantMove move() {
        return moveRows.values().iterator().next();
    }

    @Test
    void aTenantMovesWithWritesPausedOnlyBetweenTheFreezeAndEveryServiceRoutingToTheNewCluster() throws Exception {
        service.start("acme", "cluster-b", "1");
        service.tick();   // COPY while live
        assertThat(move().getStep()).isEqualTo(Step.FREEZE);
        assertThat(move().getRowsCopied()).isEqualTo(240);
        assertThat(acme.getStatus()).isEqualTo(TenantStatus.MAINTENANCE);
        verify(tenantService).announce(acme, false);

        service.tick();   // nobody has confirmed the pause yet
        assertThat(move().getStep()).isEqualTo(Step.FREEZE);
        assertThat(service.history("acme").get(0).waitingFor()).containsExactly("booking-service", "user-service");
        ack("user-service", "cluster-a", 0, "MAINTENANCE");
        ack("booking-service", "cluster-a", 0, "ACTIVE");   // not yet paused: does not count
        service.tick();
        assertThat(move().getStep()).isEqualTo(Step.FREEZE);
        ack("booking-service", "cluster-a", 0, "MAINTENANCE");
        service.tick();   // confirmed, but the drain has not passed
        assertThat(move().getStep()).isEqualTo(Step.FREEZE);
        now = now.plusSeconds(4);
        service.tick();
        assertThat(move().getStep()).isEqualTo(Step.SYNC);

        // bookings changed between the bulk copy and the pause
        when(copier.checksums(A, "civil_engineer_bookings_acme")).thenReturn(Map.of("bookings", 12L, "flyway_schema_history", 7L));
        when(copier.checksums(B, "civil_engineer_bookings_acme")).thenReturn(Map.of("bookings", 11L, "flyway_schema_history", 7L))
                .thenReturn(Map.of("bookings", 12L, "flyway_schema_history", 7L));
        service.tick();
        verify(copier).copyTables(A, B, "civil_engineer_bookings_acme", List.of("bookings"));
        assertThat(move().getTablesResynced()).isEqualTo(1);
        service.tick();   // VERIFY
        assertThat(move().getStep()).isEqualTo(Step.FLIP);
        service.tick();
        assertThat(acme.getDbClusterId()).isEqualTo("cluster-b");
        assertThat(acme.getPlacementEpoch()).isEqualTo(1);
        verify(tenantService).announcePlacement(acme);
        assertThat(move().getStep()).isEqualTo(Step.AWAIT_ACKS);

        ack("user-service", "cluster-b", 1, "MAINTENANCE");
        ack("booking-service", "cluster-a", 0, "MAINTENANCE");   // stale epoch: ignored
        service.tick();
        assertThat(move().getStep()).isEqualTo(Step.AWAIT_ACKS);
        assertThat(acme.getStatus()).isEqualTo(TenantStatus.MAINTENANCE);
        ack("booking-service", "cluster-b", 1, "MAINTENANCE");
        service.tick();
        service.tick();   // RESUME
        assertThat(move().getStep()).isEqualTo(Step.DONE);
        assertThat(acme.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        MoveService.MoveView view = service.history("acme").get(0);
        assertThat(view.freezeMillis()).isEqualTo(4000);
        assertThat(view.report()).containsEntry("tablesVerified", 4);
        verify(copier, never()).dropSchema(any(), anyString());
    }

    @Test
    void aChecksumMismatchAbandonsTheMoveAndResumesOnTheSource() throws Exception {
        service.start("acme", "cluster-b", "1");
        service.tick();
        ack("user-service", "cluster-a", 0, "MAINTENANCE");
        ack("booking-service", "cluster-a", 0, "MAINTENANCE");
        now = now.plusSeconds(4);
        service.tick();   // → SYNC
        service.tick();   // SYNC: nothing to re-copy
        when(copier.checksums(B, "civil_engineer_users_acme")).thenReturn(Map.of("bookings", 99L, "flyway_schema_history", 7L));
        service.tick();   // VERIFY fails
        assertThat(move().getStep()).isEqualTo(Step.FAILED);
        assertThat(move().getLastError()).contains("civil_engineer_users_acme.bookings");
        assertThat(acme.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(acme.getDbClusterId()).isEqualTo("cluster-a");
        verify(tenantService, never()).announcePlacement(any());
    }

    @Test
    void servicesThatDoNotPauseWritesInTimeAbandonTheMove() {
        service.start("acme", "cluster-b", "1");
        service.tick();
        now = now.plus(Duration.ofMinutes(3));
        service.tick();
        assertThat(move().getStep()).isEqualTo(Step.FAILED);
        assertThat(move().getLastError()).contains("booking-service, user-service");
        assertThat(acme.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void afterTheFlipAFailureKeepsWritesPausedUntilAnOperatorRollsBack() {
        service.start("acme", "cluster-b", "1");
        service.tick();
        ack("user-service", "cluster-a", 0, "MAINTENANCE");
        ack("booking-service", "cluster-a", 0, "MAINTENANCE");
        now = now.plusSeconds(4);
        service.tick();
        service.tick();
        service.tick();
        service.tick();   // FLIP
        ack("user-service", "cluster-b", 1, "MAINTENANCE");
        now = now.plus(Duration.ofMinutes(5));
        service.tick();   // booking-service never routed
        assertThat(move().getStep()).isEqualTo(Step.FAILED);
        assertThat(acme.getStatus()).isEqualTo(TenantStatus.MAINTENANCE);   // not split across clusters

        assertThatThrownBy(() -> service.dropSource("acme", move().getId(), "1")).isInstanceOf(IllegalStateException.class);
        service.rollback("acme", move().getId(), "1");
        assertThat(acme.getDbClusterId()).isEqualTo("cluster-a");
        assertThat(acme.getPlacementEpoch()).isEqualTo(2);
        assertThat(acme.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(move().getStep()).isEqualTo(Step.ROLLED_BACK);
        verify(tenantService, times(2)).announcePlacement(acme);
    }

    @Test
    void theSourceCopyIsDroppedOnlyAfterASuccessfulMove() throws Exception {
        service.start("acme", "cluster-b", "1");
        TenantMove m = move();
        m.setStep(Step.DONE);
        acme.setDbClusterId("cluster-b");
        service.dropSource("acme", m.getId(), "1");
        verify(copier).dropSchema(A, "civil_engineer_bookings_acme");
        verify(copier).dropSchema(A, "civil_engineer_users_acme");
        assertThat(m.getSourceDroppedAt()).isNotNull();
        assertThatThrownBy(() -> service.dropSource("acme", m.getId(), "1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void movesAreRefusedWhereTheyCannotWork() {
        assertThatThrownBy(() -> service.start("acme", "cluster-a", "1")).hasMessageContaining("already on cluster-a");
        assertThatThrownBy(() -> service.start("acme", "cluster-z", "1")).isInstanceOf(NoSuchElementException.class);
        Tenant platform = Tenant.builder().tenantKey("platform").status(TenantStatus.ACTIVE).build();
        when(tenants.findByTenantKey("platform")).thenReturn(Optional.of(platform));
        assertThatThrownBy(() -> service.start("platform", "cluster-b", "1")).hasMessageContaining("operator tenant");
        acme.setStatus(TenantStatus.SUSPENDED);
        assertThatThrownBy(() -> service.start("acme", "cluster-b", "1")).hasMessageContaining("Only an ACTIVE tenant");
        acme.setStatus(TenantStatus.ACTIVE);
        service.start("acme", "cluster-b", "1");
        assertThatThrownBy(() -> service.start("acme", "cluster-b", "1")).hasMessageContaining("already being moved");
    }

    @Test
    void aDedicatedClusterTakesOneTenantAndMakesItT2() {
        Tenant other = Tenant.builder().tenantKey("bigco").status(TenantStatus.ACTIVE).dbClusterId("cluster-d").build();
        when(tenants.findAll()).thenAnswer(i -> List.of(acme, other));
        assertThatThrownBy(() -> service.start("acme", "cluster-d", "1")).hasMessageContaining("dedicated");
        when(tenants.findAll()).thenAnswer(i -> List.of(acme));
        service.start("acme", "cluster-d", "1");
        TenantMove m = move();
        m.setStep(Step.FLIP);
        service.tick();
        assertThat(acme.getTier()).isEqualTo("DEDICATED_DB");
    }

    @Test
    void identifiersReachSqlOnlyWhenPlain() {
        assertThat(SchemaCopier.safe("civil_engineer_users_acme")).isEqualTo("civil_engineer_users_acme");
        assertThatThrownBy(() -> SchemaCopier.safe("users`; DROP DATABASE x; --")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaCopier.safe("a".repeat(65))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void columnsWithAnExpressionDefaultAreCopiedOnlyComputedOnesAreNot() {
        assertThat(SchemaCopier.insertable("DEFAULT_GENERATED")).isTrue();                 // DEFAULT CURRENT_TIMESTAMP
        assertThat(SchemaCopier.insertable("DEFAULT_GENERATED on update CURRENT_TIMESTAMP")).isTrue();
        assertThat(SchemaCopier.insertable("auto_increment")).isTrue();
        assertThat(SchemaCopier.insertable(null)).isTrue();
        assertThat(SchemaCopier.insertable("VIRTUAL GENERATED")).isFalse();
        assertThat(SchemaCopier.insertable("STORED GENERATED")).isFalse();
    }

    @Test
    void anOperatorCanPauseAndResumeWritesButNotDuringAMove() {
        service.maintenance("acme", true, "Restoring bookings", "1");
        assertThat(acme.getStatus()).isEqualTo(TenantStatus.MAINTENANCE);
        service.maintenance("acme", true, null, "1");   // idempotent
        service.maintenance("acme", false, null, "1");
        assertThat(acme.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(tenantService, times(2)).announce(acme, false);
        service.start("acme", "cluster-b", "1");
        assertThatThrownBy(() -> service.maintenance("acme", true, null, "1")).hasMessageContaining("being moved");
        acme.setStatus(TenantStatus.SUSPENDED);
        moveRows.clear();
        assertThatThrownBy(() -> service.maintenance("acme", true, null, "1")).hasMessageContaining("SUSPENDED");
    }
}
