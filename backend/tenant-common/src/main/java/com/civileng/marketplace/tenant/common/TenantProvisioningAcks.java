package com.civileng.marketplace.tenant.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Sends provisioning acknowledgements. Its own String producer rather than the host service's
 * template: services configure their value serializers differently, and the saga needs one wire
 * format it can rely on.
 */
@Slf4j
public class TenantProvisioningAcks {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final KafkaTemplate<String, String> template;
    private final String service;

    public TenantProvisioningAcks(KafkaTemplate<String, String> template, String service) {
        this.template = template;
        this.service = service;
    }

    public void send(String tenantKey, boolean ok, String error) {
        try {
            String body = JSON.writeValueAsString(
                    new TenantProvisioningAck(tenantKey, service, ok, error, System.currentTimeMillis()));
            template.send(TenantTopics.TENANT_PROVISIONED, tenantKey, body);
        } catch (Exception e) {
            // The saga times out and retries if an ack is lost; never fail provisioning over it.
            log.warn("Could not send provisioning ack for tenant '{}'", tenantKey, e);
        }
    }
}
