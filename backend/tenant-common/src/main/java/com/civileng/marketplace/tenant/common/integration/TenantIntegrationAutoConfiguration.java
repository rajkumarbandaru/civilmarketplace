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

    @Bean
    @ConditionalOnMissingBean
    public IntegrationCipher integrationCipher(IntegrationProperties properties) {
        return new IntegrationCipher(properties.getMasterKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantIntegrationStore tenantIntegrationStore(TenantProperties tenantProperties,
                                                         IntegrationCipher cipher,
                                                         IntegrationProperties properties) {
        return new JdbcTenantIntegrationStore(tenantProperties.getRegistry(), cipher,
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
