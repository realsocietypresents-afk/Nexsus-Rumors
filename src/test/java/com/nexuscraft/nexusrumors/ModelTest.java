package com.nexuscraft.nexusrumors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.nexuscraft.nexusrumors.Kit.expect;

/** The pure parts: places, drift, temperaments, wording, weighing tellings, verdicts, saving format. */
public class ModelTest {

    static final Region R0 = new Region("world", 0, 0);
    static final Region N1 = new Region("world", 0, -1);

    public static void main(String[] args) {
        grid();
        regionAndKind();
        mutation();
        retelling();
        temperaments();
        wording();
        heard();
        verdicts();
        codec();
        Kit.finish();
    }

    static Telling market(Region r, int n) {
        return new Telling(r, "", "a Mending book", "librarian", n, "", 0);
    }

    static void grid() {
        expect(Grid.regionOf("world", 0, 0, 0, 0, 192).equals(R0), "spawn is cell 0,0");
        expect(Grid.regionOf("world", 95.9, 0, 0, 0, 192).equals(R0), "95 blocks east is still around spawn (cells are centred on spawn)");
        expect(Grid.regionOf("world", 96, 0, 0, 0, 192).equals(new Region("world", 1, 0)), "96 blocks east is the next cell");
        expect(Grid.regionOf("world", -96, 0, 0, 0, 192).equals(R0), "-96 is still cell 0");
        expect(Grid.regionOf("world", -97, 0, 0, 0, 192).equals(new Region("world", -1, 0)), "-97 is cell -1");
        expect(Grid.regionOf("world", 0, -97, 0, 0, 192).equals(N1), "north is -z");
        expect(Grid.regionOf("world", -0.5, 0, 0, 0, 192).equals(R0), "fractions floor");
        expect(Grid.regionOf("world", 1000, -500, 1000, -500, 192).equals(R0), "the origin moves the whole grid");
        expect(Grid.regionOf("world", 1000 + 192 * 3, -500 - 192 * 2, 1000, -500, 192).equals(new Region("world", 3, -2)), "cells are region-size apart");
        expect(Grid.regionOf("far", 5, 5, 0, 0, 64).world().equals("far"), "the world name is kept");

        expect(Grid.where(R0).equals("around spawn"), "cell 0,0");
        expect(Grid.where(N1).equals("just north of spawn"), "ring 1 north");
        expect(Grid.where(new Region("world", 1, 0)).equals("just east of spawn"), "ring 1 east");
        expect(Grid.where(new Region("world", 0, 1)).equals("just south of spawn"), "ring 1 south");
        expect(Grid.where(new Region("world", -1, 0)).equals("just west of spawn"), "ring 1 west");
        expect(Grid.where(new Region("world", 2, 1)).equals("a fair way south-east of spawn"), "ring 2 diagonal");
        expect(Grid.where(new Region("world", -3, 0)).equals("far west of spawn"), "ring 3");
        expect(Grid.where(new Region("world", -1, -1)).equals("just north-west of spawn"), "diagonal");
        expect(Grid.where(new Region("world", 5, -9)).equals("far north-east of spawn"), "ring is the larger axis");
        expect(Grid.label(N1, "dark_forest").equals("the dark forest, just north of spawn"), "label with biome");
        expect(Grid.label(N1, "").equals("just north of spawn") && Grid.label(N1, null).equals("just north of spawn"), "label without");
        expect(!Grid.where(new Region("world", 300, 12)).matches(".*\\d.*"), "no number ever appears in a place");
    }

    static void regionAndKind() {
        Region r = new Region("my:world", -2, 7);
        expect(Region.parse(r.key()).equals(r), "a key parses back, even with a colon in the world name");
        expect(Region.parse(":1:2") == null, "a region needs a world name");
        expect(Region.parse("nonsense") == null && Region.parse("x:1") == null && Region.parse(null) == null, "bad keys");
        expect(Region.parse("w:a:b") == null, "bad numbers");
        expect(R0.step(2, -1).equals(new Region("world", 2, -1)), "step");
        expect(new Region("world", -4, 2).ring() == 4 && R0.ring() == 0, "ring");
        expect(Kind.parse("market") == Kind.MARKET && Kind.parse("DANGER") == Kind.DANGER && Kind.parse("Whereabouts") == Kind.SIGHTING
                && Kind.parse("sighting") == Kind.SIGHTING, "kinds parse by name or label");
        expect(Kind.parse("nope") == null && Kind.parse(null) == null, "unknown kinds");

        Fact f = new Fact("f1", Kind.DANGER, N1, "desert", "lava", "death", 2, "Ann", null, 100, 200);
        expect(f.exact().equals(new Telling(N1, "desert", "lava", "death", 2, "Ann", 0)), "a fact's exact telling");
        expect(!f.expired(200) && f.expired(201), "expiry is strictly after");
        expect(f.sameStory(Kind.DANGER, N1, "lava", "death") && !f.sameStory(Kind.DANGER, R0, "lava", "death")
                && !f.sameStory(Kind.MARKET, N1, "lava", "death") && !f.sameStory(Kind.DANGER, N1, "fire", "death")
                && !f.sameStory(Kind.DANGER, N1, "lava", "spawn"), "same story means same kind, place, detail and extra");
        Fact neg = new Fact("f2", Kind.MARKET, N1, null, null, null, -5, null, null, 0, 0);
        expect(neg.number == 0 && neg.detail.isEmpty() && neg.biome.isEmpty() && neg.subject.isEmpty(), "a fact never holds nulls or negatives");
        Telling t = new Telling(N1, null, null, null, -3, null, 99);
        expect(t.number() == 0 && t.skew() == Telling.MAX_SKEW && t.detail().isEmpty(), "a telling is tidied the same way");
        expect(t.vaguer().skew() == Telling.MAX_SKEW, "vagueness stops at the top");
        Rumor ru = new Rumor("f1", t, 2);
        expect(ru.hopped(t).hops() == 3 && ru.hops() == 2, "rumor hops");
    }

    static void mutation() {
        Telling base = new Telling(N1, "plains", "lava", "death", 3, "Ann", 0);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 3000; i++) {
            Telling m = Gossip.mutate(base, Kind.DANGER, new Random(i), 1.0);
            expect(!m.equals(base), "a mutation always changes something");
            if (!m.region().equals(base.region())) {
                int dx = m.region().cx() - base.region().cx();
                int dz = m.region().cz() - base.region().cz();
                expect(Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && (dx != 0 || dz != 0) && m.biome().isEmpty()
                        && m.number() == 3 && m.subject().equals("Ann"), "drift moves one cell and forgets the landscape");
                seen.add("drift");
            } else if (m.number() != 3) {
                expect(m.number() > 3 && m.biome().equals("plains"), "danger numbers only grow");
                seen.add("number");
            } else if (m.subject().isEmpty()) {
                seen.add("forget");
            } else if (m.skew() == 1) {
                seen.add("vague");
            } else {
                expect(false, "an unexplained mutation: " + m);
            }
        }
        expect(seen.size() == 4, "all four kinds of slip happen: " + seen);

        Telling bare = new Telling(N1, "", "a Mending book", "librarian", 1, "", Telling.MAX_SKEW);
        for (int i = 0; i < 200; i++) {
            Telling m = Gossip.mutate(bare, Kind.MARKET, new Random(i), 1.0);
            expect(!m.region().equals(N1) && m.number() == 1 && m.skew() == Telling.MAX_SKEW, "with nothing else to slip, the place drifts");
        }
        Telling cheap = new Telling(N1, "", "a Mending book", "librarian", 12, "", 0);
        boolean cheaper = false;
        for (int i = 0; i < 500; i++) {
            Telling m = Gossip.mutate(cheap, Kind.MARKET, new Random(i), 1.0);
            if (m.number() != 12) {
                cheaper = true;
                expect(m.number() >= 1 && m.number() < 12, "market prices only shrink");
            }
        }
        expect(cheaper, "market prices do get remembered cheaper");
        expect(Gossip.canChangeNumber(Kind.MARKET, cheap) && !Gossip.canChangeNumber(Kind.MARKET, market(N1, 1))
                && !Gossip.canChangeNumber(Kind.MARKET, market(N1, 0)) && Gossip.canChangeNumber(Kind.DANGER, new Telling(N1, "", "x", "", 1, "", 0))
                && !Gossip.canChangeNumber(Kind.DANGER, new Telling(N1, "", "x", "", 0, "", 0)) && !Gossip.canChangeNumber(Kind.SIGHTING, cheap),
                "which numbers can be stretched");

        for (int i = 0; i < 500; i++) {
            int a = Gossip.stretch(Kind.MARKET, 10, new Random(i), 1.0);
            int b = Gossip.stretch(Kind.MARKET, 10, new Random(i), 2.0);
            int c = Gossip.stretch(Kind.DANGER, 1, new Random(i), 1.0);
            int d = Gossip.stretch(Kind.DANGER, 1, new Random(i), 2.0);
            expect(a >= 3 && a <= 8, "price x0.3-0.8: " + a);
            expect(b >= 1 && b <= 6 && b <= a, "a boaster shrinks it further: " + b + " vs " + a);
            expect(c >= 2 && c <= 3, "toll x1.5-3 of 1: " + c);
            expect(d >= 2 && d <= 5 && d >= c, "a boaster grows it further: " + d + " vs " + c);
        }
        Random small = new Random(7);
        for (int i = 0; i < 400; i++) {
            expect(Gossip.stretch(Kind.MARKET, 2, small, 1.0) == 1, "a price of two can only become one");
            int three = Gossip.stretch(Kind.MARKET, 3, small, 1.0);
            expect(three >= 1 && three <= 2, "a price of three becomes one or two: " + three);
        }
        int lo = Integer.MAX_VALUE;
        int hi = 0;
        int lo2 = Integer.MAX_VALUE;
        int hi2 = 0;
        Random stream = new Random(99);
        for (int i = 0; i < 2000; i++) {
            int a = Gossip.stretch(Kind.DANGER, 10, stream, 1.0);
            int b = Gossip.stretch(Kind.DANGER, 10, stream, 2.0);
            lo = Math.min(lo, a);
            hi = Math.max(hi, a);
            lo2 = Math.min(lo2, b);
            hi2 = Math.max(hi2, b);
        }
        expect(lo >= 15 && lo <= 16 && hi >= 29 && hi <= 30, "ten deaths become 15-30 normally: " + lo + ".." + hi);
        expect(lo2 >= 20 && lo2 <= 21 && hi2 >= 48 && hi2 <= 50, "and 20-50 from a boaster: " + lo2 + ".." + hi2);
        expect(Gossip.stretch(Kind.DANGER, 10, new Random(3), 1.0) >= 15, "ten deaths become at least fifteen");
    }

    static void retelling() {
        Rumor r = new Rumor("f9", new Telling(N1, "plains", "lava", "death", 2, "Ann", 0), 1);
        UUID v = new UUID(7, 11);
        Telling a = Gossip.hear(r, Kind.DANGER, v, Temperament.PLAIN, 0.5);
        Telling b = Gossip.hear(r, Kind.DANGER, v, Temperament.PLAIN, 0.5);
        expect(a.equals(b), "the same villager tells the same rumor the same way every time");
        expect(Gossip.hear(r, Kind.DANGER, v, Temperament.PLAIN, 0.0).equals(r.telling()), "no chance of change: told as it was");
        Set<Telling> versions = new HashSet<>();
        int changed = 0;
        int soberChanged = 0;
        int gossipChanged = 0;
        for (int i = 0; i < 2000; i++) {
            UUID who = new UUID(i, 99);
            Telling t = Gossip.hear(r, Kind.DANGER, who, Temperament.PLAIN, 0.5);
            versions.add(t);
            if (!t.equals(r.telling())) {
                changed++;
            }
            if (!Gossip.hear(r, Kind.DANGER, who, Temperament.SOBER, 0.5).equals(r.telling())) {
                soberChanged++;
            }
            if (!Gossip.hear(r, Kind.DANGER, who, Temperament.GOSSIP, 0.5).equals(r.telling())) {
                gossipChanged++;
            }
            expect(Gossip.hear(r, Kind.DANGER, who, Temperament.BOASTER, 1.0).number() != 0, "boasters still tell something");
        }
        expect(changed > 800 && changed < 1200, "a plain villager changes it about half the time: " + changed);
        expect(soberChanged > 250 && soberChanged < 550, "a sober one 0.4 x that: " + soberChanged);
        expect(gossipChanged > 1500, "a gossip 1.7 x that: " + gossipChanged);
        expect(versions.size() > 5, "different villagers tell it differently");
        for (int i = 0; i < 100; i++) {
            expect(!Gossip.hear(r, Kind.DANGER, new UUID(i, 5), Temperament.GOSSIP, 1.0).equals(r.telling()), "chance x temperament over 1 always changes");
        }

        BukkitKit.Dice dice = new BukkitKit.Dice(5);
        dice.next(0.0);
        Rumor moved = Gossip.carry(r, Kind.DANGER, dice, 0.5);
        expect(moved.hops() == 2 && !moved.telling().equals(r.telling()) && moved.factId().equals("f9"), "a hop that goes wrong");
        dice.next(0.99);
        Rumor same = Gossip.carry(r, Kind.DANGER, dice, 0.5);
        expect(same.hops() == 2 && same.telling().equals(r.telling()), "a hop that goes right still counts as a hop");
        dice.next(0.0);
        expect(!Gossip.pass(r.telling(), Kind.DANGER, dice, 0.2).equals(r.telling()), "a player's retelling can slip");
        dice.next(0.2);
        expect(Gossip.pass(r.telling(), Kind.DANGER, dice, 0.2).equals(r.telling()), "at exactly the chance it does not");
    }

    static void temperaments() {
        int[] count = new int[Temperament.values().length];
        Random rnd = new Random(3);
        for (int i = 0; i < 20_000; i++) {
            UUID id = new UUID(rnd.nextLong(), rnd.nextLong());
            Temperament t = Temperament.of(id);
            expect(t == Temperament.of(id), "stable for life");
            count[t.ordinal()]++;
        }
        double[] want = {0.25, 0.40, 0.20, 0.15};
        for (Temperament t : Temperament.values()) {
            expect(Math.abs(count[t.ordinal()] / 20_000.0 - want[t.ordinal()]) < 0.02, t + " share: " + count[t.ordinal()]);
        }
        expect(Temperament.SOBER.mutation() < 1 && Temperament.GOSSIP.mutation() > 1 && Temperament.GOSSIP.tells() == 2
                && Temperament.PLAIN.tells() == 1 && Temperament.BOASTER.numberBoost() == 2.0 && Temperament.PLAIN.numberBoost() == 1.0, "the temperaments differ as designed");
        for (Temperament t : Temperament.values()) {
            expect(!t.adjective().isEmpty(), "adjective");
            Set<String> openers = new HashSet<>();
            for (long s = -10; s < 10; s++) {
                openers.add(t.opener(s));
            }
            expect(openers.size() == 3 && openers.stream().noneMatch(String::isBlank), t + " has three openers, and negative seeds work");
        }
        expect(Seeds.mix(1, 2) != Seeds.mix(2, 1) && Seeds.mix(1, 2) == Seeds.mix(1, 2), "mixing is ordered and stable");
        expect(Seeds.of(null) == 0L && Seeds.of("a") == "a".hashCode(), "string seeds");
    }

    static void wording() {
        Telling t = new Telling(N1, "dark_forest", "a Mending book", "librarian", 3, "", 0);
        expect(Tale.sentence(Kind.MARKET, t).equals("A librarian in the dark forest, just north of spawn, is selling a Mending book for 3 emeralds."), Tale.sentence(Kind.MARKET, t));
        Telling plain = new Telling(N1, "", "a Diamond Pickaxe", "armorer", 1, "", 0);
        expect(Tale.sentence(Kind.MARKET, plain).equals("An armorer just north of spawn is selling a Diamond Pickaxe for a single emerald."), Tale.sentence(Kind.MARKET, plain));
        expect(Tale.sentence(Kind.MARKET, new Telling(N1, "", "", "", 0, "", 0)).equals("A trader just north of spawn is selling something rare."), "no detail, no price");
        expect(Tale.sentence(Kind.MARKET, plain.vaguer()).startsWith("The other day, an armorer just"), "vague time 1");
        expect(Tale.sentence(Kind.MARKET, plain.vaguer().vaguer()).startsWith("A while back, an armorer"), "vague time 2");
        expect(Tale.sentence(Kind.MARKET, plain.vaguer().vaguer().vaguer()).startsWith("Ages ago, an armorer"), "vague time 3");

        Telling d = new Telling(N1, "", "lava", "death", 1, "", 0);
        expect(Tale.sentence(Kind.DANGER, d).equals("Someone died to lava just north of spawn."), Tale.sentence(Kind.DANGER, d));
        expect(Tale.sentence(Kind.DANGER, new Telling(N1, "", "lava", "death", 1, "Ann", 0)).equals("Ann died to lava just north of spawn."), "a named victim");
        expect(Tale.sentence(Kind.DANGER, new Telling(N1, "desert", "lava", "death", 3, "Ann", 0)).equals("3 people have died to lava in the desert, just north of spawn."), "a toll");
        expect(Tale.sentence(Kind.DANGER, new Telling(R0, "", "a Warden", "spawn", 1, "", 0)).equals("A Warden has been spotted around spawn."), "a spawn");
        expect(Tale.sentence(Kind.DANGER, new Telling(R0, "", "a Warden", "spawn", 3, "", 0)).equals("A Warden has been spotted 3 times around spawn."), "several spawns");
        expect(Tale.sentence(Kind.DANGER, new Telling(R0, "", "lava", "note", 0, "", 0)).equals("Folk warn of lava around spawn."), "a note");
        expect(Tale.sentence(Kind.DANGER, new Telling(R0, "", "", "death", 1, "", 0)).equals("Someone died to something nasty around spawn."), "no detail");
        expect(Tale.sentence(Kind.SIGHTING, new Telling(R0, "", "", "", 0, "Ann", 0)).equals("Ann was seen around spawn."), "a sighting");
        expect(Tale.sentence(Kind.SIGHTING, new Telling(R0, "", "", "", 0, "", 0)).equals("A traveler was seen around spawn."), "an anonymous sighting");
        expect(Tale.place(t).equals("in the dark forest, just north of spawn") && Tale.place(d).equals("just north of spawn"), "place");

        expect(Tale.nameWithArticle("WARDEN").equals("a Warden") && Tale.nameWithArticle("IRON_GOLEM").equals("an Iron Golem")
                && Tale.nameWithArticle("ENCHANTED_BOOK").equals("an Enchanted Book"), "names with articles");
        expect(Tale.title("silk_touch").equals("Silk Touch") && Tale.title("__a__b").equals("A B"), "titles");
        expect(Tale.capital("").isEmpty() && Tale.capital("x").equals("X"), "capital");
        expect(Tale.roman(1).isEmpty() && Tale.roman(2).equals("II") && Tale.roman(5).equals("V") && Tale.roman(10).equals("X")
                && Tale.roman(11).isEmpty() && Tale.roman(0).isEmpty() && Tale.roman(-1).isEmpty(), "numerals");
        expect(Texts.teller("v:" + UUID.randomUUID() + ":SOBER").equals("a wary villager") && Texts.teller("v:x:WHAT").equals("a villager")
                && Texts.teller("p:Ann").equals("Ann") && Texts.teller("odd").equals("odd"), "who told you");
    }

    static Heard.Version ver(String teller, int hops, long at, Telling t) {
        return new Heard.Version(teller, hops, at, t);
    }

    static void heard() {
        Region r1 = new Region("world", 1, 0);
        Region r2 = new Region("world", 2, 0);
        Heard h = new Heard("f1", Kind.MARKET);
        expect(h.add(ver("a", 2, 10, new Telling(r1, "plains", "x", "e", 5, "", 0)), 6), "first account added");
        expect(!h.add(ver("a", 2, 20, new Telling(r1, "plains", "x", "e", 5, "", 0)), 6) && h.versions.size() == 1 && h.lastAt == 20,
                "the same teller saying the same thing again is not new, but is recent");
        h.add(ver("b", 2, 30, new Telling(r1, "", "x", "e", 3, "", 0)), 6);
        h.add(ver("c", 1, 40, new Telling(r2, "", "x", "e", 5, "", 0)), 6);
        Telling c = h.consensus();
        expect(c.region().equals(r1) && c.number() == 5 && c.biome().equals("plains"), "place by majority, number by majority, landscape from a teller who had it: " + c);
        expect(h.agreeing() == 1 && !h.unanimous(), "only one account matches both");

        Heard tie = new Heard("f2", Kind.MARKET);
        tie.add(ver("far", 3, 1, new Telling(r1, "", "x", "e", 5, "", 0)), 6);
        tie.add(ver("near", 1, 2, new Telling(r2, "", "x", "e", 5, "", 0)), 6);
        expect(tie.consensus().region().equals(r2), "on a tie the teller nearer the event wins");
        Heard tie2 = new Heard("f3", Kind.MARKET);
        tie2.add(ver("first", 2, 1, new Telling(r1, "", "x", "e", 5, "", 0)), 6);
        tie2.add(ver("second", 2, 2, new Telling(r2, "", "x", "e", 5, "", 0)), 6);
        expect(tie2.consensus().region().equals(r1), "on a full tie the one heard first stands");
        expect(tie2.agreeing() == 1, "agreeing counts matches");
        Heard one = new Heard("f4", Kind.MARKET);
        one.add(ver("a", 1, 1, market(r1, 2)), 6);
        expect(one.unanimous() && one.agreeing() == 1 && one.consensus().equals(market(r1, 2)), "a single account is its own consensus");
        one.add(ver("b", 1, 2, market(r1, 2)), 6);
        expect(one.unanimous() && one.agreeing() == 2, "two matching accounts");

        Heard order = new Heard("f8", Kind.DANGER);
        order.add(ver("late", 1, 500, market(r1, 1)), 6);
        order.add(ver("early", 1, 100, market(r1, 2)), 6);
        expect(order.lastAt == 500, "an account heard out of order doesn't make the rumor look older");
        Heard land = new Heard("f9", Kind.MARKET);
        land.add(ver("a", 1, 1, new Telling(r1, "", "x", "e", 5, "", 0)), 6);
        land.add(ver("b", 1, 2, new Telling(r2, "desert", "x", "e", 5, "", 0)), 6);
        land.add(ver("c", 1, 3, new Telling(r1, "plains", "x", "e", 5, "", 0)), 6);
        expect(land.consensus().region().equals(r1) && land.consensus().biome().equals("plains"), "the landscape comes from an account of the winning place, not a drifted one: " + land.consensus());

        Heard cap = new Heard("f5", Kind.DANGER);
        for (int i = 0; i < 10; i++) {
            cap.add(ver("t" + i, 1, i, market(r1, i)), 3);
        }
        expect(cap.versions.size() == 3 && cap.versions.get(0).teller().equals("t7"), "only the latest accounts are kept");
        Heard floor = new Heard("f6", Kind.DANGER);
        floor.add(ver("a", 1, 1, market(r1, 1)), 0);
        floor.add(ver("b", 1, 2, market(r1, 2)), 0);
        expect(floor.versions.size() == 1, "at least one account is always kept");

        Heard subj = new Heard("f7", Kind.SIGHTING);
        subj.add(ver("a", 1, 1, new Telling(r1, "", "", "", 0, "Ann", 0)), 6);
        subj.add(ver("b", 2, 2, new Telling(r1, "", "", "", 0, "", 1)), 6);
        subj.add(ver("c", 2, 3, new Telling(r1, "", "", "", 0, "", 1)), 6);
        expect(subj.consensus().subject().isEmpty() && subj.consensus().skew() == 1, "a forgotten name and a vaguer time can win the vote too");
    }

    static void verdicts() {
        Fact f = new Fact("f1", Kind.MARKET, N1, "", "x", "e", 3, "", null, 0, 100);
        expect(Verdict.judge(f, market(N1, 3), 50) == Verdict.TRUE, "right place, right number");
        expect(Verdict.judge(f, market(N1, 1), 50) == Verdict.EXAGGERATED, "right place, wrong number");
        expect(Verdict.judge(f, market(R0, 3), 50) == Verdict.FALSE, "wrong place");
        expect(Verdict.judge(f, market(N1, 3), 100) == Verdict.TRUE && Verdict.judge(f, market(N1, 3), 101) == Verdict.STALE, "over, at the very end");
        expect(Verdict.judge(f, market(R0, 3), 500) == Verdict.FALSE, "wrong place stays wrong when it is late");
        expect(Verdict.judge(null, market(N1, 3), 50) == Verdict.STALE, "forgotten facts are no verdict");
        Fact noNumber = new Fact("f2", Kind.SIGHTING, N1, "", "", "", 0, "Ann", null, 0, 100);
        expect(Verdict.judge(noNumber, new Telling(N1, "", "", "", 7, "", 0), 5) == Verdict.TRUE, "no number in the fact: nothing to exaggerate");
        expect(Verdict.judge(noNumber, new Telling(new Region("other", 0, -1), "", "", "", 0, "", 0), 5) == Verdict.FALSE, "same cell in another world is a different place");
    }

    static void codec() {
        String nasty = "a|b\\c\nd\re||";
        List<String> fields = List.of(nasty, "", "plain", "|");
        expect(Codec.split(Codec.join(fields)).equals(fields), "fields with every special character round-trip");
        expect(Codec.split("a|b").equals(List.of("a", "b")) && Codec.split("").equals(List.of("")) && Codec.split("a|").equals(List.of("a", "")),
                "splitting");
        expect(Codec.split("a\\").equals(List.of("a\\")), "a dangling backslash is kept as text");
        expect(!Codec.join(List.of("a|b")).contains("b|") && Codec.esc("x\\y").equals("x\\\\y"), "escaping");
        Telling t = new Telling(new Region("my|world", -3, 4), "dark_forest", "a Mending book", "libr|arian", 7, "An|n", 2);
        List<String> f = Codec.tellingFields(t);
        expect(f.size() == Codec.TELLING_FIELDS, "nine fields");
        expect(Codec.parseTelling(Codec.split(Codec.join(f)), 0).equals(t), "a telling round-trips");
        List<String> padded = new ArrayList<>(List.of("x", "y"));
        padded.addAll(f);
        expect(Codec.parseTelling(padded, 2).equals(t), "and from an offset");
        expect(Codec.parseTelling(f.subList(0, 8), 0) == null, "too few fields");
        List<String> bad = new ArrayList<>(f);
        bad.set(1, "x");
        expect(Codec.parseTelling(bad, 0) == null, "a damaged number");
        bad = new ArrayList<>(f);
        bad.set(8, "");
        expect(Codec.parseTelling(bad, 0) == null, "an empty number");
    }
}
