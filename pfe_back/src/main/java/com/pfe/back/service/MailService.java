package com.pfe.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (application onboarding notifications, etc.).
 *
 * <p>Runs {@link Async} so the request thread never blocks on SMTP. When
 * {@code app.mail.enabled=false} (the default) or no {@link JavaMailSender} is
 * configured, emails are logged instead of sent — so local dev works without a
 * mail server. Failures are swallowed and logged: a bad SMTP config must never
 * break the business workflow that triggered the email.
 */
@Service
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public MailService(
            org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:PFE DevSecOps <no-reply@pfe.local>}") String from) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.enabled = enabled;
        this.from = from;
    }

    /**
     * Sends a plain-text email. Safe to call with a null/blank recipient (skipped).
     * Never throws — a mail failure is logged, not propagated.
     */
    @Async
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.debug("Skipping email '{}' — no recipient", subject);
            return;
        }
        if (!enabled || mailSender == null) {
            log.info("[MAIL DISABLED] Would send to {} | subject: {}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} | subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {} | subject: {}: {}", to, subject, e.getMessage());
        }
    }
}

