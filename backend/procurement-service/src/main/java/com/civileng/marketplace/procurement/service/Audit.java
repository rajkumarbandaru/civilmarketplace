package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Every state change in procurement is audited: who raised, approved, received and invoiced what. */
@Component
@Slf4j
@RequiredArgsConstructor
public class Audit {

    private static final String SOURCE = "procurement-service";

    private final AuditPublisher publisher;

    public void record(Long actorId, AuditAction action, String entityType, Object entityId, String detail) {
        try {
            publisher.publish(AuditEventMessage.builder()
                    .sourceService(SOURCE)
                    .actorId(actorId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(String.valueOf(entityId))
                    .afterState(detail)
                    .build());
        } catch (RuntimeException e) {
            // The audit pipeline being down must not stop trade.
            log.error("Could not publish audit event for {} {}", entityType, entityId, e);
        }
    }
}
