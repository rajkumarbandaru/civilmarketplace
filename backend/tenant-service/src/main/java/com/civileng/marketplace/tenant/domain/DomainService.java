package com.civileng.marketplace.tenant.domain;

import com.civileng.marketplace.tenant.domain.TenantDomain.Status;
import com.civileng.marketplace.tenant.domain.TenantDomain.Surface;
import com.civileng.marketplace.tenant.model.Tenant;
import com.civileng.marketplace.tenant.model.TenantStatus;
import com.civileng.marketplace.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.IDN;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The Domain Manager (architecture 06 §5, Diagram 16): a tenant adds its own host name, proves it
 * controls it with a DNS TXT record, points it at the edge, and is issued an ACME certificate; the
 * host then routes to the tenant. Serving domains are re-checked daily — one whose proof disappears
 * is DEGRADED, keeps serving through a grace period, then is unbound — and certificates are renewed
 * well before they expire.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DomainService {

    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");
    private static final Set<Status> WORKING = EnumSet.of(Status.PENDING_VERIFICATION, Status.VERIFIED,
            Status.CERT_ISSUING, Status.ACTIVE, Status.DEGRADED);
    private static final Set<Status> SERVING = EnumSet.of(Status.ACTIVE, Status.DEGRADED);
    private static final int MAX_ISSUE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantDomainRepository domains;
    private final TenantRepository tenants;
    private final DnsLookup dns;
    private final AcmeIssuer issuer;
    private final CertificateStore certificates;
    private final DomainProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public record DnsRecord(String type, String name, String value, String purpose) { }

    public record DomainView(Long id, String tenantKey, String host, Surface surface, Status status, List<DnsRecord> records,
                             String certIssuer, LocalDateTime certNotAfter, int attempts, String lastError,
                             LocalDateTime lastCheckedAt, LocalDateTime activatedAt, LocalDateTime createdAt) { }

    public List<DomainView> list(String tenantKey) {
        return domains.findByTenantKeyAndStatusNotOrderByIdAsc(tenantKey, Status.REMOVED).stream().map(this::view).toList();
    }

    public DomainView add(String tenantKey, String rawHost, Surface surface, String actor) {
        String host = normalise(rawHost);
        return tx.execute(s -> {
            Tenant t = tenants.findByTenantKey(tenantKey).orElseThrow(() -> new NoSuchElementException("No tenant " + tenantKey));
            if (t.getStatus() == TenantStatus.ARCHIVED) {
                throw new IllegalStateException("An archived tenant cannot take a domain");
            }
            if (domains.existsByHostAndStatusNot(host, Status.REMOVED)) {
                throw new IllegalStateException(host + " is already claimed");
            }
            TenantDomain d = new TenantDomain();
            d.setTenantKey(tenantKey);
            d.setHost(host);
            d.setSurface(surface == null ? Surface.WEB : surface);
            d.setStatus(Status.PENDING_VERIFICATION);
            d.setVerificationToken(token());
            d.setCreatedBy(actor == null ? "operator" : actor);
            domains.save(d);
            log.info("Domain {} added for '{}' by {}", host, tenantKey, actor);
            return view(domains.findById(d.getId()).orElseThrow());
        });
    }

    public DomainView remove(String tenantKey, Long id, String actor) throws Exception {
        TenantDomain d = tx.execute(s -> {
            TenantDomain found = owned(tenantKey, id);
            found.setStatus(Status.REMOVED);
            found.setRemovedAt(LocalDateTime.now(clock));
            return domains.save(found);
        });
        certificates.remove(d.getHost());
        log.info("Domain {} of '{}' removed by {}", d.getHost(), tenantKey, actor);
        return view(d);
    }

    /** Check now rather than at the next scheduled look. */
    public DomainView checkNow(String tenantKey, Long id) {
        TenantDomain d = tx.execute(s -> {
            TenantDomain found = owned(tenantKey, id);
            if (found.getStatus() == Status.FAILED) {
                found.setStatus(Status.PENDING_VERIFICATION);
                found.setAttempts(0);
                found.setLastError(null);
            }
            found.setLastCheckedAt(null);
            return domains.save(found);
        });
        advance(d.getId());
        return view(domains.findById(d.getId()).orElseThrow());
    }

    /** The tenant a serving custom host belongs to. */
    public Optional<String> tenantFor(String host) {
        return domains.findFirstByHostAndStatusIn(normaliseQuietly(host), SERVING).map(TenantDomain::getTenantKey);
    }

    @Scheduled(fixedDelayString = "${platform.domains.tick-ms:5000}", initialDelay = 15_000)
    public void tick() {
        for (TenantDomain d : domains.findByStatusIn(WORKING)) {
            try {
                advance(d.getId());
            } catch (RuntimeException e) {
                log.error("Domain {} step failed unexpectedly", d.getHost(), e);
            }
        }
    }

    void advance(Long id) {
        TenantDomain d = domains.findById(id).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
        switch (d.getStatus()) {
            case PENDING_VERIFICATION -> {
                if (!due(d, backoff(d.getAttempts()))) return;
                if (proven(d)) {
                    d.setStatus(Status.VERIFIED);
                    d.setAttempts(0);
                    d.setLastError(null);
                } else if (Duration.between(d.getCreatedAt() == null ? now : d.getCreatedAt(), now).compareTo(props.verifyTimeout()) > 0) {
                    d.setStatus(Status.FAILED);
                    d.setLastError("The TXT record was not found within " + props.verifyTimeout().toHours() + " hours");
                } else {
                    d.setAttempts(d.getAttempts() + 1);
                    d.setLastError("Waiting for TXT " + props.verificationName(d.getHost()));
                }
                d.setLastCheckedAt(now);
                save(d);
            }
            case VERIFIED -> {
                // Straight to issuance once proven; the backoff is only for retrying a failed attempt.
                if (d.getAttempts() > 0 && !due(d, backoff(d.getAttempts()))) return;
                d.setStatus(Status.CERT_ISSUING);
                save(d);
                issue(d.getId(), false);
            }
            case CERT_ISSUING -> issue(d.getId(), false);
            case ACTIVE, DEGRADED -> {
                if (d.getCertNotAfter() != null
                        && Duration.between(now, d.getCertNotAfter()).compareTo(props.renewBefore()) < 0) {
                    issue(d.getId(), true);
                    return;
                }
                if (!due(d, props.recheck())) return;
                recheck(d, now);
            }
            default -> { }
        }
    }

    private void recheck(TenantDomain d, LocalDateTime now) {
        d.setLastCheckedAt(now);
        if (proven(d)) {
            if (d.getStatus() == Status.DEGRADED) {
                log.info("Domain {} proven again; ACTIVE", d.getHost());
            }
            d.setStatus(Status.ACTIVE);
            d.setDegradedSince(null);
            d.setLastError(null);
        } else if (d.getStatus() == Status.ACTIVE) {
            d.setStatus(Status.DEGRADED);
            d.setDegradedSince(now);
            d.setLastError("The TXT record " + props.verificationName(d.getHost()) + " is gone; serving for "
                    + props.grace().toDays() + " more days");
        } else if (Duration.between(d.getDegradedSince(), now).compareTo(props.grace()) > 0) {
            d.setStatus(Status.FAILED);
            d.setLastError("Unbound: the TXT record stayed missing past the grace period");
            try {
                certificates.remove(d.getHost());
            } catch (Exception e) {
                log.warn("Could not remove the certificate of {}", d.getHost(), e);
            }
        }
        save(d);
    }

    /** Issues (or renews) the certificate. Outside any transaction: the ACME exchange takes seconds. */
    private void issue(Long id, boolean renewal) {
        TenantDomain d = domains.findById(id).orElseThrow();
        try {
            AcmeIssuer.Issued issued = issuer.issue(d.getHost());
            certificates.write(d.getHost(), issued.chainPem(), issued.keyPem());
            d.setCertSerial(issued.certificate().getSerialNumber().toString(16));
            d.setCertIssuer(issued.certificate().getIssuerX500Principal().getName());
            d.setCertNotAfter(LocalDateTime.ofInstant(issued.certificate().getNotAfter().toInstant(), ZoneOffset.UTC));
            if (!renewal) {
                d.setStatus(Status.ACTIVE);
                d.setActivatedAt(LocalDateTime.now(clock));
            }
            d.setAttempts(0);
            d.setLastError(null);
            d.setLastCheckedAt(LocalDateTime.now(clock));
            log.info("Certificate for {} {} (serial {}, until {})", d.getHost(), renewal ? "renewed" : "issued",
                    d.getCertSerial(), d.getCertNotAfter());
        } catch (Exception e) {
            d.setAttempts(d.getAttempts() + 1);
            d.setLastError((renewal ? "Renewal" : "Issuance") + " failed: " + e.getMessage());
            d.setLastCheckedAt(LocalDateTime.now(clock));
            if (!renewal) {
                d.setStatus(d.getAttempts() >= MAX_ISSUE_ATTEMPTS ? Status.FAILED : Status.VERIFIED);
            }
            log.warn("Certificate for {} not obtained (attempt {}): {}", d.getHost(), d.getAttempts(), e.getMessage());
        }
        save(d);
    }

    private boolean proven(TenantDomain d) {
        return dns.txt(props.verificationName(d.getHost())).contains(d.getVerificationToken());
    }

    private boolean due(TenantDomain d, Duration interval) {
        return d.getLastCheckedAt() == null
                || Duration.between(d.getLastCheckedAt(), LocalDateTime.now(clock)).compareTo(interval) >= 0;
    }

    /** 10 s, doubling, capped at 10 minutes. */
    static Duration backoff(int attempts) {
        return Duration.ofSeconds(Math.min(600, 10L << Math.min(attempts, 6)));
    }

    private void save(TenantDomain d) {
        tx.executeWithoutResult(s -> domains.save(d));
    }

    private TenantDomain owned(String tenantKey, Long id) {
        return domains.findById(id).filter(x -> x.getTenantKey().equals(tenantKey) && x.getStatus() != Status.REMOVED)
                .orElseThrow(() -> new NoSuchElementException("No such domain"));
    }

    /**
     * Lower case, no trailing dot, IDNA (ASCII) form, valid labels, at least two, and not one of
     * the platform's own zones — a tenant cannot claim a subdomain of the platform this way.
     */
    String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Enter a host name, e.g. www.example.com");
        }
        String host;
        try {
            host = IDN.toASCII(raw.trim().toLowerCase().replaceAll("\\.$", ""), IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a valid host name");
        }
        String[] labels = host.split("\\.");
        if (host.length() > 253 || labels.length < 2 || Arrays.stream(labels).anyMatch(l -> !LABEL.matcher(l).matches())) {
            throw new IllegalArgumentException("'" + raw + "' is not a valid host name");
        }
        for (String platform : props.platformDomains()) {
            if (host.equals(platform) || host.endsWith("." + platform)) {
                throw new IllegalArgumentException(host + " belongs to the platform; tenants get subdomains of it automatically");
            }
        }
        return host;
    }

    private String normaliseQuietly(String host) {
        String h = host == null ? "" : host.toLowerCase().split(":")[0];
        return h.endsWith(".") ? h.substring(0, h.length() - 1) : h;
    }

    private static String token() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private DomainView view(TenantDomain d) {
        List<DnsRecord> records = List.of(
                new DnsRecord("TXT", props.verificationName(d.getHost()), d.getVerificationToken(), "Proves you control the name"),
                new DnsRecord("CNAME", d.getHost(), props.edgeTarget(), "Sends its traffic to the platform"));
        return new DomainView(d.getId(), d.getTenantKey(), d.getHost(), d.getSurface(), d.getStatus(), records,
                d.getCertIssuer(), d.getCertNotAfter(), d.getAttempts(), d.getLastError(), d.getLastCheckedAt(),
                d.getActivatedAt(), d.getCreatedAt());
    }
}
