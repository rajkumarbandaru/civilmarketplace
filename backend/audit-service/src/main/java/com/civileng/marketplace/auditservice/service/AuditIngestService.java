package com.civileng.marketplace.auditservice.service;

import com.civileng.marketplace.auditservice.model.AccessAnomalyAlert;
import com.civileng.marketplace.auditservice.model.AuditEvent;
import com.civileng.marketplace.auditservice.repository.AccessAnomalyAlertRepository;
import com.civileng.marketplace.auditservice.repository.AuditEventRepository;
import com.civileng.marketplace.auditservice.util.AuditHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Consumes audit events off Kafka and appends them to the audit log.
 *
 * <p>Ingest is deliberately decoupled from the producing request: the producer fires and forgets,
 * so a slow or failing audit store can never make a business operation fail or hang.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditIngestService {

    private final AuditEventRepository auditEventRepository;
    private final AccessAnomalyAlertRepository anomalyRepository;
    private final ObjectMapper objectMapper;

    @Value("${audit.anomaly.bulk-read-threshold:50}")
    private int bulkReadThreshold;

    @Value("${audit.anomaly.window-minutes:10}")
    private int windowMinutes;

    @KafkaListener(topics = "audit.events", groupId = "audit-service-group")
    public void consume(String message) {
        try {
            record(objectMapper.readTree(message));
        } catch (Exception e) {
            // Never rethrow: a malformed event must not stall the partition and block every
            // subsequent audit record.
            log.error("Failed to ingest audit event: {} — payload: {}", e.getMessage(), message);
        }
    }

    @Transactional
    public AuditEvent record(JsonNode node) {
        AuditEvent previous = auditEventRepository.findTopByOrderByIdDesc();
        String previousHash = previous == null ? null : previous.getEventHash();

        AuditEvent event = AuditEvent.builder()
                .sourceService(text(node, "sourceService", "unknown"))
                .actorId(longOrNull(node, "actorId"))
                .actorRole(text(node, "actorRole", null))
                .action(text(node, "action", "UNKNOWN"))
                .entityType(text(node, "entityType", "unknown"))
                .entityId(text(node, "entityId", null))
                .subjectUserId(longOrNull(node, "subjectUserId"))
                .beforeState(text(node, "beforeState", null))
                .afterState(text(node, "afterState", null))
                .reason(text(node, "reason", null))
                .recordCount(node.hasNonNull("recordCount") ? node.get("recordCount").asInt() : null)
                .occurredAt(parseInstant(node))
                .previousHash(previousHash)
                .build();

        event.setEventHash(AuditHasher.computeHash(event, previousHash));
        AuditEvent saved = auditEventRepository.save(event);

        detectBulkReadAnomaly(saved);
        return saved;
    }

    /**
     * Flags an actor reading an unusual volume of sensitive records in a short window — the
     * signature of a bulk export of personal data rather than normal case-by-case review.
     */
    private void detectBulkReadAnomaly(AuditEvent event) {
        if (!"READ".equals(event.getAction()) || event.getActorId() == null) {
            return;
        }
        Instant since = Instant.now().minusSeconds(windowMinutes * 60L);
        long records = auditEventRepository.countRecordsReadSince(
                event.getActorId(), event.getEntityType(), since);

        if (records <= bulkReadThreshold) {
            return;
        }
        // One alert per actor/entity per window, rather than one per read past the threshold.
        boolean alreadyAlerted = anomalyRepository.existsByActorIdAndEntityTypeAndCreatedAtAfter(
                event.getActorId(), event.getEntityType(),
                LocalDateTime.now().minusMinutes(windowMinutes));
        if (alreadyAlerted) {
            return;
        }

        anomalyRepository.save(AccessAnomalyAlert.builder()
                .actorId(event.getActorId())
                .entityType(event.getEntityType())
                .recordsAccessed((int) records)
                .windowMinutes(windowMinutes)
                .detail("Actor read " + records + " " + event.getEntityType()
                        + " records in " + windowMinutes + " minutes (threshold "
                        + bulkReadThreshold + ")")
                .build());
        log.warn("Access anomaly: actor {} read {} {} records in {} min",
                event.getActorId(), records, event.getEntityType(), windowMinutes);
    }


    private static String text(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) ? node.get(field).asText() : fallback;
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asLong() : null;
    }

    private static Instant parseInstant(JsonNode node) {
        if (!node.hasNonNull("occurredAt")) {
            return Instant.now();
        }
        JsonNode value = node.get("occurredAt");
        try {
            // Jackson may serialise Instant as epoch seconds (a number) or ISO-8601 (a string)
            // depending on the producer's mapper config — accept both.
            return value.isNumber()
                    ? Instant.ofEpochMilli((long) (value.asDouble() * 1000))
                    : Instant.parse(value.asText());
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
