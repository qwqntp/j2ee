package com.j2ee.tour.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(appender);
    }

    private String logs() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingMailConfigurationLogsVerificationLinkForManualUse() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        EmailService service = new EmailService(provider, "", "from@example.com",
                "http://localhost:3000");

        assertDoesNotThrow(() ->
                service.sendEmailVerification("user@example.com", "verify-token"));

        verifyNoInteractions(provider);
        assertTrue(logs().contains("spring.mail.host"));
        assertTrue(logs().contains(
                "http://localhost:3000/verify-email?token=verify-token"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingMailConfigurationLogsPasswordResetLinkForManualUse() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        EmailService service = new EmailService(provider, "", "from@example.com",
                "http://localhost:3000");

        assertDoesNotThrow(() ->
                service.sendPasswordReset("user@example.com", "reset-token"));

        verifyNoInteractions(provider);
        assertTrue(logs().contains("spring.mail.host"));
        assertTrue(logs().contains(
                "http://localhost:3000/reset-password?token=reset-token"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void blankRecipientIsSkippedWithAWarningAndNoToken() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        EmailService service = new EmailService(provider, "smtp.example.com",
                "from@example.com", "http://localhost:3000");

        assertDoesNotThrow(() -> service.sendPasswordReset(" ", "reset-token"));

        verifyNoInteractions(provider);
        assertTrue(logs().contains("no recipient address"));
        assertFalse(logs().contains("reset-token"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingSenderBeanIsSkippedWithAWarningAndNoToken() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        EmailService service = new EmailService(provider, "smtp.example.com",
                "from@example.com", "http://localhost:3000");

        assertDoesNotThrow(() ->
                service.sendEmailVerification("user@example.com", "verify-token"));

        assertTrue(logs().contains("no JavaMailSender bean"));
        assertFalse(logs().contains("verify-token"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mailSendFailureLogsTheReasonWithoutTheToken() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        doThrow(new MailSendException("smtp down"))
                .when(sender).send(any(SimpleMailMessage.class));
        EmailService service = new EmailService(provider, "smtp.example.com",
                "from@example.com", "http://localhost:3000");

        assertDoesNotThrow(() -> service.sendEmailVerification(
                "user@example.com", "verify-token"));

        assertTrue(logs().contains("Failed to send email"));
        assertTrue(logs().contains("smtp down"));
        assertFalse(logs().contains("verify-token"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void configuredMailIsSent() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        EmailService service = new EmailService(provider, "smtp.example.com",
                "from@example.com", "http://localhost:3000");

        service.sendEmailVerification("user@example.com", "verify-token");

        verify(sender).send(any(SimpleMailMessage.class));
        assertFalse(logs().contains("verify-token"));
    }
}
