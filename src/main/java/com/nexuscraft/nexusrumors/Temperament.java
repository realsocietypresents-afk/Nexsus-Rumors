package com.nexuscraft.nexusrumors;

import java.util.UUID;

/** How a villager gossips, fixed for life by its id: the sober ones barely change a story, the boasters stretch it. */
enum Temperament {
    SOBER("wary", 0.4, 1, 1.0,
            "I can only repeat what I was told: ", "I didn't see it myself, mind. ", "Take it with a pinch of salt: "),
    PLAIN("plain-spoken", 1.0, 1, 1.0,
            "Here's what I heard: ", "Word going round: ", "They say: "),
    GOSSIP("gossipy", 1.7, 2, 1.0,
            "Oh, you must hear this! ", "Don't tell anyone I said so. ", "Everyone is talking about it: "),
    BOASTER("boastful", 1.3, 1, 2.0,
            "I know this for a fact: ", "I was practically there. ", "You'll not believe the size of it: ");

    private final String adjective;
    private final double mutation;
    private final int tells;
    private final double numberBoost;
    private final String[] openers;

    Temperament(String adjective, double mutation, int tells, double numberBoost, String... openers) {
        this.adjective = adjective;
        this.mutation = mutation;
        this.tells = tells;
        this.numberBoost = numberBoost;
        this.openers = openers;
    }

    String adjective() {
        return adjective;
    }

    /** Multiplies the chance of changing a detail. */
    double mutation() {
        return mutation;
    }

    /** How many different rumors this villager has to share. */
    int tells() {
        return tells;
    }

    /** How much further than usual a stretched number is stretched. */
    double numberBoost() {
        return numberBoost;
    }

    String opener(long seed) {
        return openers[(int) Math.floorMod(seed, (long) openers.length)];
    }

    /** The same villager is always the same temperament: 25% sober, 40% plain-spoken, 20% gossipy, 15% boastful. */
    static Temperament of(UUID id) {
        int v = (int) Math.floorMod(Seeds.mix(id.getMostSignificantBits(), id.getLeastSignificantBits()), 100L);
        return v < 25 ? SOBER : v < 65 ? PLAIN : v < 85 ? GOSSIP : BOASTER;
    }
}
