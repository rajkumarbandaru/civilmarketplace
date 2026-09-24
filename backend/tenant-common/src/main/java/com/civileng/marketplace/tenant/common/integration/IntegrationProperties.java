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

    /** Base64 AES-256 key that seals every tenant's secrets. Same value in every service. */
    private String masterKey;

    /** The operator tenant: the only one that resolves to the platform's own credentials. */
    private String operatorTenant = "platform";

    /** How long a resolved row is reused before re-reading tenant-service's table. */
    private Duration cacheTtl = Duration.ofSeconds(30);
}
