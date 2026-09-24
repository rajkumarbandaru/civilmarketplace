package com.civileng.marketplace.auth.config;

import com.civileng.marketplace.tenant.common.TenantKafkaProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    /** Without it every auth event (OTP, invitation) reaches consumers with no tenant and is dropped. */
    @Test
    void everyRecordIsStampedWithItsTenant() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(config, "bootstrapServers", "kafka:9092");
        var factory = (DefaultKafkaProducerFactory<String, Object>) config.producerFactory();
        assertThat(factory.getConfigurationProperties().get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG))
                .isEqualTo(TenantKafkaProducerInterceptor.class.getName());
    }
}
