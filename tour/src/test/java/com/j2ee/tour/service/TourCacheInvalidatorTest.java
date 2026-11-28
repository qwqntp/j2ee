package com.j2ee.tour.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TourCacheInvalidatorTest {

    private static final String SECRET_URI = "redis://svc:super-secret-token@cache.example:6379";

    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final TourCacheInvalidator invalidator = new TourCacheInvalidator(cacheManager);

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(TourCacheInvalidator.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void clearSynchronization() {
        logger.detachAppender(appender);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private String logs() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void clearsOnlyAfterCommit() {
        when(cacheManager.getCache("tours")).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();

        invalidator.invalidateAfterCommit();

        verify(cache, never()).clear();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(cache, times(1)).clear();
    }

    @Test
    void rollbackDoesNotClear() {
        when(cacheManager.getCache("tours")).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();

        invalidator.invalidateAfterCommit();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(cache, never()).clear();
    }

    @Test
    void withoutTransactionClearsImmediately() {
        when(cacheManager.getCache("tours")).thenReturn(cache);

        invalidator.invalidateAfterCommit();

        verify(cache, times(1)).clear();
    }

    @Test
    void clearFailureDoesNotEscape() {
        when(cacheManager.getCache("tours")).thenReturn(cache);
        doThrow(new RuntimeException("cache unavailable")).when(cache).clear();

        assertThatCode(() -> invalidator.invalidateAfterCommit()).doesNotThrowAnyException();

        TransactionSynchronizationManager.initSynchronization();
        invalidator.invalidateAfterCommit();
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
        verify(cache, times(2)).clear();
    }

    @Test
    void cacheLookupFailureDoesNotEscape() {
        when(cacheManager.getCache("tours")).thenThrow(new IllegalStateException("cache unavailable"));

        assertThatCode(() -> invalidator.invalidateAfterCommit()).doesNotThrowAnyException();
    }

    @Test
    void immediateClearFailureWarningOmitsExceptionText() {
        when(cacheManager.getCache("tours")).thenReturn(cache);
        doThrow(new IllegalStateException("connect failed via " + SECRET_URI)).when(cache).clear();

        invalidator.invalidateAfterCommit();

        assertThat(logs()).contains("Tour content cache clear failed")
                .contains(IllegalStateException.class.getName())
                .doesNotContain(SECRET_URI, "super-secret-token", "connect failed via");
    }

    @Test
    void afterCommitClearFailureWarningOmitsExceptionText() {
        when(cacheManager.getCache("tours")).thenReturn(cache);
        doThrow(new IllegalStateException("connect failed via " + SECRET_URI)).when(cache).clear();
        TransactionSynchronizationManager.initSynchronization();

        invalidator.invalidateAfterCommit();
        assertThat(logs()).isEmpty();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        assertThat(logs()).contains("Tour content cache clear failed")
                .contains(IllegalStateException.class.getName())
                .doesNotContain(SECRET_URI, "super-secret-token", "connect failed via");
    }

    @Test
    void missingCacheIsHarmless() {
        when(cacheManager.getCache("tours")).thenReturn(null);

        assertThatCode(() -> invalidator.invalidateAfterCommit()).doesNotThrowAnyException();

        TransactionSynchronizationManager.initSynchronization();
        assertThatCode(() -> invalidator.invalidateAfterCommit()).doesNotThrowAnyException();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(cacheManager, times(2)).getCache("tours");
        assertThat(logs()).doesNotContain("Tour content cache clear failed");
    }
}
