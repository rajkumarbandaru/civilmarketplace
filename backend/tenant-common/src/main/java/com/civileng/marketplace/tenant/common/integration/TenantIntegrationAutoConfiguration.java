package com.civileng.marketplace.tenant.common.integration;

import com.civileng.marketplace.tenant.common.TenantProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Per-tenant provider credentials for services that talk to payment, mail, SMS, WhatsApp or AI
 * providers. Opt in with {@code platform.integrations.enabled=true}; the service also needs
 * {@code platform.tenant.registry.*}, which every tenanted service already has.
 */
@AutoConfiguration
@EnableConfigurationProperties({IntegrationProperties.class, TenantProperties.class})
@ConditionalOnProperty(prefix = "platform.integrations", name = "enabled", havingValue = "true")
public class TenantIntegrationAutoConfiguration {

    /**
     * The broker when configured, the old shared key when set (to read secrets sealed before the
     * broker). At least one is required.
     */
    @Bean
    @ConditionalOnMissingBean
    public IntegrationSecrets integrationSecrets(IntegrationProperties properties) {
        IntegrationProperties.Vault v = properties.getVault();
        SecretsBroker broker = v.getAddress() == null || v.getAddress().isBlank() ? null
                : new VaultTransitBroker(v.getAddress(), v.getToken(), v.getMount());
        IntegrationCipher legacy = properties.getMasterKey() == null || properties.getMasterKey().isBlank() ? null
                : new IntegrationCipher(properties.getMasterKey());
        return new IntegrationSecrets(broker, legacy);
    }

    /** The broker itself, for the one service that destroys keys (tenant-service). */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.integrations.vault", name = "address")
    public SecretsBroker secretsBroker(IntegrationProperties properties) {
        IntegrationProperties.Vault v = properties.getVault();
        return new VaultTransitBroker(v.getAddress(), v.getToken(), v.getMount());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantIntegrationStore tenantIntegrationStore(TenantProperties tenantProperties,
                                                         IntegrationSecrets secrets,
                                                         IntegrationProperties properties) {
        return new JdbcTenantIntegrationStore(tenantProperties.getRegistry(), secrets,
                properties.getCacheTtl(), Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantIntegrationResolver tenantIntegrationResolver(TenantIntegrationStore store,
                                                               IntegrationProperties properties) {
        return new TenantIntegrationResolver(store, properties.getOperatorTenant());
    }

    /** Missing-bean guarded: tenant-service's component scan covers this package and finds it too. */
    @Bean
    @ConditionalOnMissingBean
    public IntegrationExceptionHandler integrationExceptionHandler() {
        return new IntegrationExceptionHandler();
    }
}
