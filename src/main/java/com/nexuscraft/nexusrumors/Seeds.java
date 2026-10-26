package com.nexuscraft.nexusrumors;

/** A small, stable hash for deriving the same "random" choices again and again (same villager, same story, same words). */
final class Seeds {

    private Seeds() {
    }

    static long mix(long... parts) {
        long h = 0x9E3779B97F4A7C15L;
        for (long p : parts) {
            h ^= p + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 31;
        }
        return h;
    }

    static long of(String s) {
        return s == null ? 0L : s.hashCode();
    }
}
