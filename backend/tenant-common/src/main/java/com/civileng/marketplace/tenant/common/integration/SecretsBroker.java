package com.civileng.marketplace.tenant.common.integration;

/**
 * Where tenant secrets are sealed and opened (architecture 06 §9): a broker holding one key per
 * (tenant, capability) — the tenant's data-encryption keys, themselves protected by the broker's
 * own master key (KMS/HSM in production). Services never hold a key: they ask the broker to seal or
 * open, under a policy that says which capabilities they may open, and every open is audited.
 * Destroying a tenant's keys makes everything sealed under them unreadable, backups included.
 */
public interface SecretsBroker {

    String seal(String plaintext, String tenantKey, IntegrationCapability capability);

    String open(String sealed, String tenantKey, IntegrationCapability capability);

    /** Destroys every key of the tenant. Irreversible. Returns how many keys were destroyed. */
    int shred(String tenantKey);

    /** A sealed value this broker produced. */
    boolean owns(String sealed);

    /** The key a tenant's secrets for one capability are sealed under: {@code payment-tenant-acme}. */
    static String keyName(String tenantKey, IntegrationCapability capability) {
        return capability.name().toLowerCase() + "-tenant-" + tenantKey;
    }
}
