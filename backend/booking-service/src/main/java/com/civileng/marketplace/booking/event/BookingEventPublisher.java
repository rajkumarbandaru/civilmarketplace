package com.civileng.marketplace.booking.event;

import com.civileng.marketplace.booking.client.UserServiceClient;
import com.civileng.marketplace.booking.model.Booking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Booking events that customer-facing notifications are built from.
 *
 * <p>Each event carries everything the notification needs — the customer's name and address, the
 * service, the schedule, the money — rather than only ids. notification-service has no database of
 * its own and no client for this one, so an event of ids would arrive as an email that could not
 * name what it was about. That is exactly what happened to {@code payment.completed}, whose
 * consumer read an {@code email} field the producer never sent.
 *
 * <p>Publishing never throws: an email that cannot be sent must not roll back a payment or a
 * completed job.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisher {

    public static final String BOOKING_PAID = "booking.paid";
    public static final String BOOKING_ARRIVING = "booking.arriving";
    public static final String BOOKING_COMPLETED = "booking.completed";

    private static final DateTimeFormatter SCHEDULE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' h:mm a");

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserServiceClient userServiceClient;

    /** Payment cleared: the receipt, with what was booked and when it is due to happen. */
    public void publishPaid(Booking booking, String paymentCode, BigDecimal amountPaid) {
        Map<String, Object> event = baseEvent(booking);
        event.put("paymentCode", paymentCode == null ? "" : paymentCode);
        event.put("amount", amountPaid == null ? "" : amountPaid.toPlainString());
        publish(BOOKING_PAID, event, booking.getId());
    }

    /**
     * The worker is nearly there.
     *
     * @param etaMinutes minutes to arrival, traffic-aware when a routing key is configured
     * @param distanceKm how far away they are, so the mail can say both
     * @param trafficAware whether {@code etaMinutes} came from a routing service or the fallback
     *                     estimate — the wording differs, because presenting a guess as a routed
     *                     ETA is how a customer ends up standing outside for twenty minutes
     */
    public void publishArriving(Booking booking, int etaMinutes, double distanceKm, boolean trafficAware) {
        Map<String, Object> event = baseEvent(booking);
        event.put("etaMinutes", etaMinutes);
        event.put("distanceKm", distanceKm);
        event.put("trafficAware", trafficAware);
        publish(BOOKING_ARRIVING, event, booking.getId());
    }

    /**
     * Job done: the thank-you, the ask for a rating, and — for a pay-later booking — the invoice.
     *
     * <p>{@code amountDue} is what decides whether the mail is a receipt-and-thanks or a bill: a
     * PREPAID booking was paid before the work started and owes nothing, while a POSTPAID one is
     * invoiced now.
     */
    public void publishCompleted(Booking booking) {
        Map<String, Object> event = baseEvent(booking);
        event.put("finalCost", booking.getFinalCost() == null ? "" : booking.getFinalCost().toPlainString());
        event.put("paymentPreference", booking.getPaymentPreference());
        boolean unpaid = !"PAID".equals(booking.getPaymentStatus());
        event.put("amountDue", unpaid && booking.getTotalAmount() != null
                ? booking.getTotalAmount().toPlainString() : "");
        publish(BOOKING_COMPLETED, event, booking.getId());
    }

    /** The fields every booking notification needs, resolved once. */
    private Map<String, Object> baseEvent(Booking booking) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("bookingId", booking.getId());
        event.put("bookingCode", booking.getBookingCode());
        event.put("customerId", booking.getCustomerId());
        event.put("workerId", booking.getWorkerId());
        event.put("serviceName", booking.getServiceName());
        event.put("serviceCategory", booking.getServiceCategory());
        event.put("addressLine", booking.getAddressLine() == null ? "" : booking.getAddressLine());
        event.put("city", booking.getCity() == null ? "" : booking.getCity());
        event.put("scheduledDate", booking.getScheduledDate() == null
                ? "" : booking.getScheduledDate().format(SCHEDULE_FORMAT));
        event.put("totalAmount", booking.getTotalAmount() == null
                ? "" : booking.getTotalAmount().toPlainString());
        event.put("estimatedCost", booking.getEstimatedCost() == null
                ? "" : booking.getEstimatedCost().toPlainString());
        event.put("platformFee", booking.getPlatformFee() == null
                ? "" : booking.getPlatformFee().toPlainString());
        event.put("gstAmount", booking.getGstAmount() == null
                ? "" : booking.getGstAmount().toPlainString());
        event.putAll(contactFor(booking.getCustomerId()));
        if (booking.getWorkerId() != null) {
            event.put("workerName", nameOf(booking.getWorkerId()));
        }
        return event;
    }

    /**
     * The customer's name and address, from auth-service.
     *
     * A failure here degrades the mail rather than losing the event: the consumer skips sending
     * when there is no address, and the in-app notification still lands.
     */
    private Map<String, Object> contactFor(Long userId) {
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("name", "");
        contact.put("email", "");
        contact.put("phone", "");
        if (userId == null) return contact;
        try {
            Map<String, Object> user = userServiceClient.getUserName(userId).getBody();
            if (user != null) {
                contact.put("name", String.valueOf(user.getOrDefault("name", "")));
                contact.put("email", String.valueOf(user.getOrDefault("email", "")));
                contact.put("phone", String.valueOf(user.getOrDefault("phone", "")));
            }
        } catch (Exception e) {
            log.warn("Could not resolve contact details for user {}: {}", userId, e.getMessage());
        }
        return contact;
    }

    private String nameOf(Long userId) {
        return String.valueOf(contactFor(userId).getOrDefault("name", ""));
    }

    private void publish(String topic, Map<String, Object> event, Long bookingId) {
        try {
            kafkaTemplate.send(topic, String.valueOf(bookingId), event);
            log.info("Published {} for booking {}", topic, bookingId);
        } catch (Exception e) {
            log.error("Could not publish {} for booking {}: {}", topic, bookingId, e.getMessage());
        }
    }
}
