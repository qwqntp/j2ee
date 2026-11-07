package com.j2ee.tour.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void missingMailConfigurationIsSkippedWithoutError() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        EmailService service = new EmailService(provider, "", "from@example.com",
                "http://localhost:3000");

        assertDoesNotThrow(() ->
                service.sendPasswordReset("user@example.com", "reset-token"));
        verifyNoInteractions(provider);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mailSendFailureDoesNotEscape() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        doThrow(new MailSendException("smtp down"))
                .when(sender).send(any(SimpleMailMessage.class));
        EmailService service = new EmailService(provider, "smtp.example.com",
                "from@example.com", "http://localhost:3000");

        assertDoesNotThrow(() -> service.sendBookingCancellation(
                "user@example.com", "BK-20261107-0001"));
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
    }
}
