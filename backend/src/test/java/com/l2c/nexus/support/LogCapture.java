package com.l2c.nexus.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/** Records everything the application logs while it is open, so tests can search it. */
public final class LogCapture implements AutoCloseable {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    public LogCapture() {
        appender.start();
        root.addAppender(appender);
    }

    public List<ILoggingEvent> events() {
        // Logback appends while holding the appender's lock, so copy while holding it too
        synchronized (appender) {
            return List.copyOf(appender.list);
        }
    }

    /** Every message, plus the full text of any exception attached to it. */
    public String text() {
        return events().stream()
                .map(
                        event ->
                                event.getFormattedMessage()
                                        + (event.getThrowableProxy() == null
                                                ? ""
                                                : "\n"
                                                        + ThrowableProxyUtil.asString(
                                                                event.getThrowableProxy())))
                .collect(Collectors.joining("\n"));
    }

    /** Waits for a log line that another thread writes (for example a failed background email). */
    public boolean awaitContains(String fragment, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (text().contains(fragment)) {
                return true;
            }
            Thread.sleep(50);
        }
        return text().contains(fragment);
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
