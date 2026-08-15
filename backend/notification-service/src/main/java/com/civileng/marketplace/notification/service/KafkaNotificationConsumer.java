package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.notification.dto.NotificationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Turns platform events into notifications.
 *
 * <p>Every listener takes {@code Map<String, Object>}, matching the consumer factory's
 * {@code JsonDeserializer}, which delivers an already-parsed object rather than raw JSON text.
 * (The listeners other than {@code message.sent} previously declared {@code String}; the
 * mismatch went unnoticed because no producer had yet sent a real event against this group.)
 *
 * <p>Channel selection lives here rather than in the dispatcher: which channels an event
 * deserves is a product decision per event type, not a property of the delivery layer.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaNotificationConsumer {

    private final EmailService emailService;
    private final SmsService smsService;
    private final WhatsAppService whatsAppService;
    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.registered", groupId = "notification-service-group")
    public void handleUserRegistered(Map<String, Object> data) {
        try {
            String email = str(data, "email");
            String name = str(data, "name");
            String phone = str(data, "phone");

            emailService.sendWelcomeEmail(email, name);
            whatsAppService.send(phone, "Welcome to Civil Engineering Marketplace, "
                    + (name == null ? "there" : name)
                    + "! Your account is ready. Complete your profile to get the best matches.");
            log.info("Welcome notifications dispatched for: {}", email);
        } catch (Exception e) {
            log.error("Failed to process user.registered event: {}", e.getMessage());
        }
    }

    /**
     * OTP goes to exactly one channel — the one the user chose to authenticate with. Fanning a
     * one-time code out to every address on the account would widen its exposure for no gain.
     */
    @KafkaListener(topics = "otp.sent", groupId = "notification-service-group")
    public void handleOtpSent(Map<String, Object> data) {
        try {
            String otp = str(data, "otp");
            // Older events carried no channel; those were always email.
            String channel = data.get("channel") == null ? "EMAIL" : str(data, "channel");

            switch (channel.toUpperCase()) {
                case "SMS" -> {
                    smsService.sendOtpSms(str(data, "phone"), otp);
                    log.info("OTP SMS dispatched");
                }
                case "WHATSAPP" -> {
                    whatsAppService.sendOtp(str(data, "phone"), otp);
                    log.info("OTP WhatsApp message dispatched");
                }
                default -> {
                    String email = str(data, "email");
                    emailService.sendOtpEmail(email, otp);
                    log.info("OTP email sent to: {}", email);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process otp.sent event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.completed", groupId = "notification-service-group")
    public void handlePaymentCompleted(Map<String, Object> data) {
        try {
            Long userId = asLong(data, "userId", "paymentId");
            String paymentCode = str(data, "paymentCode");
            String amount = str(data, "amount");

            dispatcher.dispatch(new NotificationRequest(
                    userId, "PAYMENT_COMPLETED", "Payment Successful",
                    "Your payment " + paymentCode + " has been completed successfully.",
                    str(data, "email"), str(data, "phone"),
                    List.of("IN_APP", "EMAIL", "WHATSAPP"),
                    "PAYMENT", asLong(data, "paymentId"), json(data)));

            // The receipt has its own template with the amount broken out, so it is sent
            // instead of the generic email body the dispatcher would produce.
            if (amount != null) {
                emailService.sendPaymentReceipt(str(data, "email"), str(data, "name"),
                        amount, paymentCode);
            }
            log.info("Payment notifications dispatched for: {}", paymentCode);
        } catch (Exception e) {
            log.error("Failed to process payment.completed event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "booking.created", groupId = "notification-service-group")
    public void handleBookingCreated(Map<String, Object> data) {
        try {
            Long customerId = asLong(data, "customerId");
            String bookingCode = str(data, "bookingCode");

            dispatcher.dispatch(new NotificationRequest(
                    customerId, "BOOKING_CREATED", "Booking Created",
                    "Your booking " + bookingCode + " has been created successfully.",
                    str(data, "email"), str(data, "phone"),
                    List.of("IN_APP", "SMS", "WHATSAPP"),
                    "BOOKING", asLong(data, "bookingId", "customerId"), json(data)));

            emailService.sendBookingConfirmation(str(data, "email"), str(data, "name"), bookingCode);
            log.info("Booking notifications dispatched for: {}", bookingCode);
        } catch (Exception e) {
            log.error("Failed to process booking.created event: {}", e.getMessage());
        }
    }

    /** In-app only: message previews are chat content and don't belong in SMS or email. */
    @KafkaListener(topics = "message.sent", groupId = "notification-service-group")
    public void handleMessageSent(Map<String, Object> data) {
        try {
            Long recipientId = asLong(data, "recipientId");

            dispatcher.dispatch(NotificationRequest.inApp(
                    recipientId, "MESSAGE_RECEIVED", "New message", str(data, "preview"),
                    "MESSAGE", recipientId, json(data)));
            log.info("Message notification sent to user {}", recipientId);
        } catch (Exception e) {
            log.error("Failed to process message.sent event: {}", e.getMessage());
        }
    }

    private static String str(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    /** Returns the first key present, so payloads that renamed a field still resolve. */
    private static Long asLong(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                try {
                    return Long.valueOf(value.toString());
                } catch (NumberFormatException ignored) {
                    // Fall through to the next candidate key.
                }
            }
        }
        return null;
    }

    private String json(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Could not serialise event payload: {}", e.getMessage());
            return null;
        }
    }
}
