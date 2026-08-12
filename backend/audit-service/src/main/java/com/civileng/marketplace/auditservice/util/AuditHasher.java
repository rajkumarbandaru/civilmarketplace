package com.civileng.marketplace.auditservice.util;

import com.civileng.marketplace.auditservice.model.AuditEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * The single source of truth for the audit hash chain, shared by ingest (which computes each
 * new event's hash) and integrity verification (which recomputes it from stored content and
 * compares). Keeping one implementation guarantees the two can never drift apart.
 */
public final class AuditHasher {

    private AuditHasher() {
    }

    public static String computeHash(AuditEvent e, String previousHash) {
        String payload = String.join("|",
                nz(previousHash), nz(e.getSourceService()), String.valueOf(e.getActorId()),
                nz(e.getAction()), nz(e.getEntityType()), nz(e.getEntityId()),
                String.valueOf(e.getSubjectUserId()), nz(e.getBeforeState()),
                nz(e.getAfterState()), nz(e.getReason()), String.valueOf(e.getOccurredAt()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
