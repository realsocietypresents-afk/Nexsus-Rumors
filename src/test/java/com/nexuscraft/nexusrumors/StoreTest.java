package com.nexuscraft.nexusrumors;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexusrumors.Kit.expect;

/** Saving and loading. */
public class StoreTest {

    public static void main(String[] args) throws Exception {
        roundTrip();
        damage();
        Kit.finish();
    }

    static void roundTrip() throws Exception {
        BukkitKit.reset();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        RumorStore s = rig.store;
        expect(!s.dirty(), "a fresh store has nothing to save");
        s.save();
        expect(!s.dirty() && new File(rig.dir, "facts.yml").isFile() && new File(rig.dir, "rumors.yml").isFile() && new File(rig.dir, "players.yml").isFile(),
                "saving writes the three files");
        UUID ann = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        Region r = new Region("world", -2, 3);
        Fact f = new Fact("f7", Kind.SIGHTING, r, "dark_forest", "de|tail\\", "ex\ntra", 4, "An|n", subject, 111, 999);
        s.facts.put(f.id, f);
        Fact plain = new Fact("f8", Kind.MARKET, r, "", "a Mending book", "librarian", 0, "", null, 5, 6);
        s.facts.put(plain.id, plain);
        s.nextFact = 9;
        s.origins.put("world", new int[]{500, -300});
        s.origins.put("my|world", new int[]{-1, 2});
        s.mill(r).add(new Rumor("f7", f.exact(), 2));
        s.mill(r).add(new Rumor("f8", plain.exact().withNumber(3), 0));
        s.mill(new Region("world", 0, 0)).add(new Rumor("f8", plain.exact(), 1));
        RumorStore.Book b = s.book(ann);
        b.name = "Ann";
        b.hidden = true;
        b.trueCount = 3;
        b.exaggeratedCount = 2;
        b.falseCount = 1;
        b.staleCount = 4;
        b.posted.add("f7");
        b.posted.add("f8");
        Heard h = new Heard("f7", Kind.SIGHTING);
        h.add(new Heard.Version("v:abc:GOSSIP", 2, 1000, f.exact()), 6);
        h.add(new Heard.Version("p:Bo|x", 3, 2000, f.exact().withNumber(9)), 6);
        b.heard.put("f7", h);
        Heard h2 = new Heard("f8", Kind.MARKET);
        h2.add(new Heard.Version("v:def:SOBER", 1, 3000, plain.exact()), 6);
        b.heard.put("f8", h2);
        s.book(UUID.randomUUID());
        s.touch();
        expect(s.dirty(), "touch marks it");
        s.save();
        expect(!s.dirty(), "saving clears it");
        File[] leftovers = rig.dir.listFiles((d, n) -> n.endsWith(".tmp"));
        expect(leftovers != null && leftovers.length == 0, "no temporary files are left behind");

        RumorStore t = new RumorStore(rig.dir, rig.plugin.getLogger());
        t.load();
        expect(t.nextFact == 9 && !t.dirty(), "the counter");
        expect(java.util.Arrays.equals(t.origins.get("world"), new int[]{500, -300}) && java.util.Arrays.equals(t.origins.get("my|world"), new int[]{-1, 2}), "origins");
        Fact g = t.facts.get("f7");
        expect(g != null && g.kind == Kind.SIGHTING && g.region.equals(r) && g.biome.equals("dark_forest") && g.detail.equals("de|tail\\")
                && g.extra.equals("ex\ntra") && g.number == 4 && g.subject.equals("An|n") && subject.equals(g.subjectId) && g.createdAt == 111 && g.expiresAt == 999,
                "a fact comes back exactly, special characters and all");
        Fact g2 = t.facts.get("f8");
        expect(g2 != null && g2.subjectId == null && g2.number == 0 && g2.biome.isEmpty(), "and one with blanks");
        expect(t.facts.keySet().stream().toList().equals(List.of("f7", "f8")), "in the same order");
        expect(t.mill(r).size() == 2 && t.mill(r).get(0).equals(new Rumor("f7", f.exact(), 2)) && t.mill(r).get(1).telling().number() == 3
                && t.mill(new Region("world", 0, 0)).get(0).hops() == 1 && t.rumorCount() == 3, "the villagers' talk");
        RumorStore.Book c = t.books.get(ann);
        expect(c != null && c.name.equals("Ann") && c.hidden && c.trueCount == 3 && c.exaggeratedCount == 2 && c.falseCount == 1 && c.staleCount == 4
                && c.checked() == 6 && c.posted.equals(b.posted), "a player's record");
        Heard x = c.heard.get("f7");
        expect(x != null && x.kind == Kind.SIGHTING && x.versions.size() == 2 && x.lastAt == 2000 && x.versions.get(1).teller().equals("p:Bo|x")
                && x.versions.get(1).telling().number() == 9 && x.versions.get(0).hops() == 2 && x.versions.get(0).at() == 1000, "her notes");
        expect(c.heard.keySet().stream().toList().equals(List.of("f7", "f8")) && c.heard.get("f8").versions.size() == 1, "all of them");
        expect(t.books.size() == 2, "and an empty record for someone who never heard anything");

        s.facts.clear();
        s.mills.clear();
        s.books.clear();
        s.touch();
        s.save();
        t.load();
        expect(t.facts.isEmpty() && t.mills.isEmpty() && t.books.isEmpty(), "loading replaces what was in memory");
    }

    static void damage() throws Exception {
        BukkitKit.reset();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        YamlConfiguration f = new YamlConfiguration();
        f.set("next", 0);
        f.set("origins", List.of("world|1|2", "bad", "world|x|2", "solo"));
        UUID bad = UUID.randomUUID();
        f.set("facts", List.of(
                "garbage",
                Codec.join(List.of("f1", "NOPE", "world", "0", "0", "", "d", "e", "1", "", "", "1", "2")),
                Codec.join(List.of("f2", "DANGER", "world", "zero", "0", "", "d", "e", "1", "", "", "1", "2")),
                Codec.join(List.of("f3", "DANGER", "world", "0", "0", "", "d", "e", "1", "", "not-a-uuid", "1", "2")),
                Codec.join(List.of("f4", "DANGER", "world", "0", "0", "", "d", "e", "1", "", "", "1", "2")),
                Codec.join(List.of("f5", "danger", "world", "0", "0", "", "d", "e", "-4", "", bad.toString(), "1", "2"))));
        f.save(new File(rig.dir, "facts.yml"));
        YamlConfiguration r = new YamlConfiguration();
        List<String> rumors = new ArrayList<>();
        rumors.add("garbage");
        rumors.add("world:0:0|f4|x|" + String.join("|", Codec.tellingFields(ModelTest.market(new Region("world", 0, 0), 1))));
        rumors.add("world:0:0|f4|1|world|0|0");
        List<String> ok = new ArrayList<>(List.of("world:0:0", "f4", "1"));
        ok.addAll(Codec.tellingFields(ModelTest.market(new Region("world", 0, 0), 1)));
        rumors.add(Codec.join(ok));
        r.set("rumors", rumors);
        r.save(new File(rig.dir, "rumors.yml"));
        YamlConfiguration p = new YamlConfiguration();
        UUID ann = UUID.randomUUID();
        p.set("not-a-player.name", "x");
        p.set(ann + ".name", "Ann");
        p.set(ann + ".true", -5);
        List<String> heard = new ArrayList<>();
        heard.add("garbage");
        heard.add("f4|NOPE|1|t|1|1|" + String.join("|", Codec.tellingFields(ModelTest.market(new Region("world", 0, 0), 1))));
        heard.add("f4|MARKET|x|t|1|1|" + String.join("|", Codec.tellingFields(ModelTest.market(new Region("world", 0, 0), 1))));
        heard.add("f4|MARKET|1|t|x|1|" + String.join("|", Codec.tellingFields(ModelTest.market(new Region("world", 0, 0), 1))));
        heard.add("f4|MARKET|1|t|1|1|world|0");
        List<String> good = new ArrayList<>(List.of("f4", "MARKET", "5", "t", "1", "4"));
        good.addAll(Codec.tellingFields(ModelTest.market(new Region("world", 0, 0), 1)));
        heard.add(Codec.join(good));
        p.set(ann + ".heard", heard);
        p.save(new File(rig.dir, "players.yml"));
        rig.store.load();
        expect(rig.store.nextFact == 1, "a damaged counter restarts at 1");
        expect(rig.store.origins.size() == 1 && rig.store.origins.containsKey("world"), "only the usable origin is kept");
        expect(rig.store.facts.keySet().stream().toList().equals(List.of("f4", "f5")), "damaged facts are skipped, good ones kept: " + rig.store.facts.keySet());
        expect(rig.store.facts.get("f5").number == 0 && rig.store.facts.get("f5").kind == Kind.DANGER, "a negative number becomes none, and kinds read in any case");
        expect(rig.store.rumorCount() == 1, "damaged rumors are skipped: " + rig.store.rumorCount());
        expect(rig.store.books.size() == 1 && rig.store.books.get(ann).trueCount == 0, "something that isn't a player is ignored, and counts are never negative");
        Heard h = rig.store.books.get(ann).heard.get("f4");
        expect(h != null && h.versions.size() == 1 && h.lastAt == 5, "damaged accounts are skipped");

        // a fact line missing its last field is skipped, not fatal
        YamlConfiguration short12 = new YamlConfiguration();
        short12.set("facts", List.of(Codec.join(List.of("f1", "DANGER", "world", "0", "0", "", "d", "e", "1", "", "", "1"))));
        short12.save(new File(rig.dir, "facts.yml"));
        rig.store.load();
        expect(rig.store.facts.isEmpty(), "twelve fields is not a fact");

        // one file that can't be written must not stop the others
        BukkitKit.reset();
        BukkitKit.Rig part = new BukkitKit.Rig();
        File jam = new File(part.dir, "facts.yml.tmp");
        expect(jam.mkdirs() && new File(jam, "x").createNewFile(), "(setup: facts.yml cannot be written)");
        part.store.book(UUID.randomUUID()).name = "Ann";
        part.store.touch();
        part.store.save();
        expect(part.store.dirty() && new File(part.dir, "players.yml").isFile() && new File(part.dir, "rumors.yml").isFile(),
                "the other files are still saved, and the store stays dirty so facts.yml is tried again");

        // an unreadable folder: saving reports it instead of crashing
        File blocked = new File(rig.dir, "blocked");
        Files.writeString(blocked.toPath(), "i am a file");
        RumorStore bad2 = new RumorStore(new File(blocked, "inside"), rig.plugin.getLogger());
        bad2.touch();
        bad2.save();
        expect(bad2.dirty(), "if saving fails it stays dirty so the next try happens");
        RumorStore missing = new RumorStore(new File(rig.dir, "does/not/exist"), rig.plugin.getLogger());
        missing.load();
        missing.touch();
        missing.save();
        expect(!missing.dirty() && new File(rig.dir, "does/not/exist/facts.yml").isFile(), "a missing folder is created");
    }
}
