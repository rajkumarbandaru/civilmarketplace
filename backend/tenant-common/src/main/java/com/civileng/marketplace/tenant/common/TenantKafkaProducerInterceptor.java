package com.civileng.marketplace.tenant.common;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Stamps the producing thread's tenant onto every outgoing record.
 *
 * <p>Done as a header rather than a field on each event type deliberately: a Kafka event that
 * loses its tenant is a cross-tenant write on the consuming side, and that guarantee should not
 * depend on whoever writes the next event DTO remembering to add the field.
 *
 * <p>Instantiated by the Kafka client, not Spring — so it reads {@link TenantContext} statically
 * and holds no injected state.
 */
public class TenantKafkaProducerInterceptor implements ProducerInterceptor<Object, Object> {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        String tenantId = TenantContext.get();
        if (tenantId != null) {
            Headers headers = record.headers();
            headers.remove(TENANT_HEADER);
            headers.add(TENANT_HEADER, tenantId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}
