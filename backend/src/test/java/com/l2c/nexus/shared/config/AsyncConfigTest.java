package com.l2c.nexus.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    @Test
    void aFullQueueDropsSurplusTasksInsteadOfThrowingIntoTheCaller() {
        AsyncConfig.DropAndLogHandler handler = new AsyncConfig.DropAndLogHandler();
        ThreadPoolTaskExecutor executor = AsyncConfig.newExecutor(1, 1, 1, handler);
        executor.initialize();
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger ran = new AtomicInteger();
        Runnable blocked =
                () -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    ran.incrementAndGet();
                };
        try {
            executor.execute(blocked); // running
            executor.execute(blocked); // queued

            assertThatCode(
                            () -> {
                                executor.execute(blocked); // dropped
                                executor.execute(blocked); // dropped
                            })
                    .doesNotThrowAnyException();
            assertThat(handler.dropped()).isEqualTo(2);
        } finally {
            release.countDown();
            executor.shutdown();
        }

        assertThat(ran.get()).as("only the accepted tasks ran").isEqualTo(2);
    }

    @Test
    void queuedWorkFinishesBeforeShutdownReturns() {
        ThreadPoolTaskExecutor executor =
                AsyncConfig.newExecutor(1, 1, 5, new AsyncConfig.DropAndLogHandler());
        executor.initialize();
        AtomicInteger finished = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            executor.execute(
                    () -> {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        finished.incrementAndGet();
                    });
        }

        executor.shutdown();

        assertThat(finished.get()).isEqualTo(3);
    }
}
