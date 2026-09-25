package com.civileng.marketplace.tenant.factory;

import com.civileng.marketplace.tenant.common.TenantTopics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({FactoryProperties.class, com.civileng.marketplace.tenant.placement.PlacementProperties.class, com.civileng.marketplace.tenant.domain.DomainProperties.class})
public class FactoryConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Acks are JSON text (see tenant-common's TenantProvisioningAcks), read as plain strings. */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> provisioningAckContainerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                // Acks only matter for tenants provisioning now; nothing from before this process.
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")));
        return factory;
    }

    static final String ACK_TOPIC = TenantTopics.TENANT_PROVISIONED;
}
