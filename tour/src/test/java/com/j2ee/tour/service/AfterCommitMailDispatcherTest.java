package com.j2ee.tour.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitMailDispatcherTest {

    private final AfterCommitMailDispatcher dispatcher = new AfterCommitMailDispatcher();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void runsImmediatelyWhenNoTransactionIsActive() {
        AtomicInteger sends = new AtomicInteger();

        dispatcher.dispatch(sends::incrementAndGet);

        assertThat(sends).hasValue(1);
    }

    @Test
    void defersTheSendUntilTheTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger sends = new AtomicInteger();

        dispatcher.dispatch(sends::incrementAndGet);

        assertThat(sends).hasValue(0);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(sends).hasValue(1);
    }

    @Test
    void sendsNothingWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger sends = new AtomicInteger();

        dispatcher.dispatch(sends::incrementAndGet);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(sends).hasValue(0);
    }
}
