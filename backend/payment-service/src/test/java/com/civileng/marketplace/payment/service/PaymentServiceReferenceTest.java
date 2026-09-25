package com.civileng.marketplace.payment.service;

import com.civileng.marketplace.payment.event.PaymentCompletedEvent;
import com.civileng.marketplace.payment.model.Payment;
import com.civileng.marketplace.payment.model.PaymentStatus;
import com.civileng.marketplace.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Payments that are not for a booking: a supplier invoice paid through procurement-service. */
class PaymentServiceReferenceTest {

    private static final RazorpayGateway.Credentials MERCHANT =
            new RazorpayGateway.Credentials("platform", "rzp_test_key", "test_secret", "hook_secret");

    private final PaymentRepository repo = mock(PaymentRepository.class);
    private final RazorpayGateway gateway = mock(RazorpayGateway.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private PaymentService service;

    @BeforeEach
    void setUp() {
        when(gateway.current()).thenReturn(MERCHANT);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new PaymentService(repo, gateway, kafka, events);
    }

    private static Payment invoicePayment(PaymentStatus status) {
        return Payment.builder().id(900L).paymentCode("PAY1").referenceType("SUPPLIER_INVOICE").referenceId(40L)
                .userId(11L).amount(new BigDecimal("96000.00")).totalAmount(new BigDecimal("96000.00"))
                .razorpayOrderId("order_X").paymentStatus(status).build();
    }

    @Test
    void aPendingOrderForTheSameReferenceIsReusedAndAPaidOneRefused() {
        when(repo.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc("SUPPLIER_INVOICE", 40L))
                .thenReturn(List.of(invoicePayment(PaymentStatus.PROCESSING)));
        Payment again = service.createReferenceOrder("SUPPLIER_INVOICE", 40L, 11L, new BigDecimal("96000.00"), "Invoice");
        assertThat(again.getId()).isEqualTo(900L);
        assertThat(again.getRazorpayKeyId()).isEqualTo("rzp_test_key");
        verify(repo, never()).save(any());

        when(repo.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc("SUPPLIER_INVOICE", 40L))
                .thenReturn(List.of(invoicePayment(PaymentStatus.COMPLETED)));
        assertThatThrownBy(() -> service.createReferenceOrder("SUPPLIER_INVOICE", 40L, 11L, new BigDecimal("96000.00"), "x"))
                .hasMessageContaining("already been paid");
        assertThatThrownBy(() -> service.createReferenceOrder("BOOKING", 40L, 11L, BigDecimal.TEN, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void completingAnnouncesWhatWasPaidForAndWhoPaidOnce() {
        Payment p = invoicePayment(PaymentStatus.PROCESSING);
        when(repo.findByRazorpayOrderId("order_X")).thenReturn(Optional.of(p));
        String signature = RazorpayGateway.Credentials.hmacSha256Hex("order_X|pay_ABC", "test_secret");

        Payment done = service.verifyAndCompletePayment("order_X", "pay_ABC", signature);
        assertThat(done.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("payment.completed"), event.capture());
        Map<String, Object> body = (Map<String, Object>) event.getValue();
        assertThat(body).containsEntry("referenceType", "SUPPLIER_INVOICE").containsEntry("referenceId", 40L)
                .containsEntry("userId", 11L).containsEntry("razorpayPaymentId", "pay_ABC").containsEntry("bookingId", null);
        // Not a booking: no escrow hold to fund.
        verify(events, never()).publishEvent(any(PaymentCompletedEvent.class));

        // Checkout and the webhook both report it; the second announces nothing.
        service.verifyAndCompletePayment("order_X", "pay_ABC", signature);
        service.applyWebhookEvent("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"order_id\":\"order_X\",\"id\":\"pay_ABC\"}}}}");
        verify(kafka, times(1)).send(eq("payment.completed"), any());
    }

    @Test
    void aBookingPaymentStillFundsItsEscrow() {
        Payment p = Payment.builder().id(5L).paymentCode("PAY5").bookingId(77L).referenceType("BOOKING").referenceId(77L)
                .userId(7L).amount(BigDecimal.TEN).totalAmount(BigDecimal.TEN).razorpayOrderId("order_B")
                .paymentStatus(PaymentStatus.PROCESSING).build();
        when(repo.findByRazorpayOrderId("order_B")).thenReturn(Optional.of(p));
        service.applyWebhookEvent("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"order_id\":\"order_B\",\"id\":\"pay_B\"}}}}");
        verify(events).publishEvent(new PaymentCompletedEvent(5L, 77L));
    }
}
