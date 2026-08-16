package com.civileng.marketplace.booking.event;

import com.civileng.marketplace.booking.model.Booking;
import com.civileng.marketplace.booking.model.BookingStatus;
import com.civileng.marketplace.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Marks a booking paid when payment-service confirms the money arrived.
 *
 * <p>Nothing did this before: {@code payment.completed} had a consumer in notification-service and
 * none here, so a booking's {@code paymentStatus} stayed "PENDING" forever no matter how much the
 * customer paid — the payment row said COMPLETED while the booking it belonged to did not know.
 *
 * <p>This is also where the paid-confirmation email begins: the enriched {@code booking.paid} event
 * is published from here, because this service is the one that knows what was booked and for when.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private static final String PAID = "PAID";

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher events;

    @KafkaListener(topics = "payment.completed", groupId = "booking-service-group")
    @Transactional
    public void onPaymentCompleted(Map<String, Object> data) {
        try {
            Long bookingId = asLong(data.get("bookingId"));
            if (bookingId == null) {
                log.warn("payment.completed without a bookingId: {}", data);
                return;
            }

            Booking booking = bookingRepository.findById(bookingId).orElse(null);
            if (booking == null) {
                log.warn("payment.completed for unknown booking {}", bookingId);
                return;
            }

            // Kafka redelivers, and a customer can pay only once — a second event must not send a
            // second receipt.
            if (PAID.equals(booking.getPaymentStatus())) {
                log.info("Booking {} already marked paid; ignoring duplicate event", bookingId);
                return;
            }

            booking.setPaymentStatus(PAID);
            // A paid booking is a real commitment rather than a request awaiting a quote, so it
            // joins the queue for assignment. An already-assigned or in-progress booking keeps the
            // status it has: payment arriving late must not walk a job backwards.
            if (booking.getStatus() == BookingStatus.QUOTATION_PENDING) {
                booking.setStatus(BookingStatus.PENDING);
            }
            Booking saved = bookingRepository.save(booking);

            events.publishPaid(saved, str(data.get("paymentCode")), amount(data.get("amount")));
            log.info("Booking {} marked paid from payment {}", bookingId, data.get("paymentId"));
        } catch (Exception e) {
            // Swallowed deliberately: a failure here must not park the consumer group on a message
            // it will never process, and the payment itself is already recorded.
            log.error("Failed to process payment.completed: {}", e.getMessage(), e);
        }
    }

    private static Long asLong(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal amount(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
