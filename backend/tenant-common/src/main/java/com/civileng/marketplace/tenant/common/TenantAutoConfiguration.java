package com.civileng.marketplace.tenant.common;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * The tenant runtime every tenanted service shares: which tenant a thread is serving, how that
 * travels inbound (the gateway's header) and outbound (Feign, Kafka), and which tenants exist.
 * Services opt in with {@code platform.tenant.enabled=true}.
 *
 * <p>Deliberately free of JPA and Flyway. Storage-level isolation is the separate concern of
 * {@link TenantSchemaAutoConfiguration}, which only engages where Hibernate is on the classpath —
 * a service whose data lives somewhere other than MySQL (search-service, on Elasticsearch) needs
 * everything here and none of that, and isolates its own storage in its own terms.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(TenantProperties.class)
@ConditionalOnProperty(prefix = "platform.tenant", name = "enabled", havingValue = "true")
@EnableKafka
public class TenantAutoConfiguration {

    @Bean
    public TenantRegistry tenantRegistry(TenantProperties properties) {
        return new TenantRegistry(properties.getRegistry());
    }

    @Bean
    public FilterRegistrationBean<TenantHeaderFilter> tenantHeaderFilter(
            TenantProperties properties) {
        TenantHeaderFilter filter = new TenantHeaderFilter(properties);
        FilterRegistrationBean<TenantHeaderFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setOrder(filter.getOrder());
        return registration;
    }

    /** Lets {@code @Scheduled} jobs sweep every tenant instead of just the bootstrap one. */
    @Bean
    public CrossTenantRunner crossTenantRunner(TenantRegistry registry) {
        return new CrossTenantRunner(registry);
    }

    /**
     * Provisions a newly created tenant in whatever terms this service stores data. The migrator
     * is optional: a service with no schema layer has nothing to migrate but may still have
     * per-tenant setup to do through a {@link TenantProvisionedCallback}.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
    public TenantProvisioningListener tenantProvisioningListener(
            ObjectProvider<TenantSchemaMigrator> migrator,
            ObjectProvider<TenantProvisionedCallback> callbacks) {
        return new TenantProvisioningListener(migrator.getIfAvailable(),
                callbacks.orderedStream().toList());
    }

    /**
     * A dedicated consumer factory so tenant events deserialize into {@link TenantEventMessage}
     * regardless of whatever type mapping a host service configured for its own topics.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
    public ConcurrentKafkaListenerContainerFactory<String, TenantEventMessage>
            tenantEventListenerContainerFactory(
                    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TenantEventMessage.class.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        // latest, not earliest: every consuming service already reconciles the whole tenant list
        // at boot (schema bootstrap here, a full reindex sweep in search-service), so replaying the
        // topic from the beginning on a group with no committed offsets re-provisions every tenant
        // that has ever existed on top of that — wasted work that grows with the tenant count. The
        // events only need to carry tenants created *while this service is running*.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        ConsumerFactory<String, TenantEventMessage> consumerFactory =
                new DefaultKafkaConsumerFactory<>(config);
        ConcurrentKafkaListenerContainerFactory<String, TenantEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    /**
     * Picked up automatically by Boot's auto-configured Kafka listener container factory, which
     * every consumer in this platform uses.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
    public TenantRecordInterceptor tenantRecordInterceptor() {
        return new TenantRecordInterceptor();
    }

    /**
     * Nested so the Feign types are only loaded where Feign is actually on the classpath —
     * a {@code @ConditionalOnClass} on the bean method itself would still resolve the method
     * signature and fail in services that do not use Feign.
     */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignTenantPropagation {

        @Bean
        public TenantFeignInterceptor tenantFeignInterceptor() {
            return new TenantFeignInterceptor();
        }
    }
}
