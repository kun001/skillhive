package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.shared.exception.DomainTooManyRequestsException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PublishExtractionBudgetTest {

    @Test
    void rejectsAnAcquireThatWouldExceedTheInFlightBudgetUntilTheHolderReleases() {
        PublishExtractionBudget budget = new PublishExtractionBudget(100);

        PublishExtractionBudget.Permit first = budget.acquire(60);
        assertThatThrownBy(() -> budget.acquire(50))
                .isInstanceOfSatisfying(DomainTooManyRequestsException.class, error -> {
                    assertThat(error.statusCode()).isEqualTo(429);
                    assertThat(error.messageCode()).isEqualTo(PublishExtractionBudget.BUSY);
                });

        first.close();
        PublishExtractionBudget.Permit second = budget.acquire(50);
        second.close();
        assertThatCode(() -> budget.acquire(100).close()).doesNotThrowAnyException();
    }

    @Test
    void disabledBudgetNeverRejects() {
        PublishExtractionBudget budget = new PublishExtractionBudget(0);

        assertThatCode(() -> {
            PublishExtractionBudget.Permit first = budget.acquire(500);
            PublishExtractionBudget.Permit second = budget.acquire(500);
            first.close();
            second.close();
        }).doesNotThrowAnyException();
    }

    @Test
    void aConcurrentAcquireWaitsForCapacityOnlyByFailingFast() throws Exception {
        PublishExtractionBudget budget = new PublishExtractionBudget(100);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread holder = new Thread(() -> {
            try (PublishExtractionBudget.Permit ignored = budget.acquire(80)) {
                holding.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release timed out");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failure.set(ex);
            }
        });
        holder.start();
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> budget.acquire(30)).isInstanceOf(DomainTooManyRequestsException.class);

        release.countDown();
        holder.join(5000);
        assertThat(failure.get()).isNull();
        assertThatCode(() -> budget.acquire(30).close()).doesNotThrowAnyException();
    }
}
