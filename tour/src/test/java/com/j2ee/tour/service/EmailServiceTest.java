package com.j2ee.tour.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void frontendUrlWithTrailingSlashesNeverProducesADoubleSlash() {
        assertThat(EmailService.buildLink("http://localhost:3000", "/verify-email?token=t"))
                .isEqualTo("http://localhost:3000/verify-email?token=t");
        assertThat(EmailService.buildLink("http://localhost:3000/", "/verify-email?token=t"))
                .isEqualTo("http://localhost:3000/verify-email?token=t");
        assertThat(EmailService.buildLink("http://localhost:3000///", "/reset-password?token=t"))
                .isEqualTo("http://localhost:3000/reset-password?token=t");
        assertThat(EmailService.buildLink("https://example.com/app/", "verify-email?token=t"))
                .isEqualTo("https://example.com/app/verify-email?token=t");
    }

    @Test
    @SuppressWarnings("unchecked")
    void trailingSlashFrontendUrlLogsASingleSlashLink() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        EmailService service = new EmailService(provider, "", "from@example.com",
                "http://localhost:3000/");

        service.sendEmailVerification("user@example.com", "verify-token");

        assertTrue(logs().contains("http://localhost:3000/verify-email?token=verify-token"));
        assertFalse(logs().contains("//verify-email"));
    }

    @Test
    void smtpTimeoutsAndUtf8EncodingAreConfigured() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            assertNotNull(input, "application.properties must be on the test classpath");
            properties.load(input);
        }

        assertThat(smtpTimeoutMillis(properties, "connectiontimeout")).isBetween(5_000, 10_000);
        assertThat(smtpTimeoutMillis(properties, "timeout")).isBetween(5_000, 10_000);
        assertThat(smtpTimeoutMillis(properties, "writetimeout")).isBetween(5_000, 10_000);
        assertThat(properties.getProperty("spring.mail.default-encoding")).isEqualTo("UTF-8");
    }

    @Test
    void vietnameseSubjectAndBodySurviveMimeEncoding() throws Exception {
        CapturingMailSender sender = new CapturingMailSender();
        sender.setHost("smtp.example.com");
        sender.setDefaultEncoding("UTF-8");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("from@example.com");
        message.setTo("nguoi.dung@example.com");
        message.setSubject("Xác nhận đặt tour Hành Trình");
        message.setText("Chào bạn, chuyến đi Đà Nẵng đã được xác nhận.");
        sender.send(message);

        assertThat(sender.captured).isNotNull();
        ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        sender.captured.writeTo(serialized);
        String raw = serialized.toString(StandardCharsets.UTF_8);
        assertThat(raw).as(raw).containsIgnoringCase("charset=UTF-8");

        MimeMessage reparsed = new MimeMessage(
                Session.getInstance(new Properties()),
                new ByteArrayInputStream(serialized.toByteArray()));

        assertThat(reparsed.getSubject()).isEqualTo("Xác nhận đặt tour Hành Trình");
        assertThat(reparsed.getContent())
                .isEqualTo("Chào bạn, chuyến đi Đà Nẵng đã được xác nhận.");
    }

    private static int smtpTimeoutMillis(Properties properties, String name) {
        String raw = properties.getProperty("spring.mail.properties.mail.smtp." + name);
        assertThat(raw).as(name).isNotNull();
        Matcher matcher = Pattern.compile(":([0-9]+)}").matcher(raw);
        assertThat(matcher.find()).as("default value in %s", raw).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private static class CapturingMailSender extends JavaMailSenderImpl {

        private MimeMessage captured;

        @Override
        protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
            this.captured = mimeMessages[0];
        }
    }
}
