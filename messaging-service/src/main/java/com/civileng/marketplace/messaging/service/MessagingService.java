package com.civileng.marketplace.messaging.service;

import com.civileng.marketplace.messaging.client.BookingDto;
import com.civileng.marketplace.messaging.client.BookingServiceClient;
import com.civileng.marketplace.messaging.model.Message;
import com.civileng.marketplace.messaging.model.MessageThread;
import com.civileng.marketplace.messaging.repository.MessageRepository;
import com.civileng.marketplace.messaging.repository.MessageThreadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessagingService {

    private static final int PREVIEW_LENGTH = 280;

    private final MessageThreadRepository threadRepository;
    private final MessageRepository messageRepository;
    private final BookingServiceClient bookingServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Sends a message, lazily creating the thread on the first message for a booking. A thread
     * requires both parties to exist on the booking — messaging opens once a worker is assigned,
     * matching when there is actually someone to talk to.
     */
    @Transactional
    public Message sendMessage(Long bookingId, Long senderId, String body) {
        MessageThread thread = threadRepository.findByBookingId(bookingId)
                .orElseGet(() -> createThread(bookingId, senderId));

        if (!thread.involves(senderId)) {
            throw new IllegalArgumentException("You are not a party to this booking's conversation");
        }

        Message message = messageRepository.save(Message.builder()
                .threadId(thread.getId())
                .senderId(senderId)
                .body(body)
                .build());

        String preview = body.length() > PREVIEW_LENGTH ? body.substring(0, PREVIEW_LENGTH) : body;
        thread.setLastMessageAt(LocalDateTime.now());
        thread.setLastMessagePreview(preview);

        Long recipientId;
        if (thread.isCustomer(senderId)) {
            thread.setWorkerUnreadCount(thread.getWorkerUnreadCount() + 1);
            recipientId = thread.getWorkerId();
        } else {
            thread.setCustomerUnreadCount(thread.getCustomerUnreadCount() + 1);
            recipientId = thread.getCustomerId();
        }
        threadRepository.save(thread);

        publishMessageSent(bookingId, message, recipientId);
        return message;
    }

    private MessageThread createThread(Long bookingId, Long requesterId) {
        BookingDto booking = bookingServiceClient.getBooking(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found or booking-service unavailable");
        }
        if (booking.getWorkerId() == null) {
            throw new IllegalArgumentException(
                    "This booking has no assigned worker yet — nothing to message");
        }
        if (!booking.involves(requesterId)) {
            throw new IllegalArgumentException("You are not a party to this booking");
        }
        return threadRepository.save(MessageThread.builder()
                .bookingId(bookingId)
                .customerId(booking.getCustomerId())
                .workerId(booking.getWorkerId())
                .build());
    }

    @Transactional
    public Page<Message> getMessages(Long bookingId, Long userId, Pageable pageable) {
        MessageThread thread = threadRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("No conversation for this booking yet"));
        if (!thread.involves(userId)) {
            throw new IllegalArgumentException("You are not a party to this booking's conversation");
        }

        Page<Message> messages = messageRepository.findByThreadIdOrderByIdDesc(thread.getId(), pageable);
        markRead(thread, userId);
        return messages;
    }

    private void markRead(MessageThread thread, Long userId) {
        if (thread.isCustomer(userId) && thread.getCustomerUnreadCount() > 0) {
            thread.setCustomerUnreadCount(0);
            threadRepository.save(thread);
        } else if (!thread.isCustomer(userId) && thread.getWorkerUnreadCount() > 0) {
            thread.setWorkerUnreadCount(0);
            threadRepository.save(thread);
        }
    }

    @Transactional(readOnly = true)
    public Page<MessageThread> getMyThreads(Long userId, Pageable pageable) {
        return threadRepository.findByParticipant(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return threadRepository.totalUnreadForUser(userId);
    }

    /** Fire-and-forget fan-out target for notification-service; never blocks the send. */
    private void publishMessageSent(Long bookingId, Message message, Long recipientId) {
        try {
            kafkaTemplate.send("message.sent", String.valueOf(bookingId), Map.of(
                    "bookingId", bookingId,
                    "threadId", message.getThreadId(),
                    "senderId", message.getSenderId(),
                    "recipientId", recipientId,
                    "preview", message.getBody().length() > PREVIEW_LENGTH
                            ? message.getBody().substring(0, PREVIEW_LENGTH) : message.getBody()
            ));
        } catch (Exception e) {
            log.warn("Failed to publish message.sent for booking {}: {}", bookingId, e.getMessage());
        }
    }
}
