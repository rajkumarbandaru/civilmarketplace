package com.civileng.marketplace.audit.common;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration so a service gains audit publishing by adding the dependency alone.
 *
 * <p>Registered via {@code META-INF/spring/...AutoConfiguration.imports} rather than relying on
 * component scanning — this package sits outside every service's own scan root.
 *
 * <p>The Kafka beans back off if the including service already defines its own, so services that
 * already produce events (auth-service, payment-service) keep their existing configuration.
 */
@Configuration
public class AuditAutoConfiguration {

    private static final String TENANT_PRODUCER_INTERCEPTOR =
            "com.civileng.marketplace.tenant.common.TenantKafkaProducerInterceptor";

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, AuditAutoConfiguration.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Bean
    @ConditionalOnMissingBean(ProducerFactory.class)
    public ProducerFactory<String, Object> auditProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        // Bounded so a broker outage cannot stall callers behind a full buffer.
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);

        // This factory is built from scratch rather than from spring.kafka.producer.*, so the
        // tenant-stamping interceptor configured there never reaches it — audit events would
        // arrive at audit-service with no tenant and be dropped. Wired by name so audit-common
        // keeps working in a service that has no tenant-common on its classpath.
        if (isPresent(TENANT_PRODUCER_INTERCEPTOR)) {
            config.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, TENANT_PRODUCER_INTERCEPTOR);
        }

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, Object> auditKafkaTemplate(
            ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    @ConditionalOnMissingBean(AuditPublisher.class)
    public AuditPublisher auditPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        return new AuditPublisher(kafkaTemplate);
    }
}
