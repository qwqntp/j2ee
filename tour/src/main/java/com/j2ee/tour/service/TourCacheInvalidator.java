package com.j2ee.tour.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Clears stable tour content only after the surrounding transaction commits.
 *
 * <p>A rollback must not evict content that is still current. Without an active
 * synchronization, plain callers and unit tests clear immediately. Cache errors
 * are fire-and-forget: a committed write must not fail because Redis is down.</p>
 */
@Component
public class TourCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(TourCacheInvalidator.class);
    private static final String REGION = "tours";

    private final CacheManager cacheManager;

    public TourCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void invalidateAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clear();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                clear();
            }
        });
    }

    private void clear() {
        try {
            Cache cache = cacheManager.getCache(REGION);
            if (cache != null) {
                cache.clear();
            }
        } catch (RuntimeException exception) {
            // A cache failure must never turn a committed mutation into a 500.
            // Provider messages can carry connection URIs; log only the exception class.
            log.warn("Tour content cache clear failed: {}", exception.getClass().getName());
        }
    }
}
