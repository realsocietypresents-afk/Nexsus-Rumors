package com.nexuscraft.nexusrumors;

/** What turned out when a player went and looked. */
enum Verdict {
    /** The place and the numbers were right. */
    TRUE,
    /** The place was right, but a number had been stretched. */
    EXAGGERATED,
    /** Nothing like that was there. */
    FALSE,
    /** It is over, or nobody remembers it any more: no verdict. */
    STALE;

    /**
     * Judges a player's belief against what really happened. {@code fact} may be null (forgotten).
     * A rumor about an event that has ended is STALE if it had the right place and FALSE if it never did.
     */
    static Verdict judge(Fact fact, Telling believed, long now) {
        if (fact == null) {
            return STALE;
        }
        boolean placeRight = fact.region.equals(believed.region());
        if (!placeRight) {
            return FALSE;
        }
        if (fact.expired(now)) {
            return STALE;
        }
        return fact.number > 0 && fact.number != believed.number() ? EXAGGERATED : TRUE;
    }
}
