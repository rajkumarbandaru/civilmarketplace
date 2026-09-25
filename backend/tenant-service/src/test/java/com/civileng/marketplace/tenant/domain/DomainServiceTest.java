package com.civileng.marketplace.tenant.domain;

import com.civileng.marketplace.tenant.domain.TenantDomain.Status;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DomainServiceTest {

    private final TenantDomainRepository repo = mock(TenantDomainRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final DnsLookup dns = mock(DnsLookup.class);
    private final AcmeIssuer issuer = mock(AcmeIssuer.class);
    private final CertificateStore certs = mock(CertificateStore.class);
    private final Map<Long, TenantDomain> rows = new HashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private Instant now = Instant.parse("2026-09-25T10:00:00Z");
    private DomainService service;

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(i -> now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tenants.findByTenantKey("acme")).thenReturn(Optional.of(Tenant.builder().tenantKey("acme").status(TenantStatus.ACTIVE).build()));
        when(repo.save(any())).thenAnswer(i -> {
            TenantDomain d = i.getArgument(0);
            if (d.getId() == null) {
                d.setId(ids.incrementAndGet());
            }
            rows.put(d.getId(), d);
            return d;
        });
        when(repo.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(rows.get(i.<Long>getArgument(0))));
        when(repo.findByStatusIn(anyCollection())).thenAnswer(i -> rows.values().stream()
                .filter(d -> i.<Collection<?>>getArgument(0).contains(d.getStatus())).toList());
        when(repo.existsByHostAndStatusNot(anyString(), any())).thenAnswer(i -> rows.values().stream()
                .anyMatch(d -> d.getHost().equals(i.getArgument(0)) && d.getStatus() != i.getArgument(1)));
        when(repo.findFirstByHostAndStatusIn(anyString(), anyCollection())).thenAnswer(i -> rows.values().stream()
                .filter(d -> d.getHost().equals(i.getArgument(0)) && i.<Collection<?>>getArgument(1).contains(d.getStatus())).findFirst());
        DomainProperties props = new DomainProperties("acme://pebble/pebble:14000", null, "challtestsrv:8053",
                "edge.civilengineer.com", List.of("civilengineer.com", "localhost"), "/tmp/certs", Duration.ofHours(72),
                Duration.ofDays(1), Duration.ofDays(30), Duration.ofDays(7));
        service = new DomainService(repo, tenants, dns, issuer, certs, props, new TransactionTemplate(txm), clock);
    }

    private TenantDomain only() {
        return rows.values().iterator().next();
    }

    private AcmeIssuer.Issued issued(Instant notAfter) {
        X509Certificate c = mock(X509Certificate.class);
        when(c.getSerialNumber()).thenReturn(BigInteger.valueOf(0xBEEF));
        when(c.getIssuerX500Principal()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Pebble Intermediate CA"));
        when(c.getNotAfter()).thenReturn(Date.from(notAfter));
        return new AcmeIssuer.Issued("-----CHAIN-----", "-----KEY-----", c);
    }

    @Test
    void hostsAreNormalisedAndPlatformZonesCannotBeClaimed() {
        assertThat(service.normalise(" WWW.Acme-Builders.Test. ")).isEqualTo("www.acme-builders.test");
        assertThat(service.normalise("bücher.example")).isEqualTo("xn--bcher-kva.example");
        for (String bad : List.of("", "localhost", "acme.localhost", "app.civilengineer.com", "no_underscores.test",
                "-bad.test", "single")) {
            assertThatThrownBy(() -> service.normalise(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void aDomainIsServedOnlyAfterItsOwnerProvesItAndACertificateIsIssued() throws Exception {
        DomainService.DomainView v = service.add("acme", "www.acme-builders.test", null, "1");
        assertThat(v.status()).isEqualTo(Status.PENDING_VERIFICATION);
        assertThat(v.records()).extracting(DomainService.DnsRecord::type, DomainService.DnsRecord::name)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("TXT", "_platform-verify.www.acme-builders.test"),
                        org.assertj.core.groups.Tuple.tuple("CNAME", "www.acme-builders.test"));
        String token = v.records().get(0).value();
        assertThatThrownBy(() -> service.add("acme", "www.acme-builders.test", null, "1")).hasMessageContaining("already claimed");

        service.tick();
        assertThat(only().getStatus()).isEqualTo(Status.PENDING_VERIFICATION);
        assertThat(service.tenantFor("www.acme-builders.test")).isEmpty();

        when(dns.txt("_platform-verify.www.acme-builders.test")).thenReturn(List.of("someone-else", token));
        now = now.plusSeconds(30);
        AcmeIssuer.Issued cert = issued(Instant.parse("2026-12-24T10:00:00Z"));
        when(issuer.issue("www.acme-builders.test")).thenReturn(cert);
        service.tick();   // → VERIFIED
        assertThat(only().getStatus()).isEqualTo(Status.VERIFIED);
        service.tick();   // → CERT_ISSUING → ACTIVE
        assertThat(only().getStatus()).isEqualTo(Status.ACTIVE);
        verify(certs).write("www.acme-builders.test", "-----CHAIN-----", "-----KEY-----");
        assertThat(only().getCertIssuer()).contains("Pebble");
        assertThat(service.tenantFor("WWW.acme-builders.test:443")).contains("acme");
    }

    @Test
    void aServingDomainWhoseProofDisappearsDegradesThenIsUnboundAfterTheGrace() throws Exception {
        service.add("acme", "shop.acme.test", null, "1");
        TenantDomain d = only();
        d.setStatus(Status.ACTIVE);
        d.setCertNotAfter(LocalDateTime.parse("2027-01-01T00:00"));
        d.setLastCheckedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        service.tick();
        assertThat(d.getStatus()).isEqualTo(Status.ACTIVE);   // not due for a re-check yet

        now = now.plus(Duration.ofDays(1)).plusSeconds(1);
        service.tick();
        assertThat(d.getStatus()).isEqualTo(Status.DEGRADED);
        assertThat(service.tenantFor("shop.acme.test")).contains("acme");   // still serving in its grace

        now = now.plus(Duration.ofDays(8));
        service.tick();
        assertThat(d.getStatus()).isEqualTo(Status.FAILED);
        assertThat(service.tenantFor("shop.acme.test")).isEmpty();
        verify(certs).remove("shop.acme.test");
    }

    @Test
    void aDegradedDomainRecoversWhenTheRecordReturns() {
        service.add("acme", "shop.acme.test", null, "1");
        TenantDomain d = only();
        d.setStatus(Status.DEGRADED);
        d.setDegradedSince(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        when(dns.txt("_platform-verify.shop.acme.test")).thenReturn(List.of(d.getVerificationToken()));
        service.tick();
        assertThat(d.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(d.getDegradedSince()).isNull();
    }

    @Test
    void certificatesAreRenewedAMonthBeforeTheyExpire() throws Exception {
        service.add("acme", "shop.acme.test", null, "1");
        TenantDomain d = only();
        d.setStatus(Status.ACTIVE);
        d.setLastCheckedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        d.setCertNotAfter(LocalDateTime.ofInstant(now.plus(Duration.ofDays(20)), ZoneOffset.UTC));
        AcmeIssuer.Issued cert = issued(Instant.parse("2027-01-01T00:00:00Z"));
        when(issuer.issue("shop.acme.test")).thenReturn(cert);
        service.tick();
        assertThat(d.getCertNotAfter()).isEqualTo(LocalDateTime.parse("2027-01-01T00:00"));
        assertThat(d.getStatus()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void failedIssuanceRetriesWithBackoffThenFails() throws Exception {
        service.add("acme", "shop.acme.test", null, "1");
        TenantDomain d = only();
        d.setStatus(Status.VERIFIED);
        when(issuer.issue(anyString())).thenThrow(new RuntimeException("connection refused"));
        for (int i = 0; i < 5; i++) {
            now = now.plus(Duration.ofMinutes(11));
            service.tick();
        }
        assertThat(d.getStatus()).isEqualTo(Status.FAILED);
        assertThat(d.getLastError()).contains("connection refused");
        assertThat(DomainService.backoff(0)).isEqualTo(Duration.ofSeconds(10));
        assertThat(DomainService.backoff(20)).isEqualTo(Duration.ofMinutes(10));

        service.checkNow("acme", d.getId());   // an operator retries a failed domain
        assertThat(d.getStatus()).isIn(Status.PENDING_VERIFICATION, Status.VERIFIED);
    }

    @Test
    void aRemovedDomainStopsServingAndItsCertificateIsDeleted() throws Exception {
        service.add("acme", "shop.acme.test", null, "1");
        TenantDomain d = only();
        d.setStatus(Status.ACTIVE);
        service.remove("acme", d.getId(), "1");
        assertThat(d.getStatus()).isEqualTo(Status.REMOVED);
        verify(certs).remove("shop.acme.test");
        assertThat(service.tenantFor("shop.acme.test")).isEmpty();
        assertThatThrownBy(() -> service.remove("other", d.getId(), "1")).isInstanceOf(NoSuchElementException.class);
    }
}
