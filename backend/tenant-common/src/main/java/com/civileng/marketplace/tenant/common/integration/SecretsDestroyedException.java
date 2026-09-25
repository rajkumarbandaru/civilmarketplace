package com.civileng.marketplace.tenant.common.integration;

/** The tenant's keys were destroyed (crypto-shredding): its sealed secrets can never be read again. */
public class SecretsDestroyedException extends IllegalStateException {

    public SecretsDestroyedException(String tenantKey, IntegrationCapability capability) {
        super("The " + capability.name().toLowerCase() + " credentials of '" + tenantKey
                + "' were destroyed with its encryption keys");
    }
}
