package com.civileng.marketplace.audit.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Publishes audit events off the request's critical path.
 *
 * <p>Two deliberate properties:
 * <ul>
 *   <li>A publish failure never fails the business operation — auditing must not make the
 *       platform unavailable. Failures are logged at ERROR so they are still visible.</li>
 *   <li>Sends are asynchronous; the caller does not block on the broker.</li>
 * </ul>
 * The trade-off is that a broker outage loses events rather than blocking writes. If stronger
 * guarantees are needed later, add a transactional outbox in the producing service rather than
 * making this call synchronous.
 */
@Slf4j
public class AuditPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AuditPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(AuditEventMessage event) {
        try {
            String key = event.getEntityType() + ":" +
                    (event.getEntityId() == null ? "-" : event.getEntityId());
            kafkaTemplate.send(AuditTopics.AUDIT_EVENTS, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Audit event dropped ({} {} by actor {}): {}",
                                    event.getAction(), event.getEntityType(),
                                    event.getActorId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to publish audit event ({} {}): {}",
                    event.getAction(), event.getEntityType(), e.getMessage());
        }
    }
}
