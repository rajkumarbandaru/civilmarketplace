package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.notification.model.EmailStatus;
import com.civileng.marketplace.notification.model.NotificationChannel;
import com.civileng.marketplace.tenant.common.integration.IntegrationCapability;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional SMS through Twilio.
 *
 * <p>{@code app.sms.provider} selects the provider: {@code twilio} for real delivery,
 * {@code log} to write the message to the service log and keep it inside the cluster
 * (the useful default when no Twilio account is provisioned). A {@code twilio} setting
 * with unconfigured credentials degrades to logging rather than breaking OTP login.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmsService {

    /**
     * What the Notifications screen files these under. SMS has no templates, so every row shares
     * one source key rather than inventing a per-message one.
     */
    private static final String SOURCE_KEY = "sms";

    private final TwilioGateway twilioGateway;
    private final PhoneNumbers phoneNumbers;
    private final EmailLogService deliveryLog;

    @Value("${app.sms.provider:log}")
    private String provider;

    @Value("${app.sms.sender-id:CIVILENG}")
    private String senderId;

    @Value("${app.sms.twilio.phone-number:}")
    private String twilioFrom;

    public void sendOtpSms(String phone, String otp) {
        TwilioGateway.Route route = route();
        deliver(phone, route, "Your " + route.senderLabel() + " verification code is " + otp
                + ". It expires in 5 minutes. Do not share it with anyone.");
    }

    @Async
    public void sendBookingConfirmation(String phone, String bookingCode) {
        TwilioGateway.Route route = route();
        deliver(phone, route, route.senderLabel() + ": your booking " + bookingCode + " is confirmed.");
    }

    @Async
    public void sendPaymentReceipt(String phone, String amount, String paymentCode) {
        TwilioGateway.Route route = route();
        deliver(phone, route, route.senderLabel() + ": payment of " + amount + " received. Ref "
                + paymentCode + ".");
    }

    /**
     * Dispatches an arbitrary SMS body. Never throws: a failed notification must not roll
     * back or fail the business action that triggered it.
     */
    public void send(String phone, String message) {
        deliver(phone, route(), message);
    }

    /**
     * The current tenant's SMS account — its own DLT-registered sender, never the platform's. The
     * operator tenant alone sends on the {@code app.sms.*} account.
     */
    private TwilioGateway.Route route() {
        return twilioGateway.route(IntegrationCapability.SMS, "twilio".equalsIgnoreCase(provider),
                twilioFrom, "senderId", senderId);
    }

    private void deliver(String phone, TwilioGateway.Route route, String message) {
        String to = phoneNumbers.toE164(phone);
        if (to == null) {
            // Nothing is recorded: with no usable number there is no recipient to file it under,
            // and a log of messages to nobody is noise rather than history.
            log.warn("[SMS] skipped - unusable phone number {}", PhoneNumbers.mask(phone));
            return;
        }

        if (!route.sendable()) {
            // The body is not logged: an OTP in a log line is a credential anyone with log access
            // can use. The delivery log row (tenant schema, admin-only) keeps it for support.
            log.info("[SMS:log] to={} ({})", PhoneNumbers.mask(to), route.skipReason());
            deliveryLog.record(NotificationChannel.SMS, SOURCE_KEY, to, message, message,
                    EmailStatus.SKIPPED, "log", null, route.skipReason());
            return;
        }

        try {
            String sid = twilioGateway.send(route.account(), route.from(), to, message);
            log.info("[SMS:twilio] sent to={} sid={}", PhoneNumbers.mask(to), sid);
            // SENT, not DELIVERED: Twilio has accepted it. Handset delivery would need its own
            // status callback, which is not wired up.
            deliveryLog.record(NotificationChannel.SMS, SOURCE_KEY, to, message, message,
                    EmailStatus.SENT, "twilio", sid, null);
        } catch (Exception e) {
            log.error("[SMS:twilio] failed to send to {}: {}", PhoneNumbers.mask(to), e.getMessage());
            deliveryLog.record(NotificationChannel.SMS, SOURCE_KEY, to, message, message,
                    EmailStatus.FAILED, "twilio", null, e.getMessage());
        }
    }
}
