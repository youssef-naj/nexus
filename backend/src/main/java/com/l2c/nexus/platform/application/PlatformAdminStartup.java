package com.l2c.nexus.platform.application;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Applies nexus.platform.admin-emails when the application starts. Addresses are never logged. */
@Component
class PlatformAdminStartup {

    static final int MAX_ADMINISTRATORS = 20;

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminStartup.class);

    private final PlatformAdminSyncService sync;
    private final String configured;

    PlatformAdminStartup(
            PlatformAdminSyncService sync,
            @Value("${nexus.platform.admin-emails:}") String configured) {
        this.sync = sync;
        this.configured = configured;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        PlatformAdminSyncService.SyncResult result = sync.sync(parse(configured));
        log.info(
                "Platform administrators synchronized: {} granted, {} revoked, {} listed but not"
                        + " registered and verified yet",
                result.granted(),
                result.revoked(),
                result.missing());
    }

    /** Comma-separated; trimmed, lower-cased, de-duplicated; at most MAX_ADMINISTRATORS. */
    static Set<String> parse(String value) {
        Set<String> emails =
                Arrays.stream(value.split(","))
                        .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
                        .filter(entry -> !entry.isEmpty())
                        .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (emails.size() > MAX_ADMINISTRATORS) {
            throw new IllegalStateException(
                    "nexus.platform.admin-emails lists more than "
                            + MAX_ADMINISTRATORS
                            + " addresses");
        }
        return emails;
    }
}
