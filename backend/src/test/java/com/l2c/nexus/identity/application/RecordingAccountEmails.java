package com.l2c.nexus.identity.application;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Test double that records emails per recipient. Emails are sent asynchronously, so we wait. */
public class RecordingAccountEmails implements AccountEmails {

    public enum Kind {
        VERIFICATION,
        ALREADY_REGISTERED
    }

    public record Sent(Kind kind, String to, String link) {
        public String token() {
            return link.substring(link.indexOf("token=") + "token=".length());
        }
    }

    private final Map<String, BlockingQueue<Sent>> byRecipient = new ConcurrentHashMap<>();

    private BlockingQueue<Sent> queue(String to) {
        return byRecipient.computeIfAbsent(
                to.toLowerCase(Locale.ROOT), key -> new LinkedBlockingQueue<>());
    }

    @Override
    public void sendVerificationEmail(String toEmail, String displayName, String link) {
        queue(toEmail).add(new Sent(Kind.VERIFICATION, toEmail, link));
    }

    @Override
    public void sendAlreadyRegisteredEmail(String toEmail) {
        queue(toEmail).add(new Sent(Kind.ALREADY_REGISTERED, toEmail, null));
    }

    public Sent awaitNext(String to) {
        try {
            Sent sent = queue(to).poll(5, TimeUnit.SECONDS);
            if (sent == null) {
                throw new AssertionError("No email was sent to " + to);
            }
            return sent;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for an email", e);
        }
    }

    /** Waits up to the given time and returns the next email, or null if none arrives. */
    public Sent pollWithin(String to, long millis) {
        try {
            return queue(to).poll(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for an email", e);
        }
    }
}
