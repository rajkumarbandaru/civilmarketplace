package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.notification.dto.EmailLogDto.LogResponse;
import com.civileng.marketplace.notification.dto.EmailLogDto.LogSummary;
import com.civileng.marketplace.notification.model.EmailLog;
import com.civileng.marketplace.notification.model.EmailStatus;
import com.civileng.marketplace.notification.model.NotificationChannel;
import com.civileng.marketplace.notification.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The delivery log behind the console's Emails screen: one row per send attempt, from the moment
 * it is queued to whatever the provider last told us about it.
 *
 * <p>Every write runs {@code REQUIRES_NEW} and swallows its own failures. Logging is observability,
 * not the job — a full disk or a lock timeout here must not take down the email that the customer
 * is waiting on.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailLogService {

    /**
     * Brevo's webhook event names mapped onto our statuses. Anything not listed — {@code opened},
     * {@code click}, and the rest of the engagement events — tells us nothing about delivery and
     * is ignored.
     */
    private static final Map<String, EmailStatus> BREVO_EVENTS = Map.of(
            "delivered", EmailStatus.DELIVERED,
            "hard_bounce", EmailStatus.UNDELIVERED,
            "soft_bounce", EmailStatus.UNDELIVERED,
            "blocked", EmailStatus.UNDELIVERED,
            "spam", EmailStatus.UNDELIVERED,
            "invalid_email", EmailStatus.UNDELIVERED,
            "deferred", EmailStatus.PENDING,
            "error", EmailStatus.FAILED);

    private static final int MAX_ERROR_LENGTH = 1000;

    private final EmailLogRepository repository;
    private final EmailTemplateService templateService;

    /** Opens a row at PENDING before the send is attempted, so a crash mid-send still shows up. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long open(String templateKey, String recipient, String subject, String provider,
                     Long triggeredBy) {
        return open(NotificationChannel.EMAIL, templateKey, recipient, subject, provider, triggeredBy);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long open(NotificationChannel channel, String templateKey, String recipient,
                     String subject, String provider, Long triggeredBy) {
        try {
            EmailLog saved = repository.save(EmailLog.builder()
                    .templateKey(templateKey)
                    .channel(channel)
                    .recipient(recipient)
                    .subject(truncate(subject, 300))
                    .status(EmailStatus.PENDING)
                    .provider(provider)
                    .triggeredBy(triggeredBy)
                    .build());
            return saved.getId();
        } catch (Exception e) {
            log.warn("[EmailLog] could not open log row for {}: {}", recipient, e.getMessage());
            return null;
        }
    }

    /**
     * Records a delivery whose outcome is already known, in one row.
     *
     * <p>Email opens a PENDING row first because rendering and the provider call can each fail
     * separately and a crash between them must still leave a trace. SMS, WhatsApp and in-app have
     * no such gap — the body is a string that already exists and the call either returned or threw
     * — so a second write would buy nothing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(NotificationChannel channel, String sourceKey, String recipient,
                       String summary, String body, EmailStatus status, String provider,
                       String providerMessageId, String error) {
        try {
            repository.save(EmailLog.builder()
                    .templateKey(sourceKey)
                    .channel(channel)
                    .recipient(recipient)
                    .subject(truncate(summary, 300))
                    .body(body)
                    .status(status)
                    .provider(provider)
                    .providerMessageId(providerMessageId)
                    .errorMessage(truncate(error, MAX_ERROR_LENGTH))
                    .build());
        } catch (Exception e) {
            log.warn("[EmailLog] could not record {} delivery to {}: {}",
                    channel, recipient, e.getMessage());
        }
    }

    /**
     * Stores the rendered body against an already-open row.
     *
     * <p>Separate from {@link #open} because email opens its row <em>before</em> rendering — so
     * that a render failure still leaves a trace — and the body does not exist until after.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachBody(Long logId, String body) {
        if (logId == null || body == null) {
            return;
        }
        try {
            repository.findById(logId).ifPresent(row -> {
                row.setBody(body);
                repository.save(row);
            });
        } catch (Exception e) {
            log.warn("[EmailLog] could not store body for row {}: {}", logId, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long logId, EmailStatus status, String providerMessageId, String error) {
        if (logId == null) {
            return;
        }
        try {
            repository.findById(logId).ifPresent(row -> {
                row.setStatus(status);
                if (providerMessageId != null) {
                    row.setProviderMessageId(providerMessageId);
                }
                row.setErrorMessage(truncate(error, MAX_ERROR_LENGTH));
                repository.save(row);
            });
        } catch (Exception e) {
            log.warn("[EmailLog] could not update log row {}: {}", logId, e.getMessage());
        }
    }

    /**
     * Applies one Brevo webhook event.
     *
     * <p>A late {@code deferred} after a {@code delivered} would otherwise walk the row backwards,
     * so a terminal status is never overwritten by a non-terminal one.
     */
    @Transactional
    public boolean applyProviderEvent(String messageId, String event, String reason) {
        EmailStatus mapped = BREVO_EVENTS.get(event == null ? "" : event.toLowerCase());
        if (mapped == null || messageId == null || messageId.isBlank()) {
            return false;
        }
        return repository.findFirstByProviderMessageIdOrderByIdDesc(messageId)
                .map(row -> {
                    if (isTerminal(row.getStatus()) && !isTerminal(mapped)) {
                        return false;
                    }
                    row.setStatus(mapped);
                    if (reason != null && !reason.isBlank()) {
                        row.setErrorMessage(truncate(reason, MAX_ERROR_LENGTH));
                    }
                    repository.save(row);
                    return true;
                })
                .orElse(false);
    }

    private static boolean isTerminal(EmailStatus status) {
        return status == EmailStatus.DELIVERED
                || status == EmailStatus.UNDELIVERED
                || status == EmailStatus.FAILED;
    }

    @Transactional(readOnly = true)
    public Page<LogResponse> search(EmailStatus status, NotificationChannel channel,
                                    String templateKey, String search, Pageable pageable) {
        Map<String, String> names = templateService.nameByKey();
        String needle = search == null || search.isBlank() ? null : search.trim();
        String key = templateKey == null || templateKey.isBlank() ? null : templateKey;
        // Without the body: a page of 25 emails would otherwise ship ~100 KB of HTML nobody is
        // looking at yet. The eye icon fetches one row in full when it is actually wanted.
        return repository.search(status, channel, key, needle, pageable)
                .map(row -> LogResponse.from(row,
                        names.getOrDefault(row.getTemplateKey(), row.getTemplateKey()), false));
    }

    @Transactional(readOnly = true)
    public LogSummary summary() {
        Map<String, Long> byStatus = new HashMap<>();
        for (EmailStatus status : EmailStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        long total = 0;
        for (Object[] row : repository.countByStatus()) {
            byStatus.put(((EmailStatus) row[0]).name(), ((Number) row[1]).longValue());
            total += ((Number) row[1]).longValue();
        }

        Map<String, Long> byChannel = new HashMap<>();
        for (NotificationChannel channel : NotificationChannel.values()) {
            byChannel.put(channel.name(), 0L);
        }
        for (Object[] row : repository.countByChannel()) {
            byChannel.put(((NotificationChannel) row[0]).name(), ((Number) row[1]).longValue());
        }

        return LogSummary.builder().total(total).byStatus(byStatus).byChannel(byChannel).build();
    }

    @Transactional(readOnly = true)
    public LogResponse get(Long id) {
        Map<String, String> names = templateService.nameByKey();
        return repository.findById(id)
                .map(row -> LogResponse.from(row,
                        names.getOrDefault(row.getTemplateKey(), row.getTemplateKey()), true))
                .orElse(null);
    }

    /** Distinct template keys present in the log, for the filter dropdown. */
    @Transactional(readOnly = true)
    public List<String> loggedTemplateKeys() {
        return repository.findDistinctTemplateKeys();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
