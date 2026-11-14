package com.j2ee.tour.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.j2ee.tour.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class AsyncConfigTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(AsyncConfig.class);
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
    void mailExecutorIsBounded() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().mailTaskExecutor();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isEqualTo(50);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("tour-mail-");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void saturatedExecutorDropsTheMailTaskWithAWarning() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().mailTaskExecutor();
        try {
            executor.getThreadPoolExecutor().getRejectedExecutionHandler()
                    .rejectedExecution(() -> { }, executor.getThreadPoolExecutor());

            assertThat(logs()).contains("dropping");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void emailMethodsRunOffTheCallerThread() {
        JavaMailSender sender = mock(JavaMailSender.class);
        AtomicReference<String> sendingThread = new AtomicReference<>();
        doAnswer(invocation -> {
            sendingThread.set(Thread.currentThread().getName());
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AsyncConfig.class, TestAsyncConfig.class);
            context.registerBean(JavaMailSender.class, () -> sender);
            context.registerBean(EmailService.class, () -> new EmailService(
                    context.getBeanProvider(JavaMailSender.class),
                    "smtp.example.com",
                    "from@example.com",
                    "http://localhost:3000"));
            context.refresh();

            String callerThread = Thread.currentThread().getName();
            context.getBean(EmailService.class)
                    .sendEmailVerification("user@example.com", "verify-token");

            verify(sender, timeout(5_000)).send(any(SimpleMailMessage.class));
            assertThat(sendingThread.get())
                    .startsWith("tour-mail-")
                    .isNotEqualTo(callerThread);
        }
    }

    @Configuration
    @EnableAsync
    static class TestAsyncConfig {
    }
}
