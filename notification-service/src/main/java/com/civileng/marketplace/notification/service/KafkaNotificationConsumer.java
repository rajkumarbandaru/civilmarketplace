package com.civileng.marketplace.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaNotificationConsumer {

    private final EmailService emailService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.registered", groupId = "notification-service-group")
    public void handleUserRegistered(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String email = (String) data.get("email");
            String name = (String) data.get("name");

            emailService.sendWelcomeEmail(email, name);
            log.info("Welcome email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to process user.registered event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "otp.sent", groupId = "notification-service-group")
    public void handleOtpSent(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String email = (String) data.get("email");
            String otp = (String) data.get("otp");

            emailService.sendOtpEmail(email, otp);
            log.info("OTP email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to process otp.sent event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.completed", groupId = "notification-service-group")
    public void handlePaymentCompleted(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            Long userId = Long.valueOf(data.get("paymentId").toString());
            String paymentCode = (String) data.get("paymentCode");

            notificationService.createNotification(
                    userId, "PAYMENT_COMPLETED",
                    "Payment Successful",
                    "Your payment " + paymentCode + " has been completed successfully.",
                    "IN_APP", "PAYMENT", userId, message);
            log.info("Payment notification sent for: {}", paymentCode);
        } catch (Exception e) {
            log.error("Failed to process payment.completed event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "booking.created", groupId = "notification-service-group")
    public void handleBookingCreated(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            Long userId = Long.valueOf(data.get("customerId").toString());
            String bookingCode = (String) data.get("bookingCode");

            notificationService.createNotification(
                    userId, "BOOKING_CREATED",
                    "Booking Created",
                    "Your booking " + bookingCode + " has been created successfully.",
                    "IN_APP", "BOOKING", userId, message);
            log.info("Booking notification sent for: {}", bookingCode);
        } catch (Exception e) {
            log.error("Failed to process booking.created event: {}", e.getMessage());
        }
    }

    /**
     * Unlike the other listeners in this class, the parameter here is {@code Map}, not
     * {@code String}: the consumer factory's {@code value-deserializer} is
     * {@code JsonDeserializer}, which delivers an already-parsed object, not raw JSON text.
     * {@code message.sent} is (as of 2026-08-12) the first event any producer has actually sent
     * against this consumer group live, which is why the mismatch between the other listeners'
     * {@code String} parameter and the configured deserializer was never caught — they've never
     * received a real message to fail on. Fix those the same way if/when they get a real producer.
     */
    @KafkaListener(topics = "message.sent", groupId = "notification-service-group")
    public void handleMessageSent(Map<String, Object> data) {
        try {
            Long recipientId = Long.valueOf(data.get("recipientId").toString());
            String preview = (String) data.get("preview");

            notificationService.createNotification(
                    recipientId, "MESSAGE_RECEIVED",
                    "New message",
                    preview,
                    "IN_APP", "MESSAGE", recipientId, objectMapper.writeValueAsString(data));
            log.info("Message notification sent to user {}", recipientId);
        } catch (Exception e) {
            log.error("Failed to process message.sent event: {}", e.getMessage());
        }
    }
}
