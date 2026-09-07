package com.civileng.marketplace.tenant.common;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Binds the tenant carried on a record before the listener runs, so a Kafka consumer writes into
 * the schema the event came from rather than the bootstrap tenant's.
 *
 * <p>Boot applies a {@code RecordInterceptor} bean to its auto-configured listener container
 * factory, which is what all of this platform's consumers use. Consumers on a hand-built factory
 * — {@link TenantProvisioningListener} is the one — are untouched, which is correct: provisioning
 * must run outside any tenant.
 *
 * <p>A record with no tenant header is skipped rather than processed untenanted. Before
 * multi-tenancy every event was implicitly single-tenant, and silently steering those into the
 * bootstrap tenant would write one tenant's data into another's schema.
 */
@Slf4j
public class TenantRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(TenantKafkaProducerInterceptor.TENANT_HEADER);

        if (header == null) {
            log.warn("Dropping record from topic '{}' offset {} — no tenant header",
                    record.topic(), record.offset());
            return null;
        }

        TenantContext.set(new String(header.value(), StandardCharsets.UTF_8));
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record,
                            Consumer<Object, Object> consumer) {
        TenantContext.clear();
    }
}
