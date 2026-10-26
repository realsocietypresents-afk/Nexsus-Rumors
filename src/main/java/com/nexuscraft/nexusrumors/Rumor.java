package com.nexuscraft.nexusrumors;

/** A telling of a fact that circulates in a region's villagers' talk. {@code hops} counts how many regions it has crossed. */
record Rumor(String factId, Telling telling, int hops) {

    Rumor hopped(Telling t) {
        return new Rumor(factId, t, hops + 1);
    }
}
