package com.nexuscraft.nexusrumors;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** How a story changes in the telling. Pure: all the randomness comes in as a {@link Random}. */
final class Gossip {

    private static final int[][] AROUND = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    private enum Change { DRIFT, NUMBER, FORGET, VAGUE }

    private Gossip() {
    }

    /** Whether the telling has a number that {@link #mutate} can stretch. */
    static boolean canChangeNumber(Kind kind, Telling t) {
        return kind == Kind.DANGER ? t.number() >= 1 : kind == Kind.MARKET && t.number() >= 2;
    }

    /**
     * One thing goes wrong in the telling: the place slides to a neighbouring region, the number is stretched
     * (a price shrinks, a death toll grows), the name is forgotten, or the time gets vaguer. Always changes something.
     */
    static Telling mutate(Telling t, Kind kind, Random rnd, double numberBoost) {
        List<Change> bag = new ArrayList<>();
        add(bag, Change.DRIFT, 3);
        if (canChangeNumber(kind, t)) {
            add(bag, Change.NUMBER, 3);
        }
        if (!t.subject().isEmpty()) {
            add(bag, Change.FORGET, 2);
        }
        if (t.skew() < Telling.MAX_SKEW) {
            add(bag, Change.VAGUE, 2);
        }
        Change pick = bag.get(rnd.nextInt(bag.size()));
        return switch (pick) {
            case DRIFT -> {
                int[] d = AROUND[rnd.nextInt(AROUND.length)];
                yield t.withRegion(t.region().step(d[0], d[1]));
            }
            case NUMBER -> t.withNumber(stretch(kind, t.number(), rnd, numberBoost));
            case FORGET -> t.withoutSubject();
            case VAGUE -> t.vaguer();
        };
    }

    private static void add(List<Change> bag, Change c, int weight) {
        for (int i = 0; i < weight; i++) {
            bag.add(c);
        }
    }

    /** A price remembered as cheaper than it was, a death toll as bigger. Never returns the same number. */
    static int stretch(Kind kind, int n, Random rnd, double boost) {
        if (kind == Kind.MARKET) {
            double f = 0.3 + rnd.nextDouble() * 0.5;
            f = Math.max(0.1, 1.0 - (1.0 - f) * boost);
            return Math.max(1, Math.min(n - 1, (int) Math.round(n * f)));
        }
        double f = 1.5 + rnd.nextDouble() * 1.5;
        f = 1.0 + (f - 1.0) * boost;
        return (int) Math.ceil(n * f);
    }

    /**
     * What this villager tells: the rumor in their region's talk, perhaps changed once more.
     * The same villager telling the same rumor always says the same thing, so asking twice does not reroll it.
     */
    static Telling hear(Rumor r, Kind kind, UUID villager, Temperament temper, double retellChance) {
        Random rnd = new Random(Seeds.mix(villager.getMostSignificantBits(), villager.getLeastSignificantBits(),
                Seeds.of(r.factId())));
        if (rnd.nextDouble() < Math.min(1.0, retellChance * temper.mutation())) {
            return mutate(r.telling(), kind, rnd, temper.numberBoost());
        }
        return r.telling();
    }

    /** The rumor as it arrives in the next region: one hop further, and perhaps changed on the way. */
    static Rumor carry(Rumor r, Kind kind, Random rnd, double mutationChance) {
        Telling t = rnd.nextDouble() < mutationChance ? mutate(r.telling(), kind, rnd, 1.0) : r.telling();
        return r.hopped(t);
    }

    /** What a player passing a rumor on says: now and then it comes out slightly different. */
    static Telling pass(Telling t, Kind kind, Random rnd, double mutationChance) {
        return rnd.nextDouble() < mutationChance ? mutate(t, kind, rnd, 1.0) : t;
    }
}
