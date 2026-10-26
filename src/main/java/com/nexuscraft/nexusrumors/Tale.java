package com.nexuscraft.nexusrumors;

import java.util.Locale;

/** Puts a telling into words. */
final class Tale {

    private static final String[] SKEW = {"", "The other day, ", "A while back, ", "Ages ago, "};

    private Tale() {
    }

    /** "in the dark forest, just north of spawn" or "just north of spawn". */
    static String place(Telling t) {
        return t.biome().isEmpty() ? Grid.where(t.region()) : "in " + Grid.label(t.region(), t.biome());
    }

    /** A full sentence about the telling. */
    static String sentence(Kind kind, Telling t) {
        String body = switch (kind) {
            case MARKET -> market(t);
            case DANGER -> danger(t);
            case SIGHTING -> sighting(t);
        };
        String lead = SKEW[Math.max(0, Math.min(SKEW.length - 1, t.skew()))];
        if (lead.isEmpty()) {
            return body;
        }
        return lead + Character.toLowerCase(body.charAt(0)) + body.substring(1);
    }

    private static String market(Telling t) {
        String who = t.extra().isEmpty() ? "A trader" : article(t.extra()) + " " + t.extra();
        String what = t.detail().isEmpty() ? "something rare" : t.detail();
        String price = t.number() <= 0 ? "" : t.number() == 1 ? " for a single emerald" : " for " + t.number() + " emeralds";
        return who + " " + place(t) + (t.biome().isEmpty() ? "" : ",") + " is selling " + what + price + ".";
    }

    private static String danger(Telling t) {
        String what = t.detail().isEmpty() ? "something nasty" : t.detail();
        if (t.extra().equals("spawn")) {
            String times = t.number() > 1 ? " " + t.number() + " times" : "";
            return capital(what) + " has been spotted" + times + " " + place(t) + ".";
        }
        if (t.extra().equals("note")) {
            return "Folk warn of " + what + " " + place(t) + ".";
        }
        if (t.number() > 1) {
            return t.number() + " people have died to " + what + " " + place(t) + ".";
        }
        String who = t.subject().isEmpty() ? "Someone" : t.subject();
        return who + " died to " + what + " " + place(t) + ".";
    }

    private static String sighting(Telling t) {
        String who = t.subject().isEmpty() ? "A traveler" : t.subject();
        return who + " was seen " + place(t) + ".";
    }

    static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String article(String noun) {
        return "aeiou".indexOf(Character.toLowerCase(noun.charAt(0))) >= 0 ? "An" : "A";
    }

    /** A creature or item name for sentences: "WARDEN" becomes "a Warden", "ENCHANTED_BOOK" "an Enchanted Book". */
    static String nameWithArticle(String enumName) {
        String n = title(enumName);
        return (article(n).equals("An") ? "an " : "a ") + n;
    }

    static String title(String enumName) {
        StringBuilder out = new StringBuilder();
        for (String w : enumName.toLowerCase(Locale.ROOT).split("[_ ]+")) {
            if (w.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** 2 becomes "II"; level 1 and anything out of range add nothing. */
    static String roman(int level) {
        return level >= 2 && level < ROMAN.length ? ROMAN[level] : "";
    }
}
