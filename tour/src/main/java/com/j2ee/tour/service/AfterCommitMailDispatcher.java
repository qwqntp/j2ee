package com.j2ee.tour.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers a mail task until the surrounding transaction has committed.
 *
 * <p>Email is fire-and-forget, so sending before commit can deliver a link whose
 * token row was rolled back, or announce a booking that no longer exists. When
 * a transaction is active the task is registered as an {@code afterCommit}
 * callback; without a transaction it runs immediately so non-transactional
 * callers and unit tests keep their existing behaviour.</p>
 */
@Component
public class AfterCommitMailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitMailDispatcher.class);

    public void dispatch(Runnable mailTask) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mailTask.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    mailTask.run();
                } catch (RuntimeException exception) {
                    // A commit callback must never turn a successful commit into a failure.
                    log.warn("Mail task failed after commit: {}", exception.getMessage());
                }
            }
        });
    }
}
