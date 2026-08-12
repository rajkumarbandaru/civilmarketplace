package com.civileng.marketplace.notification.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.email.from-address}")
    private String fromAddress;

    @Value("${app.email.from-name}")
    private String fromName;

    @Async
    public void sendEmail(String to, String subject, String templateName,
                          Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            String htmlContent = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent to {} with subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendOtpEmail(String to, String otp) {
        sendEmail(to, "Your OTP Code - Civil Engineering Marketplace",
                "otp-template",
                Map.of("otp", otp, "expiryMinutes", 5));
    }

    @Async
    public void sendWelcomeEmail(String to, String name) {
        sendEmail(to, "Welcome to Civil Engineering Marketplace!",
                "welcome-template",
                Map.of("name", name));
    }

    @Async
    public void sendBookingConfirmation(String to, String name, String bookingCode) {
        sendEmail(to, "Booking Confirmed - " + bookingCode,
                "booking-confirmed-template",
                Map.of("name", name, "bookingCode", bookingCode));
    }

    @Async
    public void sendPaymentReceipt(String to, String name, String amount,
                                   String paymentCode) {
        sendEmail(to, "Payment Received - " + paymentCode,
                "payment-received-template",
                Map.of("name", name, "amount", amount, "paymentCode", paymentCode));
    }
}
