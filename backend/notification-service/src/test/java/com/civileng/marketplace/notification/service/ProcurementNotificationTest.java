package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.notification.dto.NotificationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** procurement.events: one event, one in-app + email notification per named person. */
class ProcurementNotificationTest {

    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final KafkaNotificationConsumer consumer = new KafkaNotificationConsumer(mock(EmailService.class),
            mock(SmsService.class), mock(WhatsAppService.class), dispatcher, new ObjectMapper());

    @Test
    void everyRecipientIsToldInAppAndByEmailWithALinkBack() {
        Map<String, Object> pending = new HashMap<>();
        pending.put("userId", null);
        pending.put("email", "new@buildco.in");
        consumer.handleProcurementEvent(Map.of(
                "type", "PROCUREMENT_PO_APPROVAL_NEEDED", "title", "PO-00009 needs your approval",
                "message", "Total 403840.00 is above BuildCo's approval limit.",
                "referenceType", "PURCHASE_ORDER", "referenceId", 9, "link", "/procurement/orders/9",
                "recipients", List.of(Map.of("userId", 11, "email", "anita@buildco.in"), pending)));

        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(2)).dispatch(sent.capture());
        NotificationRequest known = sent.getAllValues().get(0);
        assertThat(known.userId()).isEqualTo(11L);
        assertThat(known.email()).isEqualTo("anita@buildco.in");
        assertThat(known.channels()).containsExactly("IN_APP", "EMAIL");
        assertThat(known.referenceType()).isEqualTo("PROCUREMENT_PURCHASE_ORDER");
        assertThat(known.referenceId()).isEqualTo(9L);
        assertThat(known.message()).contains("approval limit").contains("Open: /procurement/orders/9");
        assertThat(known.data()).contains("/procurement/orders/9");
        // Someone added by email who has not signed in yet has no account to notify in-app.
        assertThat(sent.getAllValues().get(1).channels()).containsExactly("EMAIL");
    }

    @Test
    void aMalformedEventIsLoggedNotThrown() {
        consumer.handleProcurementEvent(Map.of("recipients", "not a list"));
        verifyNoInteractions(dispatcher);
    }
}
