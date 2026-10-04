package com.l2c.nexus.team.application;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test double that records invitation emails per recipient. Delivery is asynchronous, so we wait.
 */
public class RecordingInvitationEmails implements InvitationEmails {

    public record Sent(
            String to, String organizationName, String inviterName, String role, String link) {
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
    public void sendInvitation(
            String toEmail,
            String organizationName,
            String inviterName,
            String role,
            String acceptLink) {
        queue(toEmail).add(new Sent(toEmail, organizationName, inviterName, role, acceptLink));
    }

    public Sent awaitNext(String to) {
        Sent sent = pollWithin(to, 5000);
        if (sent == null) {
            throw new AssertionError("No invitation email was sent to " + to);
        }
        return sent;
    }

    public Sent pollWithin(String to, long millis) {
        try {
            return queue(to).poll(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for an email", e);
        }
    }
}
