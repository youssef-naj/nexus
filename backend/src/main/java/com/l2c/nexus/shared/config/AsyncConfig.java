package com.l2c.nexus.shared.config;

import com.l2c.nexus.shared.logging.SafeExceptionFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The executor behind @Async (account and invitation emails), ADR-0033. Two properties matter: when
 * its queue is full, surplus tasks are DROPPED with a warning instead of throwing into the request
 * that published them (registration must not fail because the mail queue is busy); and on shutdown
 * it finishes the queued work before the process exits.
 */
@Configuration
@EnableAsync
class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    static final class DropAndLogHandler implements RejectedExecutionHandler {

        private final AtomicLong dropped = new AtomicLong();

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            log.warn(
                    "Background task dropped because the queue is full ({} dropped so far)",
                    dropped.incrementAndGet());
        }

        long dropped() {
            return dropped.get();
        }
    }

    private final int core;
    private final int max;
    private final int queue;

    AsyncConfig(
            @Value("${spring.task.execution.pool.core-size:2}") int core,
            @Value("${spring.task.execution.pool.max-size:4}") int max,
            @Value("${spring.task.execution.pool.queue-capacity:100}") int queue) {
        this.core = core;
        this.max = max;
        this.queue = queue;
    }

    /**
     * Configured but not initialized: Spring initializes the bean; tests call initialize()
     * themselves.
     */
    static ThreadPoolTaskExecutor newExecutor(
            int core, int max, int queue, RejectedExecutionHandler handler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix("nexus-async-");
        executor.setRejectedExecutionHandler(handler);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }

    @Bean(name = "nexusAsyncExecutor")
    ThreadPoolTaskExecutor nexusAsyncExecutor() {
        return newExecutor(core, max, queue, new DropAndLogHandler());
    }

    @Override
    public Executor getAsyncExecutor() {
        return nexusAsyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error(
                        "Background task {} failed: {}",
                        method.getName(),
                        SafeExceptionFormatter.format(throwable));
    }
}
