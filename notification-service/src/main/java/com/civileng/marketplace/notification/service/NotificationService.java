package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.notification.model.Notification;
import com.civileng.marketplace.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification createNotification(Long userId, String type, String title,
                                            String message, String channel,
                                            String referenceType, Long referenceId,
                                            String data) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .channel(channel)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .data(data)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification created for user {}: {}", userId, type);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Notification> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(n -> {
                    n.setIsRead(true);
                    n.setReadAt(LocalDateTime.now());
                    notificationRepository.save(n);
                });
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(notificationRepository::delete);
    }
}
