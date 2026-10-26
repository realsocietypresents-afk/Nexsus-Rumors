package com.nexuscraft.nexusrumors;

import java.util.UUID;

/**
 * Something that really happened. Rumors are tellings of facts; a fact is the truth every verdict is judged against.
 * {@code number} is the price (market), the death toll or how many times it was seen (danger); 0 means "no number".
 */
final class Fact {
    final String id;
    final Kind kind;
    final Region region;
    final String biome;
    final String detail;
    final String extra;
    int number;
    final String subject;
    final UUID subjectId;
    final long createdAt;
    long expiresAt;

    Fact(String id, Kind kind, Region region, String biome, String detail, String extra, int number,
         String subject, UUID subjectId, long createdAt, long expiresAt) {
        this.id = id;
        this.kind = kind;
        this.region = region;
        this.biome = biome == null ? "" : biome;
        this.detail = detail == null ? "" : detail;
        this.extra = extra == null ? "" : extra;
        this.number = Math.max(0, number);
        this.subject = subject == null ? "" : subject;
        this.subjectId = subjectId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** The exact account of this fact, before anyone has retold it. */
    Telling exact() {
        return new Telling(region, biome, detail, extra, number, subject, 0);
    }

    boolean expired(long now) {
        return now > expiresAt;
    }

    /** Same thing happening again in the same place, so it counts towards one story rather than a second one. */
    boolean sameStory(Kind k, Region r, String d, String e) {
        return kind == k && region.equals(r) && detail.equals(d) && extra.equals(e);
    }
}
