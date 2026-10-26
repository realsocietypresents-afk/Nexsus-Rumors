package com.nexuscraft.nexusrumors;

/**
 * One account of a fact, as somebody tells it. The truth is a {@link Fact}; a telling can be exact or can have
 * drifted: a neighbouring region, a stretched number, a name forgotten, a vaguer time. {@code biome} is only
 * ever filled in while the region is still the right one, so a drifted telling stops naming the landscape.
 * {@code skew} is how much vaguer the time has become (0 = exact).
 */
record Telling(Region region, String biome, String detail, String extra, int number, String subject, int skew) {

    static final int MAX_SKEW = 3;

    Telling {
        biome = biome == null ? "" : biome;
        detail = detail == null ? "" : detail;
        extra = extra == null ? "" : extra;
        subject = subject == null ? "" : subject;
        number = Math.max(0, number);
        skew = Math.max(0, Math.min(MAX_SKEW, skew));
    }

    Telling withRegion(Region r) {
        return new Telling(r, "", detail, extra, number, subject, skew);
    }

    Telling withNumber(int n) {
        return new Telling(region, biome, detail, extra, n, subject, skew);
    }

    Telling withoutSubject() {
        return new Telling(region, biome, detail, extra, number, "", skew);
    }

    Telling vaguer() {
        return new Telling(region, biome, detail, extra, number, subject, skew + 1);
    }
}
