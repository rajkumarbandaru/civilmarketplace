package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.notification.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The three moments a customer hears from us about a booking: it was paid for, the professional is
 * nearly there, and the job is done.
 *
 * <p>Separate from {@link KafkaNotificationConsumer} because these events come from booking-service
 * and carry the booking itself — the service, the address, the schedule, the money. The older
 * {@code payment.completed} handler lives there and can only speak in payment codes, which is why
 * a customer's receipt never said what they had bought.
 *
 * <p>Every handler swallows its own failures: a mail that cannot be rendered must not stall the
 * consumer group behind a message it will never get past.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingLifecycleConsumer {

    private final NotificationDispatcher dispatcher;
    private final EmailService emailService;

    /** Payment cleared: receipt with the booking's details and its scheduled date and time. */
    @KafkaListener(topics = "booking.paid", groupId = "notification-service-group")
    public void onBookingPaid(Map<String, Object> data) {
        try {
            String email = str(data, "email");
            String bookingCode = str(data, "bookingCode");

            dispatcher.dispatch(new NotificationRequest(
                    asLong(data, "customerId"), "BOOKING_PAID", "Booking confirmed",
                    "Payment received. Booking " + bookingCode + " is confirmed for "
                            + str(data, "scheduledDate") + ".",
                    email, str(data, "phone"),
                    List.of("IN_APP", "SMS", "WHATSAPP"),
                    "BOOKING", asLong(data, "bookingId"), null));

            emailService.sendBookingPaidReceipt(email, data);
            log.info("Paid-booking receipt sent for {}", bookingCode);
        } catch (Exception e) {
            log.error("Failed to process booking.paid: {}", e.getMessage(), e);
        }
    }

    /**
     * The professional is within a kilometre or about five minutes out.
     *
     * <p>Goes out on the fast channels as well as email: a customer who needs to open a gate has
     * minutes to act on it, and email alone is the channel they are least likely to see in time.
     */
    @KafkaListener(topics = "booking.arriving", groupId = "notification-service-group")
    public void onWorkerArriving(Map<String, Object> data) {
        try {
            String email = str(data, "email");
            Object eta = data.get("etaMinutes");

            dispatcher.dispatch(new NotificationRequest(
                    asLong(data, "customerId"), "WORKER_ARRIVING", "Your professional is nearly here",
                    "Arriving in about " + eta + " minutes for " + str(data, "serviceName") + ".",
                    email, str(data, "phone"),
                    List.of("IN_APP", "SMS", "WHATSAPP"),
                    "BOOKING", asLong(data, "bookingId"), null));

            emailService.sendWorkerArriving(email, data);
            log.info("Arrival alert sent for booking {} ({} min out)", data.get("bookingCode"), eta);
        } catch (Exception e) {
            log.error("Failed to process booking.arriving: {}", e.getMessage(), e);
        }
    }

    /** Job finished: thank-you and rating request, plus the invoice on a pay-later booking. */
    @KafkaListener(topics = "booking.completed", groupId = "notification-service-group")
    public void onBookingCompleted(Map<String, Object> data) {
        try {
            String email = str(data, "email");
            String amountDue = str(data, "amountDue");
            boolean invoiced = amountDue != null && !amountDue.isBlank();

            dispatcher.dispatch(new NotificationRequest(
                    asLong(data, "customerId"),
                    invoiced ? "BOOKING_INVOICED" : "BOOKING_COMPLETED",
                    invoiced ? "Your invoice is ready" : "Thanks for using our service",
                    invoiced
                            ? "Booking " + str(data, "bookingCode") + " is complete. ₹" + amountDue
                              + " is due — you can pay by card, UPI or net banking."
                            : "Booking " + str(data, "bookingCode")
                              + " is complete. We would love your rating.",
                    email, str(data, "phone"),
                    List.of("IN_APP", "SMS", "WHATSAPP"),
                    "BOOKING", asLong(data, "bookingId"), null));

            emailService.sendBookingCompleted(email, data);
            log.info("Completion mail sent for {} (invoice: {})", data.get("bookingCode"), invoiced);
        } catch (Exception e) {
            log.error("Failed to process booking.completed: {}", e.getMessage(), e);
        }
    }

    private static String str(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
