package com.j2ee.tour.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executor for {@code @Async} work (currently the mail methods in
 * {@code EmailService}). The pool is intentionally small: email must never
 * compete with request threads or grow an unbounded queue. When the executor is
 * saturated the task is logged and dropped instead of blocking the caller.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    static final int CORE_POOL_SIZE = 2;
    static final int MAX_POOL_SIZE = 4;
    static final int QUEUE_CAPACITY = 50;

    @Bean(name = "mailTaskExecutor")
    public ThreadPoolTaskExecutor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("tour-mail-");
        executor.setRejectedExecutionHandler(AsyncConfig::dropRejectedMail);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return mailTaskExecutor();
    }

    private static void dropRejectedMail(Runnable task, ThreadPoolExecutor executor) {
        log.warn("Mail task rejected: executor is saturated (active={}, queued={}); "
                        + "dropping this email",
                executor.getActiveCount(), executor.getQueue().size());
    }
}
