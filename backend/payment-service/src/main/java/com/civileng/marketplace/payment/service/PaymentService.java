package com.civileng.marketplace.payment.service;

import com.civileng.marketplace.payment.model.Payment;
import com.civileng.marketplace.payment.model.PaymentStatus;
import com.civileng.marketplace.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RazorpayGateway razorpayGateway;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * Razorpay rejects an order below 100 paise (₹1), so the call is refused here rather than
     * spending a round trip to be told the same thing.
     */
    private static final BigDecimal MIN_AMOUNT = BigDecimal.ONE;

    @Transactional
    public Payment createPaymentOrder(Long bookingId, Long userId, BigDecimal amount) {
        Payment existingPayment = paymentRepository
                .findFirstByBookingIdAndPaymentStatusOrderByCreatedAtDesc(
                        bookingId, PaymentStatus.PENDING)
                .orElse(null);

        if (existingPayment != null) {
            existingPayment.setRazorpayKeyId(razorpayGateway.current().keyId());
            return existingPayment;
        }

        return newPaymentOrder(bookingId, userId, amount);
    }

    /**
     * Funding order for an escrow hold. Unlike {@link #createPaymentOrder} this never reuses a
     * pending payment on the same booking: a booking can carry several milestone holds, and
     * sharing one payment row between them would make the hold-to-payment link ambiguous and
     * fund several holds off a single capture.
     */
    @Transactional
    public Payment createEscrowFundingOrder(Long bookingId, Long userId, BigDecimal amount) {
        return newPaymentOrder(bookingId, userId, amount);
    }

    /**
     * A payment for something other than a booking — a supplier invoice, for procurement-service.
     * The calling service decides who may pay what; this only takes the money. Asking again while
     * one for the same reference is pending returns that one, so a double click is one payment.
     */
    public Payment createReferenceOrder(String referenceType, Long referenceId, Long userId, BigDecimal amount,
                                        String description) {
        if (referenceType == null || referenceType.isBlank() || "BOOKING".equals(referenceType) || referenceId == null) {
            throw new IllegalArgumentException("referenceType and referenceId are required");
        }
        List<Payment> earlier = paymentRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(referenceType, referenceId);
        if (earlier.stream().anyMatch(p -> p.getPaymentStatus() == PaymentStatus.COMPLETED)) {
            throw new IllegalArgumentException("This has already been paid");
        }
        Optional<Payment> open = earlier.stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.PROCESSING && p.getAmount().compareTo(amount) == 0)
                .findFirst();
        if (open.isPresent()) {
            open.get().setRazorpayKeyId(razorpayGateway.current().keyId());
            return open.get();
        }
        return newPaymentOrder(null, referenceType, referenceId, userId, amount, description);
    }

    private Payment newPaymentOrder(Long bookingId, Long userId, BigDecimal amount) {
        return newPaymentOrder(bookingId, "BOOKING", bookingId, userId, amount, null);
    }

    private Payment newPaymentOrder(Long bookingId, String referenceType, Long referenceId, Long userId,
                                    BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException(
                    "Amount must be at least ₹1 (100 paise)");
        }

        // Resolved before anything is written: a tenant with no merchant account gets a 409 naming
        // the missing integration, not a FAILED payment row it can do nothing about.
        RazorpayGateway.Credentials merchant = razorpayGateway.current();

        Payment payment = Payment.builder()
                .paymentCode(generatePaymentCode())
                .bookingId(bookingId)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .description(description)
                .userId(userId)
                .amount(amount)
                .totalAmount(amount)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount.multiply(BigDecimal.valueOf(100))
                    .longValue());
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", payment.getPaymentCode());
            orderRequest.put("payment_capture", 1);

            Order razorpayOrder = merchant.client().orders.create(orderRequest);
            payment.setRazorpayOrderId(razorpayOrder.get("id"));
            payment.setPaymentStatus(PaymentStatus.PROCESSING);

            log.info("Razorpay order created: {} for booking {}",
                    razorpayOrder.get("id"), bookingId);
        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage());
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
        }

        Payment saved = paymentRepository.save(payment);
        saved.setRazorpayKeyId(merchant.keyId());

        // Map.of rejects null values, and razorpayOrderId is null whenever the PSP call above
        // failed — which threw an NPE out of the *success* path and turned every PSP outage into
        // a 500 with no payment row visible to the caller. A HashMap tolerates the null so the
        // event still carries the failure.
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("paymentId", saved.getId());
        event.put("bookingId", bookingId);
        event.put("referenceType", referenceType);
        event.put("referenceId", referenceId);
        event.put("amount", amount);
        event.put("razorpayOrderId", saved.getRazorpayOrderId());
        event.put("status", saved.getPaymentStatus().name());
        kafkaTemplate.send("payment.created", event);

        return saved;
    }

    @Transactional
    public Payment verifyAndCompletePayment(String razorpayOrderId,
                                             String razorpayPaymentId,
                                             String razorpaySignature) {
        if (!razorpayGateway.current()
                .checkoutSignatureMatches(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            throw new IllegalArgumentException("Invalid payment signature");
        }

        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            // Checkout and the webhook both report the same payment; the second changes nothing.
            return payment;
        }
        payment.setRazorpaySignature(razorpaySignature);
        return complete(payment, razorpayPaymentId);
    }

    @Transactional
    public Payment processRefund(Long paymentId, BigDecimal amount, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        if (payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Payment is not completed");
        }

        payment.setRefundAmount(amount);
        payment.setRefundReason(reason);
        payment.setRefundedAt(LocalDateTime.now());
        payment.setPaymentStatus(PaymentStatus.REFUNDED);

        Payment saved = paymentRepository.save(payment);
        log.info("Refund processed for payment: {} amount: {}", paymentId, amount);

        kafkaTemplate.send("payment.refunded", Map.of(
                "paymentId", saved.getId(),
                "bookingId", saved.getBookingId(),
                "amount", amount,
                "reason", reason
        ));

        return saved;
    }

    public Payment getPaymentByBooking(Long bookingId) {
        return paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for booking"));
    }

    public Payment getPaymentByRazorpayOrder(String orderId) {
        return paymentRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
    }

    /**
     * Applies a Razorpay webhook that has already been matched to its tenant and had its signature
     * checked against that tenant's webhook secret ({@code RazorpayWebhookController}). Runs bound to
     * that tenant, so the order lookup below can only ever see that tenant's payments.
     */
    @Transactional
    /**
     * Marks a payment completed and announces it once. The event names what was paid for —
     * a booking, or {@code referenceType}/{@code referenceId} for anything else — and who paid.
     */
    private Payment complete(Payment payment, String razorpayPaymentId) {
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setPaymentStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        log.info("Payment completed: {} for {} {}", saved.getId(), saved.getReferenceType(),
                saved.getReferenceId() != null ? saved.getReferenceId() : saved.getBookingId());

        // A HashMap, not Map.of: bookingId is null for a payment that is not for a booking.
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("paymentId", saved.getId());
        event.put("bookingId", saved.getBookingId());
        event.put("userId", saved.getUserId());
        event.put("paymentCode", saved.getPaymentCode());
        event.put("amount", saved.getTotalAmount());
        event.put("referenceType", saved.getReferenceType());
        event.put("referenceId", saved.getReferenceId());
        event.put("razorpayPaymentId", razorpayPaymentId);
        kafkaTemplate.send("payment.completed", event);
        if (saved.getBookingId() != null) {
            // Lets an escrow hold funded by this payment move to HELD.
            eventPublisher.publishEvent(new com.civileng.marketplace.payment.event.PaymentCompletedEvent(
                    saved.getId(), saved.getBookingId()));
        }
        return saved;
    }

    public void applyWebhookEvent(String payload) {
        try {
            JSONObject event = new JSONObject(payload);
            String eventType = event.optString("event");

            if ("payment.captured".equals(eventType)) {
                JSONObject paymentEntity = event.getJSONObject("payload")
                        .getJSONObject("payment").getJSONObject("entity");
                String orderId = paymentEntity.optString("order_id");
                String paymentId = paymentEntity.optString("id");

                Payment payment = paymentRepository
                        .findByRazorpayOrderId(orderId)
                        .orElse(null);
                if (payment != null && payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
                    complete(payment, paymentId);
                }
            }

            log.info("Webhook processed: {}", eventType);

        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage());
            throw new IllegalArgumentException("Webhook processing failed");
        }
    }

    private String generatePaymentCode() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%04d", new Random().nextInt(10000));
        return "PAY-" + timestamp + "-" + random;
    }
}
