package com.civileng.marketplace.payment.controller;

import com.civileng.marketplace.payment.model.Payment;
import com.civileng.marketplace.payment.model.PaymentStatus;
import com.civileng.marketplace.payment.repository.PaymentRepository;
import com.civileng.marketplace.payment.service.UserNameResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Invoices for the admin console.
 *
 * <p>An invoice is not a second record beside a payment — it <em>is</em> the payment, presented as
 * a document: the same money, split into the lines a customer is billed for. Deriving it rather
 * than storing a copy means an invoice can never disagree with the payment it bills, which is the
 * failure mode that matters most in a finance screen.
 *
 * <p>The invoice number is derived from the payment id and the year it was created, so it is
 * stable across restarts and reproducible from the payment alone.
 */
@RestController
@RequestMapping("/api/v1/payments/admin/invoices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Invoices", description = "Invoice documents derived from payments")
public class AdminInvoiceController {

    private final PaymentRepository paymentRepository;
    private final UserNameResolver userNameResolver;

    @Value("${app.platform-fee-percentage:5.0}")
    private BigDecimal platformFeePercentage;

    @Value("${app.gst-percentage:18.0}")
    private BigDecimal gstPercentage;

    @GetMapping
    @Operation(summary = "List invoices, newest first")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        // Filtering happens over the fetched page only when a filter is actually supplied; the
        // unfiltered path — which is what the screen opens on — stays a plain indexed page query.
        boolean filtered = notBlank(status) || notBlank(search);
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt");

        List<Payment> payments;
        long totalElements;
        if (filtered) {
            List<Payment> all = paymentRepository.findAll(newestFirst).stream()
                    .filter(p -> matchesStatus(p, status))
                    .filter(p -> matchesSearch(p, search))
                    .toList();
            totalElements = all.size();
            int from = Math.min(page * size, all.size());
            payments = all.subList(from, Math.min(from + size, all.size()));
        } else {
            Page<Payment> pageResult = paymentRepository.findAll(PageRequest.of(page, size, newestFirst));
            payments = pageResult.getContent();
            totalElements = pageResult.getTotalElements();
        }

        List<Map<String, Object>> invoices = payments.stream().map(this::toInvoice).toList();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        return ResponseEntity.ok(Map.of(
                "success", true, "data", invoices,
                "page", page, "size", size,
                "totalElements", totalElements,
                "totalPages", totalPages));
    }

    /**
     * Raises an invoice against a booking.
     *
     * <p>Because an invoice <em>is</em> a payment here, raising one means creating the payment it
     * bills — unpaid, with no PSP order behind it. The customer settles it later through Checkout,
     * which creates the Razorpay order at that point; billing and collecting stay separate steps so
     * an admin can invoice work that has not been paid for yet.
     *
     * <p>Fees are stored on the row rather than recomputed at render time, so a later change to the
     * platform's fee percentages cannot silently restate invoices that were already issued.
     */
    @PostMapping
    @Operation(summary = "Raise an invoice against a booking")
    public ResponseEntity<Map<String, Object>> raise(@RequestBody Map<String, Object> request) {
        Long bookingId = requireLong(request, "bookingId");
        Long customerId = requireLong(request, "customerId");
        BigDecimal subtotal = requireAmount(request, "amount");

        BigDecimal platformFee = percentageOf(subtotal, platformFeePercentage);
        BigDecimal gst = percentageOf(subtotal.add(platformFee), gstPercentage);

        Payment invoice = Payment.builder()
                .paymentCode(generateInvoiceCode())
                .bookingId(bookingId)
                .userId(customerId)
                .amount(subtotal)
                .platformFee(platformFee)
                .gstAmount(gst)
                .totalAmount(subtotal.add(platformFee).add(gst))
                .paymentStatus(PaymentStatus.PENDING)
                .description(asText(request.get("description")))
                .build();

        Payment saved = paymentRepository.save(invoice);
        log.info("Invoice {} raised for booking {} ({})",
                invoiceNumber(saved), bookingId, saved.getTotalAmount());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "data", toInvoice(saved)));
    }

    private static BigDecimal percentageOf(BigDecimal base, BigDecimal percentage) {
        return base.multiply(percentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static String generateInvoiceCode() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "INV-" + timestamp + "-" + String.format("%04d", new Random().nextInt(10000));
    }

    private static String asText(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static Long requireLong(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    /** Same ₹1 floor the PSP enforces, so a raised invoice is always one that can be paid. */
    private static BigDecimal requireAmount(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        if (amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Amount must be at least ₹1 (100 paise)");
        }
        return amount;
    }

    @GetMapping("/summary")
    @Operation(summary = "Totals across all invoices, for the screen's header cards")
    public ResponseEntity<Map<String, Object>> summary() {
        List<Payment> all = paymentRepository.findAll();

        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        long paidCount = 0, pendingCount = 0, refundedCount = 0, failedCount = 0;

        for (Payment p : all) {
            BigDecimal total = nonNull(p.getTotalAmount());
            billed = billed.add(total);
            switch (p.getPaymentStatus()) {
                case COMPLETED, CAPTURED -> { collected = collected.add(total); paidCount++; }
                case REFUNDED, PARTIALLY_REFUNDED -> {
                    // A partial refund was still collected in full first — counting only the
                    // remainder would make collected and refunded double-discount each other.
                    collected = collected.add(total);
                    refunded = refunded.add(refundOf(p));
                    refundedCount++;
                }
                case FAILED, CANCELLED -> failedCount++;
                default -> { outstanding = outstanding.add(total); pendingCount++; }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalBilled", billed.doubleValue());
        data.put("totalCollected", collected.doubleValue());
        data.put("totalOutstanding", outstanding.doubleValue());
        data.put("totalRefunded", refunded.doubleValue());
        data.put("invoiceCount", (long) all.size());
        data.put("paidCount", paidCount);
        data.put("pendingCount", pendingCount);
        data.put("refundedCount", refundedCount);
        data.put("failedCount", failedCount);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/{invoiceNumber}")
    @Operation(summary = "One invoice, with its billing lines")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String invoiceNumber) {
        Payment payment = findByInvoiceNumber(invoiceNumber);
        if (payment == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false, "message", "No invoice " + invoiceNumber));
        }
        Map<String, Object> invoice = new HashMap<>(toInvoice(payment));
        invoice.put("lines", lines(payment));
        invoice.put("razorpayPaymentId", payment.getRazorpayPaymentId());
        invoice.put("refundReason", payment.getRefundReason());
        invoice.put("failureReason", payment.getFailureReason());
        return ResponseEntity.ok(Map.of("success", true, "data", invoice));
    }

    // ------------------------------------------------------------------ mapping

    /**
     * The payment as a billing document. Amounts are doubles because this is a display projection
     * and the JSON is read by a browser; the authoritative values stay {@code BigDecimal} in the
     * payment row this is derived from.
     */
    private Map<String, Object> toInvoice(Payment p) {
        var customer = userNameResolver.resolve(p.getUserId());
        Map<String, Object> invoice = new HashMap<>();
        invoice.put("invoiceNumber", invoiceNumber(p));
        invoice.put("paymentCode", p.getPaymentCode());
        invoice.put("bookingId", p.getBookingId());
        invoice.put("bookingCode", "BK-" + p.getBookingId());
        invoice.put("customerId", p.getUserId());
        invoice.put("customerName", customer.name());
        invoice.put("customerEmail", customer.email());
        invoice.put("subtotal", nonNull(p.getAmount()).doubleValue());
        invoice.put("platformFee", nonNull(p.getPlatformFee()).doubleValue());
        invoice.put("gstAmount", nonNull(p.getGstAmount()).doubleValue());
        invoice.put("total", nonNull(p.getTotalAmount()).doubleValue());
        invoice.put("refundAmount", refundOf(p).doubleValue());
        invoice.put("currency", p.getCurrency() == null ? "INR" : p.getCurrency());
        invoice.put("status", invoiceStatus(p));
        invoice.put("paymentStatus", p.getPaymentStatus().name());
        invoice.put("paymentMethod", p.getPaymentMethod() == null ? null : p.getPaymentMethod().name());
        invoice.put("description", p.getDescription());
        invoice.put("issuedAt", p.getCreatedAt() == null ? null : p.getCreatedAt().toString());
        invoice.put("paidAt", p.getPaidAt() == null ? null : p.getPaidAt().toString());
        invoice.put("refundedAt", p.getRefundedAt() == null ? null : p.getRefundedAt().toString());
        return invoice;
    }

    /** The document's line items, in the order they are billed. */
    private List<Map<String, Object>> lines(Payment p) {
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(line(p.getDescription() == null || p.getDescription().isBlank()
                ? "Service charge" : p.getDescription(), nonNull(p.getAmount())));
        if (nonNull(p.getPlatformFee()).signum() != 0) {
            lines.add(line("Platform fee", nonNull(p.getPlatformFee())));
        }
        if (nonNull(p.getGstAmount()).signum() != 0) {
            lines.add(line("GST", nonNull(p.getGstAmount())));
        }
        if (refundOf(p).signum() != 0) {
            lines.add(line("Refund", refundOf(p).negate()));
        }
        return lines;
    }

    private static Map<String, Object> line(String label, BigDecimal amount) {
        Map<String, Object> line = new HashMap<>();
        line.put("label", label);
        line.put("amount", amount.doubleValue());
        return line;
    }

    /**
     * PAID / PENDING / REFUNDED / CANCELLED — the payment's state read as a document's state.
     * Overdue is deliberately absent: nothing on the platform carries payment terms yet, so an
     * "overdue" badge would be an invention rather than a fact about the booking.
     */
    private static String invoiceStatus(Payment p) {
        return switch (p.getPaymentStatus()) {
            case COMPLETED, CAPTURED -> "PAID";
            case REFUNDED, PARTIALLY_REFUNDED -> "REFUNDED";
            case FAILED, CANCELLED -> "CANCELLED";
            default -> "PENDING";
        };
    }

    static String invoiceNumber(Payment p) {
        int year = (p.getCreatedAt() == null ? LocalDateTime.now() : p.getCreatedAt()).getYear();
        return String.format("INV-%d-%06d", year, p.getId());
    }

    /**
     * Reverses {@link #invoiceNumber}: the id is the only part that identifies the payment, and the
     * year is checked so a mistyped number cannot resolve to somebody else's invoice.
     */
    private Payment findByInvoiceNumber(String invoiceNumber) {
        if (invoiceNumber == null) return null;
        String[] parts = invoiceNumber.trim().toUpperCase(Locale.ROOT).split("-");
        if (parts.length != 3 || !"INV".equals(parts[0])) return null;
        try {
            Payment payment = paymentRepository.findById(Long.parseLong(parts[2])).orElse(null);
            return payment != null && invoiceNumber(payment).equalsIgnoreCase(invoiceNumber.trim())
                    ? payment : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ filters

    private static boolean matchesStatus(Payment p, String status) {
        return !notBlank(status) || invoiceStatus(p).equalsIgnoreCase(status.trim());
    }

    private boolean matchesSearch(Payment p, String search) {
        if (!notBlank(search)) return true;
        String needle = search.trim().toLowerCase(Locale.ROOT);
        var customer = userNameResolver.resolve(p.getUserId());
        return contains(invoiceNumber(p), needle)
                || contains(p.getPaymentCode(), needle)
                || contains("BK-" + p.getBookingId(), needle)
                || contains(customer.name(), needle)
                || contains(customer.email(), needle);
    }

    private static boolean contains(String value, String lowercaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowercaseNeedle);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** A refunded payment with no recorded refund amount was refunded in full. */
    private static BigDecimal refundOf(Payment p) {
        if (p.getRefundAmount() != null) return p.getRefundAmount();
        return p.getPaymentStatus() == PaymentStatus.REFUNDED ? nonNull(p.getTotalAmount()) : BigDecimal.ZERO;
    }
}
