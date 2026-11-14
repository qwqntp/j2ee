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

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String from;
    private final String frontendUrl;

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
        String link = frontendUrl + "/reset-password?token=" + token;
        send(to, "Reset your tour password",
                "Use this link to reset your password:\n"
                        + link
                        + "\n\nThis link expires in 30 minutes and can only be used once.",
                link);
    }

    @Async
    public void sendEmailVerification(String to, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        send(to, "Verify your tour email",
                "Use this link to verify your email address:\n"
                        + link
                        + "\n\nThis link expires in 2 days and can only be used once.",
                link);
    }

    @Async
    public void sendBookingConfirmation(String to, String bookingCode, BigDecimal totalAmount) {
        send(to, "Booking confirmed - " + bookingCode,
                "Your booking " + bookingCode + " has been created."
                        + "\nTotal: " + totalAmount
                        + "\n\nThank you for booking with us.",
                null);
    }

    @Async
    public void sendBookingCancellation(String to, String bookingCode) {
        send(to, "Booking cancelled - " + bookingCode,
                "Your booking " + bookingCode + " has been cancelled."
                        + "\n\nIf this was not expected, please contact support.",
                null);
    }

    private void send(String to, String subject, String body, String manualLink) {
        if (to == null || to.isBlank()) {
            log.warn("Skipped email '{}': no recipient address is set", subject);
            return;
        }
        if (mailHost == null || mailHost.isBlank()) {
            log.warn("Skipped email '{}': spring.mail.host is not configured "
                    + "(set SPRING_MAIL_HOST or MAIL_HOST)", subject);
            logManualLink(subject, manualLink);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Skipped email '{}': no JavaMailSender bean is available; "
                    + "check spring.mail.* configuration", subject);
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
            log.warn("Failed to send email '{}' to {}: {}",
                    subject, to, exception.getMessage());
        }
    }

    /**
     * Dev fallback only: when no SMTP host is configured the token link cannot be delivered.
     * Logging it here lets a developer finish registration, email verification, or a password
     * reset by hand. The link contains a one-time token, so it is never logged when SMTP is
     * configured or when sending fails for another reason.
     */
    private void logManualLink(String subject, String manualLink) {
        if (manualLink == null || manualLink.isBlank()) {
            return;
        }
        log.warn("No SMTP configured; '{}' was not sent. Dev-only manual link: {}",
                subject, manualLink);
    }
}
