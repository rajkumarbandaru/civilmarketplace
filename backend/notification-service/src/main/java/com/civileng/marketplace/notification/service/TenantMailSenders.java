package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.tenant.common.integration.ResolvedIntegration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One JavaMail sender per tenant SMTP relay, built from the tenant's own integration and reused
 * until its settings change — the platform's auto-configured {@code JavaMailSender} is the
 * platform's relay and is never handed a tenant's mail.
 */
@Component
public class TenantMailSenders {

    private final Map<String, Entry> senders = new ConcurrentHashMap<>();

    public JavaMailSender forTenant(ResolvedIntegration smtp) {
        int fingerprint = Objects.hash(smtp.setting("host"), smtp.setting("port"),
                smtp.setting("username"), smtp.secret("password"));
        Entry cached = senders.get(smtp.tenantKey());
        if (cached != null && cached.fingerprint == fingerprint) {
            return cached.sender;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.setting("host"));
        sender.setPort(parsePort(smtp.setting("port")));
        sender.setUsername(smtp.setting("username"));
        sender.setPassword(smtp.secret("password"));
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        senders.put(smtp.tenantKey(), new Entry(fingerprint, sender));
        return sender;
    }

    private static int parsePort(String port) {
        try {
            return port == null ? 587 : Integer.parseInt(port.trim());
        } catch (NumberFormatException e) {
            return 587;
        }
    }

    private record Entry(int fingerprint, JavaMailSender sender) {
    }
}
