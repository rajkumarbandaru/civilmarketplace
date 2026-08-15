package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.notification.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Single entry point for sending one notification across several channels.
 *
 * <p>Before this existed each caller reached for {@link EmailService} or {@link SmsService}
 * directly and the in-app row was created separately, so which channels a given event
 * actually used was scattered across the Kafka listeners. A request now names its channels
 * and the dispatcher fans out, skipping any channel with no usable address for the recipient.
 *
 * <p>Delivery itself is best-effort and non-blocking: each channel logs its own failures and
 * none of them propagate, because a notification must never fail the action that triggered it.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final WhatsAppService whatsAppService;

    public void dispatch(NotificationRequest request) {
        Set<String> channels = new LinkedHashSet<>(request.channels());

        for (String channel : channels) {
            switch (channel.toUpperCase()) {
                case "IN_APP" -> inApp(request);
                case "EMAIL" -> emailService.sendNotificationEmail(
                        request.email(), request.title(), request.message());
                case "SMS" -> smsService.send(request.phone(), plain(request));
                case "WHATSAPP" -> whatsAppService.send(request.phone(), plain(request));
                default -> log.warn("Unknown notification channel '{}' for type {}",
                        channel, request.type());
            }
        }
    }

    private void inApp(NotificationRequest request) {
        if (request.userId() == null) {
            log.warn("IN_APP notification of type {} skipped - no userId", request.type());
            return;
        }
        try {
            notificationService.createNotification(
                    request.userId(), request.type(), request.title(), request.message(),
                    "IN_APP", request.referenceType(), request.referenceId(), request.data());
        } catch (Exception e) {
            log.error("Failed to persist IN_APP notification for user {}: {}",
                    request.userId(), e.getMessage());
        }
    }

    /** SMS and WhatsApp bodies carry the title inline; neither channel has a subject line. */
    private static String plain(NotificationRequest request) {
        return request.title() + ": " + request.message();
    }
}
