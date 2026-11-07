package com.j2ee.tour.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String from;
    private final String frontendUrl;
    private final AtomicBoolean missingConfigurationLogged = new AtomicBoolean(false);

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${spring.mail.host:}") String mailHost,
                        @Value("${app.mail.from:no-reply@tour.local}") String from,
                        @Value("${app.web.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @Async
    public void sendPasswordReset(String to, String token) {
        send(to, "Reset your tour password",
                "Use this link to reset your password:\n"
                        + frontendUrl + "/reset-password?token=" + token
                        + "\n\nThis link expires in 30 minutes and can only be used once.");
    }

    @Async
    public void sendEmailVerification(String to, String token) {
        send(to, "Verify your tour email",
                "Use this link to verify your email address:\n"
                        + frontendUrl + "/verify-email?token=" + token
                        + "\n\nThis link expires in 2 days and can only be used once.");
    }

    @Async
    public void sendBookingConfirmation(String to, String bookingCode, BigDecimal totalAmount) {
        send(to, "Booking confirmed - " + bookingCode,
                "Your booking " + bookingCode + " has been created."
                        + "\nTotal: " + totalAmount
                        + "\n\nThank you for booking with us.");
    }

    @Async
    public void sendBookingCancellation(String to, String bookingCode) {
        send(to, "Booking cancelled - " + bookingCode,
                "Your booking " + bookingCode + " has been cancelled."
                        + "\n\nIf this was not expected, please contact support.");
    }

    private void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }
        if (mailHost == null || mailHost.isBlank()) {
            logMissingConfiguration();
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            logMissingConfiguration();
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception exception) {
            // Email delivery must never break registration, booking, or cancellation.
            log.warn("Failed to send email to {}: {}", to, exception.getMessage());
        }
    }

    private void logMissingConfiguration() {
        if (missingConfigurationLogged.compareAndSet(false, true)) {
            log.warn("Mail is not configured (set SPRING_MAIL_HOST/MAIL_HOST); email sending is skipped");
        }
    }
}
