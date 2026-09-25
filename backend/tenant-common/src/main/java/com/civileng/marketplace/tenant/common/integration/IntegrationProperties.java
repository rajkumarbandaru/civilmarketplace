package com.civileng.marketplace.tenant.common.integration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code platform.integrations.*}: how tenant provider credentials are read. */
@Data
@ConfigurationProperties(prefix = "platform.integrations")
public class IntegrationProperties {

    /** Turns per-tenant integration resolution on for this service. */
    private boolean enabled = false;

    /**
     * The previous scheme: one base64 AES-256 key shared by every service. Now only needed to read
     * secrets sealed before the broker; leave it unset once they have been re-sealed.
     */
    private String masterKey;

    /** The secrets broker (Vault Transit). With it, new secrets are sealed per tenant and capability. */
    private final Vault vault = new Vault();

    @Data
    public static class Vault {
        /** e.g. http://vault:8200; blank = no broker. */
        private String address;
        /** This service's token; its Vault policy says what it may seal, open and destroy. */
        private String token;
        private String mount = "transit";
    }

    /** The operator tenant: the only one that resolves to the platform's own credentials. */
    private String operatorTenant = "platform";

    /** How long a resolved row is reused before re-reading tenant-service's table. */
    private Duration cacheTtl = Duration.ofSeconds(30);
}
