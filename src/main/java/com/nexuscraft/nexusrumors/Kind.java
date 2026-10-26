package com.nexuscraft.nexusrumors;

import java.util.Locale;

/** What a rumor is about. */
enum Kind {
    MARKET("Market"),
    DANGER("Danger"),
    SIGHTING("Whereabouts");

    private final String label;

    Kind(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    static Kind parse(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        for (Kind k : values()) {
            if (k.name().toLowerCase(Locale.ROOT).equals(t) || k.label.toLowerCase(Locale.ROOT).equals(t)) {
                return k;
            }
        }
        return null;
    }
}
