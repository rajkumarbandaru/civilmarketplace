package com.civileng.marketplace.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends WhatsApp messages through the Twilio WhatsApp channel.
 *
 * <p>Same Messages API as {@link SmsService}; the channel is selected purely by the
 * {@code whatsapp:} prefix on both addresses. {@code app.whatsapp.provider} accepts
 * {@code twilio} or {@code log}, and an unconfigured Twilio account degrades to logging.
 *
 * <p>Note on Twilio's rules: outside a 24-hour customer-initiated window, WhatsApp only
 * delivers pre-approved templates. Free-form bodies here will be rejected by Twilio in
 * that case — the error is logged, not thrown, so the calling flow is unaffected.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsAppService {

    private static final String CHANNEL_PREFIX = "whatsapp:";

    private final TwilioGateway twilioGateway;
    private final PhoneNumbers phoneNumbers;

    @Value("${app.whatsapp.provider:log}")
    private String provider;

    @Value("${app.whatsapp.sender-name:Civil Engineer Marketplace}")
    private String senderName;

    @Value("${app.whatsapp.twilio.from-number:}")
    private String twilioFrom;

    @Async
    public void sendOtp(String phone, String otp) {
        send(phone, "*" + senderName + "*\nYour verification code is *" + otp
                + "*. It expires in 5 minutes. Do not share it with anyone.");
    }

    @Async
    public void sendBookingConfirmation(String phone, String name, String bookingCode) {
        send(phone, "*" + senderName + "*\nHi " + name + ", your booking *" + bookingCode
                + "* is confirmed. We'll notify you of any updates.");
    }

    @Async
    public void sendPaymentReceipt(String phone, String name, String amount, String paymentCode) {
        send(phone, "*" + senderName + "*\nHi " + name + ", we've received your payment of *"
                + amount + "*. Reference: " + paymentCode + ".");
    }

    /** Dispatches an arbitrary WhatsApp body. Never throws. */
    public void send(String phone, String message) {
        String to = phoneNumbers.toE164(phone);
        if (to == null) {
            log.warn("[WhatsApp] skipped - unusable phone number {}", PhoneNumbers.mask(phone));
            return;
        }

        if (!"twilio".equalsIgnoreCase(provider) || !twilioGateway.isConfigured()) {
            log.info("[WhatsApp:log] to={} message={}", PhoneNumbers.mask(to), message);
            return;
        }

        try {
            String sid = twilioGateway.send(prefixed(twilioFrom), prefixed(to), message);
            log.info("[WhatsApp:twilio] sent to={} sid={}", PhoneNumbers.mask(to), sid);
        } catch (Exception e) {
            log.error("[WhatsApp:twilio] failed to send to {}: {}", PhoneNumbers.mask(to), e.getMessage());
        }
    }

    /** Twilio addresses the WhatsApp channel by prefix; applying it twice is rejected. */
    private static String prefixed(String number) {
        return number.startsWith(CHANNEL_PREFIX) ? number : CHANNEL_PREFIX + number;
    }
}
