package com.l2c.nexus.organization.application;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Turns a name into a readable label such as "acme-corp". Never used for access decisions. */
@Component
class SlugGenerator {

    static final int MAX_BASE_LENGTH = 40;
    private static final String FALLBACK = "org";

    String baseFrom(String name) {
        String ascii =
                Normalizer.normalize(name, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "")
                        .toLowerCase(Locale.ROOT);
        String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_BASE_LENGTH) {
            slug = slug.substring(0, MAX_BASE_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? FALLBACK : slug;
    }
}
