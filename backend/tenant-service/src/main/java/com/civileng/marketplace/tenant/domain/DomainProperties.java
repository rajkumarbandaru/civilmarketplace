package com.civileng.marketplace.tenant.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * The Domain Manager, bound from {@code platform.domains.*}.
 *
 * @param acmeDirectory  the ACME server: Let's Encrypt in production
 *                       ({@code acme://letsencrypt.org}), Pebble locally ({@code acme://pebble/pebble:14000})
 * @param contactEmail   the ACME account's contact
 * @param dnsServer      where DNS is asked ({@code host:port}); blank = the system resolver
 * @param edgeTarget     the name tenants point their domain at (CNAME)
 * @param platformDomains the platform's own zones: never claimable as a custom domain
 * @param certDirectory  where issued certificates are written for the edge (one {@code <host>.crt}/{@code .key} each)
 * @param verifyTimeout  how long a domain may stay unverified before it FAILS
 * @param recheck        how often a serving domain's DNS is re-checked
 * @param renewBefore    renew this long before the certificate expires
 * @param grace          how long a DEGRADED domain keeps serving before it is unbound
 */
@ConfigurationProperties(prefix = "platform.domains")
public record DomainProperties(String acmeDirectory, String contactEmail, String dnsServer, String edgeTarget,
                               List<String> platformDomains, String certDirectory, Duration verifyTimeout,
                               Duration recheck, Duration renewBefore, Duration grace) {

    public DomainProperties {
        acmeDirectory = blank(acmeDirectory) ? "acme://letsencrypt.org/staging" : acmeDirectory;
        contactEmail = blank(contactEmail) ? "ops@civilengineer.com" : contactEmail;
        edgeTarget = blank(edgeTarget) ? "edge.civilengineer.com" : edgeTarget;
        platformDomains = platformDomains == null || platformDomains.isEmpty()
                ? List.of("civilengineer.com", "localhost") : List.copyOf(platformDomains);
        certDirectory = blank(certDirectory) ? "/var/lib/platform/certs" : certDirectory;
        verifyTimeout = verifyTimeout == null ? Duration.ofHours(72) : verifyTimeout;
        recheck = recheck == null ? Duration.ofDays(1) : recheck;
        renewBefore = renewBefore == null ? Duration.ofDays(30) : renewBefore;
        grace = grace == null ? Duration.ofDays(7) : grace;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public String verificationName(String host) {
        return "_platform-verify." + host;
    }
}
