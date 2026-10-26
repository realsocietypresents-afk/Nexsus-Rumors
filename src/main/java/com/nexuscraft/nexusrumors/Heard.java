package com.nexuscraft.nexusrumors;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything one player has been told about one fact. Hearing it from several people lets you compare: the
 * {@link #consensus} is the most common account in each detail, and the closer a teller is to the source
 * (fewer hops) the more they count when the vote is tied.
 */
final class Heard {

    /** One person's account: who, how far from the event they were, when, and what they said. */
    record Version(String teller, int hops, long at, Telling telling) {
    }

    final String factId;
    final Kind kind;
    long lastAt;
    final List<Version> versions = new ArrayList<>();

    Heard(String factId, Kind kind) {
        this.factId = factId;
        this.kind = kind;
    }

    /** Adds an account. The same teller saying the same thing again changes nothing; beyond {@code max} the oldest goes. */
    boolean add(Version v, int max) {
        lastAt = Math.max(lastAt, v.at());
        for (Version old : versions) {
            if (old.teller().equals(v.teller()) && old.telling().equals(v.telling())) {
                return false;
            }
        }
        versions.add(v);
        while (versions.size() > Math.max(1, max)) {
            versions.remove(0);
        }
        return true;
    }

    /** The account most tellers agree on, detail by detail. */
    Telling consensus() {
        Region region = pick(Region.class, versions, v -> v.telling().region());
        String biome = "";
        for (Version v : versions) {
            if (v.telling().region().equals(region) && !v.telling().biome().isEmpty()) {
                biome = v.telling().biome();
                break;
            }
        }
        String detail = pick(String.class, versions, v -> v.telling().detail());
        String extra = pick(String.class, versions, v -> v.telling().extra());
        Integer number = pick(Integer.class, versions, v -> v.telling().number());
        String subject = pick(String.class, versions, v -> v.telling().subject());
        Integer skew = pick(Integer.class, versions, v -> v.telling().skew());
        return new Telling(region, biome, detail, extra, number, subject, skew);
    }

    /** How many of the accounts match the consensus about both place and number. */
    int agreeing() {
        Telling c = consensus();
        int n = 0;
        for (Version v : versions) {
            if (v.telling().region().equals(c.region()) && v.telling().number() == c.number()) {
                n++;
            }
        }
        return n;
    }

    /** Whether everybody told it the same way. */
    boolean unanimous() {
        for (Version v : versions) {
            if (!v.telling().equals(versions.get(0).telling())) {
                return false;
            }
        }
        return true;
    }

    private interface Field<T> {
        T of(Version v);
    }

    /** The most common value; on a tie, the one told by someone closer to the event, then the one heard first. */
    private static <T> T pick(Class<T> type, List<Version> list, Field<T> field) {
        T best = null;
        int bestCount = -1;
        int bestHops = Integer.MAX_VALUE;
        for (Version v : list) {
            T value = field.of(v);
            int count = 0;
            int nearest = Integer.MAX_VALUE;
            for (Version w : list) {
                if (field.of(w).equals(value)) {
                    count++;
                    nearest = Math.min(nearest, w.hops());
                }
            }
            if (count > bestCount || (count == bestCount && nearest < bestHops)) {
                best = value;
                bestCount = count;
                bestHops = nearest;
            }
        }
        return type.cast(best);
    }
}
