package com.nexuscraft.nexusrumors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexusrumors.Kit.expect;

/** The gossip economy end to end, with a clock and dice we control. */
public class ServiceTest {

    public static void main(String[] args) {
        facts();
        caps();
        trades();
        deaths();
        spawns();
        sightings();
        hearing();
        tellers();
        telling();
        posting();
        spreading();
        upkeep();
        checking();
        notebook();
        Kit.finish();
    }

    static BukkitKit.Rig rig() {
        BukkitKit.reset();
        BukkitKit.world();
        return new BukkitKit.Rig();
    }

    static BukkitKit.Rig rig(String key, Object value) {
        BukkitKit.reset();
        BukkitKit.world();
        YamlConfiguration y = BukkitKit.shipped();
        y.set(key, value);
        return new BukkitKit.Rig(y);
    }

    static Location at(double x, double z) {
        return new Location(BukkitKit.world(), x, 70, z);
    }

    static RumorService.Reported danger(BukkitKit.Rig rig, double x, double z, String what) {
        return rig.service.report(Kind.DANGER, at(x, z), what, "death", 1, "", null);
    }

    static void facts() {
        BukkitKit.Rig rig = rig();
        BukkitKit.biome(BukkitKit.world(), Biome.DARK_FOREST);
        RumorService.Reported r = danger(rig, 10, 10, "lava");
        expect(r.changed() && r.fact() != null && r.fact().id.equals("f1"), "a first fact");
        expect(r.fact().region.equals(new Region("world", 0, 0)) && r.fact().biome.equals("dark_forest"), "in the right region, with the landscape");
        expect(r.fact().expiresAt == rig.now.get() + 180 * 60_000L, "it lives for the danger lifetime");
        List<Rumor> mill = rig.store.mill(r.fact().region);
        expect(mill.size() == 1 && mill.get(0).hops() == 0 && mill.get(0).telling().equals(r.fact().exact()), "villagers there know it, exactly");
        expect(rig.store.dirty() && rig.store.origins.containsKey("world"), "the store knows to save, and remembers the grid's origin");

        rig.minutes(10);
        RumorService.Reported again = danger(rig, 50, -20, "lava");
        expect(again.fact() == r.fact() && again.changed() && r.fact().number == 2, "the same thing again in the same region grows the story");
        expect(rig.store.mill(r.fact().region).size() == 1 && rig.store.mill(r.fact().region).get(0).telling().number() == 2, "and the talk is updated, not doubled");
        expect(r.fact().expiresAt == rig.now.get() + 180 * 60_000L, "and its life is renewed");
        RumorService.Reported note = rig.service.report(Kind.DANGER, at(10, 10), "a cave-in", "note", 0, "", null);
        rig.minutes(5);
        RumorService.Reported note2 = rig.service.report(Kind.DANGER, at(10, 10), "a cave-in", "note", 0, "", null);
        expect(note.changed() && note2.fact() == note.fact() && !note2.changed() && note.fact().number == 0
                && note.fact().expiresAt == rig.now.get() + 180 * 60_000L, "repeating a note without a count renews it but adds nothing");
        rig.store.facts.remove(note.fact().id);
        rig.store.mill(note.fact().region).removeIf(x -> x.factId().equals(note.fact().id));
        RumorService.Reported other = danger(rig, 10, 10, "fire");
        expect(other.fact() != r.fact() && other.fact().id.equals("f3"), "a different danger is a different story");
        RumorService.Reported farAway = danger(rig, 400, 10, "lava");
        expect(farAway.fact().region.equals(new Region("world", 2, 0)), "so is the same danger in another region: " + farAway.fact().region);

        // the home region's talk is only refreshed when it is the first-hand telling
        BukkitKit.Rig home = rig();
        Fact hf = danger(home, 10, 10, "lava").fact();
        home.store.mill(hf.region).clear();
        Telling drifted = hf.exact().withRegion(new Region("world", 0, 1));
        home.store.mill(hf.region).add(new Rumor(hf.id, drifted, 2));
        danger(home, 10, 10, "lava");
        expect(hf.number == 2 && home.store.mill(hf.region).get(0).equals(new Rumor(hf.id, drifted, 2)), "a second-hand version in the home region is left alone");

        RumorService.Reported m = rig.service.report(Kind.MARKET, at(1, 1), "a Mending book", "librarian", 4, "", null);
        RumorService.Reported m2 = rig.service.report(Kind.MARKET, at(2, 2), "a Mending book", "librarian", 3, "", null);
        expect(m.changed() && m2.fact() == m.fact() && !m2.changed() && m.fact().number == 4, "a market story that is already out there is left as it was");

        UUID ann = UUID.randomUUID();
        RumorService.Reported s = rig.service.report(Kind.SIGHTING, at(1, 1), "", "", 0, "Ann", ann);
        RumorService.Reported s2 = rig.service.report(Kind.SIGHTING, at(300, 1), "", "", 0, "Ann", ann);
        expect(s.changed() && s2.fact() == s.fact() && !s2.changed(), "one sighting of a person at a time");
        expect(rig.service.report(Kind.SIGHTING, at(300, 1), "", "", 0, "Bo", UUID.randomUUID()).changed(), "but someone else can be seen");
        rig.minutes(21);
        RumorService.Reported s3 = rig.service.report(Kind.SIGHTING, at(300, 1), "", "", 0, "Ann", ann);
        expect(s3.changed() && s3.fact() != s.fact() && s3.fact().expiresAt == rig.now.get() + 20 * 60_000L, "once the first is over, she can be seen again");

        expect(rig.service.report(Kind.DANGER, null, "x", "", 1, "", null) == RumorService.Reported.NONE, "no place, no story");
        World nether = BukkitKit.nether();
        expect(rig.service.report(Kind.DANGER, new Location(nether, 0, 60, 0), "lava", "death", 1, "", null).fact() == null, "the Nether is not tracked by default");
        expect(!RumorService.Reported.NONE.changed() && RumorService.Reported.NONE.fact() == null, "the empty report");
        BukkitKit.Rig named = rig("worlds", List.of("world_nether"));
        World n2 = BukkitKit.nether();
        expect(named.service.report(Kind.DANGER, new Location(n2, 0, 60, 0), "lava", "death", 1, "", null).changed(), "unless it is named in worlds");
        expect(named.service.report(Kind.DANGER, at(0, 0), "lava", "death", 1, "", null).fact() == null, "and then the overworld is not");
        BukkitKit.Rig off = rig("enabled", false);
        expect(danger(off, 0, 0, "lava").fact() == null && off.store.facts.isEmpty(), "switched off: nothing is recorded");

        // the grid's origin is where the world spawn was when the first rumor happened, and stays there
        BukkitKit.Rig moved = rig();
        BukkitKit.world().setSpawnLocation(500, 64, -300);
        expect(moved.service.regionAt(at(500, -300)).equals(new Region("world", 0, 0)), "cells are centred on the world's spawn");
        BukkitKit.world().setSpawnLocation(0, 64, 0);
        expect(moved.service.regionAt(at(500, -300)).equals(new Region("world", 0, 0)), "and stay there if spawn moves later");
        expect(moved.service.regionAt(at(500 + 192, -300)).equals(new Region("world", 1, 0)), "one cell over");

        // facts keep counting from where they left off after a restart
        rig.store.save();
        BukkitKit.Rig back = rig.restart();
        expect(back.store.nextFact == rig.store.nextFact, "ids carry on after a restart");
    }

    static void caps() {
        BukkitKit.reset();
        BukkitKit.world();
        YamlConfiguration y = BukkitKit.shipped();
        y.set("max-rumors-per-region", 3);
        BukkitKit.Rig rig = new BukkitKit.Rig(y);
        for (int i = 0; i < 5; i++) {
            danger(rig, 0, 0, "thing " + i);
        }
        List<Rumor> mill = rig.store.mill(new Region("world", 0, 0));
        expect(mill.size() == 3 && mill.get(0).factId().equals("f3") && mill.get(2).factId().equals("f5"), "a region's talk holds only the latest few");

        BukkitKit.reset();
        BukkitKit.world();
        y = BukkitKit.shipped();
        y.set("max-rumors-total", 10);
        y.set("max-rumors-per-region", 100);
        BukkitKit.Rig total = new BukkitKit.Rig(y);
        for (int i = 0; i < 14; i++) {
            danger(total, i * 192, 0, "thing");
        }
        expect(total.store.rumorCount() == 10, "and the whole server only so many: " + total.store.rumorCount());
        expect(total.service.report(Kind.DANGER, at(14 * 192, 0), "fresh", "death", 1, "", null).fact() != null
                && total.store.mills.get(new Region("world", 14, 0).key()).size() == 1, "the newest is never the one dropped");

        BukkitKit.reset();
        BukkitKit.world();
        y = BukkitKit.shipped();
        y.set("max-rumors-total", 10);
        y.set("max-rumors-per-region", 100);
        BukkitKit.Rig first = new BukkitKit.Rig(y);
        Region lead = new Region("world", 99, 0);
        first.store.mill(lead);
        for (int i = 0; i < 10; i++) {
            danger(first, i * 192, 0, "thing");
        }
        Fact fresh = first.service.report(Kind.DANGER, at(99 * 192, 0), "fresh", "death", 1, "", null).fact();
        expect(first.store.rumorCount() == 10 && first.store.mill(lead).size() == 1 && first.store.mill(lead).get(0).factId().equals(fresh.id),
                "even when its region is the first the server looks at, the newest rumor is kept");
    }

    static MerchantRecipe recipe(ItemStack result, int emeralds) {
        MerchantRecipe r = new MerchantRecipe(result, 0, 8, true);
        if (emeralds > 0) {
            r.addIngredient(new ItemStack(Material.EMERALD, emeralds));
        }
        return r;
    }

    static ItemStack book(Enchantment e, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(e, level, true);
        book.setItemMeta(meta);
        return book;
    }

    static void trades() {
        BukkitKit.Rig rig = rig();
        rig.dice.always = 0.0;
        Villager lib = BukkitKit.villager(10, 10);
        lib.setProfession(Villager.Profession.LIBRARIAN);
        rig.service.onTrade(lib, recipe(book(Enchantment.MENDING, 1), 17));
        expect(rig.store.facts.size() == 1, "a Mending book is worth gossip");
        Fact f = rig.store.facts.get("f1");
        expect(f.kind == Kind.MARKET && f.detail.equals("a Mending book") && f.extra.equals("librarian") && f.number == 17 && f.region.equals(new Region("world", 0, 0)),
                "what, who, how much and where: " + f.detail + "/" + f.extra + "/" + f.number);
        rig.service.onTrade(lib, recipe(book(Enchantment.SHARPNESS, 5), 9));
        expect(rig.store.facts.size() == 1, "the same villager does not gossip about two trades in half an hour");
        rig.minutes(31);
        rig.service.onTrade(lib, recipe(book(Enchantment.SHARPNESS, 5), 9));
        expect(rig.store.facts.size() == 2 && rig.store.facts.get("f2").detail.equals("a Sharpness V book"), "after that they may, and levels are told in numerals");
        Villager smith = BukkitKit.villager(10, 10);
        smith.setProfession(Villager.Profession.ARMORER);
        rig.service.onTrade(smith, recipe(new ItemStack(Material.DIAMOND_CHESTPLATE), 24));
        expect(rig.store.facts.get("f3").detail.equals("a Diamond Chestplate") && rig.store.facts.get("f3").extra.equals("armorer"), "notable items too");
        Villager none = BukkitKit.villager(10, 10);
        rig.service.onTrade(none, recipe(new ItemStack(Material.SADDLE), 0));
        expect(rig.store.facts.get("f4").extra.equals("trader") && rig.store.facts.get("f4").number == 0, "no profession is just a trader; no emeralds means no price");
        WanderingTrader wt = new WanderingTrader();
        wt.stubSetLocation(at(50, 50));
        rig.service.onTrade(wt, recipe(new ItemStack(Material.NAME_TAG), 2));
        expect(rig.store.facts.get("f5").extra.equals("wandering trader"), "wandering traders count");
        int before = rig.store.facts.size();
        Villager dull = BukkitKit.villager(10, 10);
        rig.service.onTrade(dull, recipe(new ItemStack(Material.BREAD), 1));
        rig.service.onTrade(dull, recipe(book(Enchantment.LURE, 1), 3));
        rig.service.onTrade(dull, recipe(new ItemStack(Material.ENCHANTED_BOOK), 3));
        rig.service.onTrade(dull, null);
        rig.service.onTrade(null, recipe(new ItemStack(Material.SADDLE), 1));
        rig.service.onTrade(dull, new MerchantRecipe(null, 1));
        expect(rig.store.facts.size() == before, "bread, an unremarkable book, a blank book and nonsense are not gossip");

        rig.dice.always = 0.9;
        Villager shy = BukkitKit.villager(10, 10);
        rig.service.onTrade(shy, recipe(new ItemStack(Material.BELL), 1));
        expect(rig.store.facts.size() == before, "only some notable trades get talked about (chance 0.6)");
        rig.dice.always = 0.6;
        rig.service.onTrade(shy, recipe(new ItemStack(Material.BELL), 1));
        expect(rig.store.facts.size() == before, "a roll exactly at the chance does not");
        rig.dice.always = 0.5;
        rig.service.onTrade(shy, recipe(new ItemStack(Material.BELL), 1));
        expect(rig.store.facts.size() == before + 1, "a roll under the chance does");

        Villager mixed = BukkitKit.villager(300, 300);
        MerchantRecipe two = recipe(new ItemStack(Material.BELL), 2);
        two.addIngredient(new ItemStack(Material.DIAMOND, 3));
        rig.service.onTrade(mixed, two);
        expect(rig.store.facts.values().stream().anyMatch(x -> x.detail.equals("a Bell") && x.number == 2 && x.region.equals(new Region("world", 2, 2))),
                "only emeralds count towards the price");
        BukkitKit.Rig off = rig("market.enabled", false);
        off.dice.always = 0.0;
        off.service.onTrade(lib, recipe(new ItemStack(Material.SADDLE), 1));
        expect(off.store.facts.isEmpty(), "market rumors can be switched off");
        BukkitKit.Rig noCool = rig("market.villager-cooldown-minutes", 0);
        noCool.dice.always = 0.0;
        noCool.service.onTrade(lib, recipe(new ItemStack(Material.SADDLE), 1));
        noCool.service.onTrade(lib, recipe(new ItemStack(Material.BELL), 1));
        expect(noCool.store.facts.size() == 2, "without a cooldown, every trade can be talked about");
        BukkitKit.Rig dup = rig();
        dup.dice.always = 0.0;
        Villager a = BukkitKit.villager(10, 10);
        a.setProfession(Villager.Profession.LIBRARIAN);
        Villager b = BukkitKit.villager(20, 20);
        b.setProfession(Villager.Profession.LIBRARIAN);
        dup.service.onTrade(a, recipe(book(Enchantment.MENDING, 1), 5));
        dup.service.onTrade(b, recipe(book(Enchantment.MENDING, 1), 5));
        expect(dup.store.facts.size() == 1, "two librarians in one region selling the same book are one story");
            }

    static EntityDamageEvent hit(Object victim, EntityDamageEvent.DamageCause cause) {
        return new EntityDamageEvent((org.bukkit.entity.Entity) victim, cause);
    }

    static void deaths() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        ann.moveTo(BukkitKit.world(), 5, 5);
        rig.service.onDeath(ann, hit(ann, EntityDamageEvent.DamageCause.LAVA));
        Fact f = rig.store.facts.get("f1");
        expect(f.kind == Kind.DANGER && f.detail.equals("lava") && f.extra.equals("death") && f.number == 1 && f.subject.isEmpty(), "a death by lava, victim unnamed");
        rig.service.onDeath(ann, hit(ann, EntityDamageEvent.DamageCause.LAVA));
        expect(rig.store.facts.size() == 1 && f.number == 2, "the second death in the same place adds to the toll");

        String[][] causes = {
                {"FIRE", "fire"}, {"FIRE_TICK", "fire"}, {"HOT_FLOOR", "fire"}, {"FALL", "a long fall"}, {"FLY_INTO_WALL", "a long fall"},
                {"DROWNING", "deep water"}, {"BLOCK_EXPLOSION", "an explosion"}, {"ENTITY_EXPLOSION", "an explosion"},
                {"PROJECTILE", "an arrow"}, {"STARVATION", "hunger"}, {"VOID", "the void"}, {"SUFFOCATION", "a collapse"},
                {"FALLING_BLOCK", "a collapse"}, {"CRAMMING", "a collapse"}, {"LIGHTNING", "lightning"}, {"WITHER", "the withering"},
                {"FREEZE", "the cold"}, {"POISON", "dark magic"}, {"MAGIC", "dark magic"}, {"DRAGON_BREATH", "dark magic"},
                {"SONIC_BOOM", "a Warden"}, {"CONTACT", "something nasty"}, {"CUSTOM", "something nasty"}};
        for (String[] c : causes) {
            BukkitKit.Rig r = rig();
            BukkitKit.TestPlayer p = BukkitKit.join("P");
            p.moveTo(BukkitKit.world(), 5, 5);
            r.service.onDeath(p, hit(p, EntityDamageEvent.DamageCause.valueOf(c[0])));
            expect(r.store.facts.size() == 1 && r.store.facts.get("f1").detail.equals(c[1]), c[0] + " is told as " + c[1] + ": " + r.store.facts.values().stream().map(x -> x.detail).toList());
        }
        for (String quiet : new String[]{"SUICIDE", "KILL"}) {
            BukkitKit.Rig r = rig();
            BukkitKit.TestPlayer p = BukkitKit.join("P");
            p.moveTo(BukkitKit.world(), 5, 5);
            r.service.onDeath(p, hit(p, EntityDamageEvent.DamageCause.valueOf(quiet)));
            expect(r.store.facts.isEmpty(), quiet + " is nobody's story");
        }

        BukkitKit.Rig r = rig();
        BukkitKit.TestPlayer p = BukkitKit.join("P");
        p.moveTo(BukkitKit.world(), 5, 5);
        r.service.onDeath(p, null);
        expect(r.store.facts.get("f1").detail.equals("something nasty"), "no known cause");
        Zombie z = new Zombie();
        z.stubSetType(EntityType.ZOMBIE);
        r.service.onDeath(p, new EntityDamageByEntityEvent(z, p, EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        expect(r.store.facts.get("f2").detail.equals("a Zombie"), "killed by a creature: it is named");
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        r.service.onDeath(p, new EntityDamageByEntityEvent(bo, p, EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        expect(r.store.facts.size() == 2, "player against player is not a danger of the land");
        BukkitKit.Rig pvp = rig("danger.pvp", true);
        BukkitKit.TestPlayer q = BukkitKit.join("Q");
        q.moveTo(BukkitKit.world(), 5, 5);
        BukkitKit.TestPlayer killer = BukkitKit.join("K");
        pvp.service.onDeath(q, new EntityDamageByEntityEvent(killer, q, EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        expect(pvp.store.facts.size() == 1, "unless the server wants it (danger.pvp)");

        BukkitKit.Rig named = rig("danger.name-victims", true);
        BukkitKit.TestPlayer ann2 = BukkitKit.join("Ann");
        ann2.moveTo(BukkitKit.world(), 5, 5);
        named.service.onDeath(ann2, hit(ann2, EntityDamageEvent.DamageCause.LAVA));
        expect(named.store.facts.get("f1").subject.equals("Ann"), "victims are named when the server asks");
        BukkitKit.TestPlayer hider = BukkitKit.join("Hidey");
        hider.moveTo(BukkitKit.world(), 500, 5);
        named.service.setHidden(hider, true);
        named.service.onDeath(hider, hit(hider, EntityDamageEvent.DamageCause.LAVA));
        expect(named.store.facts.get("f2").subject.isEmpty(), "but never someone who opted out");
        BukkitKit.Rig noDeaths = rig("danger.deaths", false);
        BukkitKit.TestPlayer c = BukkitKit.join("C");
        c.moveTo(BukkitKit.world(), 5, 5);
        noDeaths.service.onDeath(c, hit(c, EntityDamageEvent.DamageCause.LAVA));
        BukkitKit.Rig noDanger = rig("danger.enabled", false);
        noDanger.service.onDeath(c, hit(c, EntityDamageEvent.DamageCause.LAVA));
        expect(noDeaths.store.facts.isEmpty() && noDanger.store.facts.isEmpty(), "death rumors can be switched off");
    }

    static void spawns() {
        BukkitKit.Rig rig = rig();
        LivingEntity warden = new LivingEntity();
        warden.stubSetType(EntityType.WARDEN);
        warden.stubSetLocation(at(100, 100));
        rig.service.onSpawn(warden);
        Fact f = rig.store.facts.get("f1");
        expect(f.detail.equals("a Warden") && f.extra.equals("spawn") && f.number == 1, "a Warden appears");
        rig.service.onSpawn(warden);
        expect(rig.store.facts.size() == 1 && f.number == 2, "seen again: one story, counted");
        LivingEntity zombie = new LivingEntity();
        zombie.stubSetType(EntityType.ZOMBIE);
        zombie.stubSetLocation(at(100, 100));
        rig.service.onSpawn(zombie);
        rig.service.onSpawn(null);
        expect(rig.store.facts.size() == 1, "zombies and nothing are not news");
        LivingEntity wither = new LivingEntity();
        wither.stubSetType(EntityType.WITHER);
        wither.stubSetLocation(at(100, 100));
        rig.service.onSpawn(wither);
        expect(rig.store.facts.get("f2").detail.equals("a Wither"), "the config's list decides");
        BukkitKit.Rig off = rig("danger.enabled", false);
        off.service.onSpawn(warden);
        expect(off.store.facts.isEmpty(), "and danger rumors can be switched off");
    }

    static void sightings() {
        BukkitKit.Rig rig = rig();
        rig.dice.always = 0.0;
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        ann.moveTo(BukkitKit.world(), 5, 5);
        rig.service.sampleSightings();
        expect(rig.store.facts.isEmpty(), "no villager nearby, nobody saw her");
        BukkitKit.villager(20, 20);
        rig.service.sampleSightings();
        Fact f = rig.store.facts.get("f1");
        expect(f != null && f.kind == Kind.SIGHTING && f.subject.equals("Ann") && f.subjectId.equals(ann.getUniqueId()) && f.region.equals(new Region("world", 0, 0)),
                "a villager nearby saw her");
        rig.service.sampleSightings();
        expect(rig.store.facts.size() == 1, "one at a time");

        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        bo.moveTo(BukkitKit.world(), 5, 5);
        bo.setGameMode(GameMode.CREATIVE);
        BukkitKit.TestPlayer cy = BukkitKit.join("Cy");
        cy.moveTo(BukkitKit.world(), 5, 5);
        cy.setGameMode(GameMode.SPECTATOR);
        BukkitKit.TestPlayer di = BukkitKit.join("Di");
        di.moveTo(BukkitKit.world(), 5, 5);
        rig.service.setHidden(di, true);
        BukkitKit.TestPlayer ed = BukkitKit.join("Ed");
        ed.moveTo(BukkitKit.world(), 500, 5);
        rig.service.sampleSightings();
        expect(rig.store.facts.size() == 1, "creative players, spectators, people who hid and people far from any villager are not noticed");
        ed.moveTo(BukkitKit.world(), 25, 5);
        rig.service.sampleSightings();
        expect(rig.store.facts.size() == 2 && rig.store.facts.get("f2").subject.equals("Ed"), "within 24 blocks");
        BukkitKit.TestPlayer fay = BukkitKit.join("Fay");
        fay.stubSetLocation(new Location(BukkitKit.nether(), 20, 70, 20));
        rig.service.sampleSightings();
        expect(rig.store.facts.size() == 2, "the Nether is not tracked");

        rig.dice.always = 0.9;
        BukkitKit.TestPlayer hal = BukkitKit.join("Hal");
        hal.moveTo(BukkitKit.world(), 10, 10);
        rig.service.sampleSightings();
        expect(rig.store.facts.size() == 2, "only now and then (chance 0.25)");

        BukkitKit.Rig anon = rig("sightings.name-players", false);
        anon.dice.always = 0.0;
        BukkitKit.TestPlayer ivy = BukkitKit.join("Ivy");
        ivy.moveTo(BukkitKit.world(), 5, 5);
        BukkitKit.villager(20, 20);
        anon.service.sampleSightings();
        Fact a = anon.store.facts.get("f1");
        expect(a.subject.isEmpty() && ivy.getUniqueId().equals(a.subjectId), "anonymous sightings still remember who, so only one at a time");
        BukkitKit.Rig off = rig("sightings.enabled", false);
        off.dice.always = 0.0;
        BukkitKit.TestPlayer jo = BukkitKit.join("Jo");
        jo.moveTo(BukkitKit.world(), 5, 5);
        BukkitKit.villager(20, 20);
        off.service.sampleSightings();
        expect(off.store.facts.isEmpty(), "sightings can be switched off");
    }

    /** Tells the service to put this fact into the villager region talk and returns it. */
    static Fact plant(BukkitKit.Rig rig, double x, double z, String detail) {
        return danger(rig, x, z, detail).fact();
    }

    static void hearing() {
        BukkitKit.Rig rig = rig("mutation.retell-chance", 0.0);
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        Villager v = BukkitKit.villager(10, 10, Temperament.PLAIN);
        expect(rig.service.hear(ann, v) == RumorService.HearResult.NOT_HANDLED && ann.messages.isEmpty(), "not sneaking: ordinary trading, not a word from us");
        ann.setSneaking(true);
        expect(rig.service.hear(ann, v) == RumorService.HearResult.NOTHING && ann.text().contains("Nothing worth repeating") && ann.text().contains("plain-spoken"),
                "sneaking at a villager who knows nothing");
        rig.seconds(100);
        Fact f = plant(rig, 0, 0, "lava");
        ann.clear();
        expect(rig.service.hear(ann, v) == RumorService.HearResult.HEARD, "now they have something");
        expect(ann.text().contains("Danger") && ann.text().contains("Someone died to lava in the plains, around spawn") && ann.text().contains("/rumors"), "and say it: " + ann.text());
        Heard h = rig.store.book(ann.getUniqueId()).heard.get(f.id);
        expect(h != null && h.versions.size() == 1 && h.versions.get(0).teller().equals("v:" + v.getUniqueId() + ":PLAIN") && h.versions.get(0).hops() == 1,
                "it is in her notes, from that villager, one step from the event");
        String first = ann.text();
        ann.clear();
        expect(rig.service.hear(ann, v) == RumorService.HearResult.COOLDOWN && ann.text().contains("Try again in 90s"), "asking again straight away");
        rig.seconds(30);
        ann.clear();
        rig.service.hear(ann, v);
        expect(ann.text().contains("Try again in 60s"), "the wait counts down");
        rig.seconds(59);
        expect(rig.service.hear(ann, v) == RumorService.HearResult.COOLDOWN, "one second early is still too early");
        rig.seconds(1);
        ann.clear();
        expect(rig.service.hear(ann, v) == RumorService.HearResult.TOLD_ALL, "and at exactly 90 seconds they'll talk again");
        rig.seconds(91);
        ann.clear();
        expect(rig.service.hear(ann, v) == RumorService.HearResult.TOLD_ALL && ann.text().contains("told you everything"), "after the wait there is nothing new to hear");

        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        bo.setSneaking(true);
        rig.service.hear(bo, v);
        expect(bo.text().equals(first), "the same villager tells everyone the same thing");

        // ordinary right-click needs no sneak when the server says so
        BukkitKit.Rig loose = rig("hear.require-sneak", false);
        BukkitKit.TestPlayer cy = BukkitKit.join("Cy");
        Villager w = BukkitKit.villager(10, 10);
        expect(loose.service.hear(cy, w) == RumorService.HearResult.NOTHING, "require-sneak: false takes any right-click");
        BukkitKit.Rig off = rig("enabled", false);
        BukkitKit.TestPlayer di = BukkitKit.join("Di");
        di.setSneaking(true);
        expect(off.service.hear(di, w) == RumorService.HearResult.NOT_HANDLED, "switched off: villagers just trade");
        BukkitKit.TestPlayer ed = BukkitKit.join("Ed");
        ed.setSneaking(true);
        Villager nether = new Villager();
        nether.stubSetLocation(new Location(BukkitKit.nether(), 0, 70, 0));
        expect(rig.service.hear(ed, nether) == RumorService.HearResult.NOT_HANDLED, "villagers in the Nether are not part of this");

        // expired news is not repeated
        BukkitKit.Rig old = rig();
        BukkitKit.TestPlayer fi = BukkitKit.join("Fi");
        fi.setSneaking(true);
        Villager x = BukkitKit.villager(10, 10, Temperament.PLAIN);
        plant(old, 0, 0, "lava");
        old.minutes(181);
        expect(old.service.hear(fi, x) == RumorService.HearResult.NOTHING, "a villager does not repeat what is over");
    }

    static void tellers() {
        // a gossip knows two things, others one
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        ann.setSneaking(true);
        Villager gossip = BukkitKit.villager(10, 10, Temperament.GOSSIP);
        Villager plain = BukkitKit.villager(10, 10, Temperament.PLAIN);
        plant(rig, 0, 0, "lava");
        plant(rig, 0, 0, "fire");
        plant(rig, 0, 0, "hunger");
        expect(rig.service.repertoire(gossip).size() == 2 && rig.service.repertoire(plain).size() == 1, "a gossip has two things to say, a plain villager one");
        expect(rig.service.repertoire(gossip).equals(rig.service.repertoire(gossip)), "and always the same two");
        rig.service.hear(ann, gossip);
        rig.seconds(100);
        rig.service.hear(ann, gossip);
        rig.seconds(100);
        expect(rig.store.book(ann.getUniqueId()).heard.size() == 2, "two asks, two different rumors");
        rig.seconds(1);
        ann.clear();
        expect(rig.service.hear(ann, gossip) == RumorService.HearResult.TOLD_ALL, "then they are out of news");
        expect(rig.service.entries(ann.getUniqueId()).size() == 2, "entries lists them");

        // the same story from a second villager is compared with the first
        BukkitKit.Rig same = rig("mutation.retell-chance", 0.0);
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        bo.setSneaking(true);
        Villager v1 = BukkitKit.villager(10, 10, Temperament.PLAIN);
        Villager v2 = BukkitKit.villager(10, 10, Temperament.PLAIN);
        plant(same, 0, 0, "lava");
        same.service.hear(bo, v1);
        bo.clear();
        same.service.hear(bo, v2);
        expect(bo.text().contains("Same story you already had"), "a second villager says the same: " + bo.text());
        expect(same.store.book(bo.getUniqueId()).heard.size() == 1 && same.store.book(bo.getUniqueId()).heard.get("f1").versions.size() == 2, "one rumor, two accounts");

        BukkitKit.Rig differ = rig("mutation.retell-chance", 1.0);
        BukkitKit.TestPlayer cy = BukkitKit.join("Cy");
        cy.setSneaking(true);
        Villager g1 = BukkitKit.villager(10, 10, Temperament.GOSSIP);
        Fact f = plant(differ, 0, 0, "lava");
        Rumor r = differ.store.mill(f.region).get(0);
        Telling t1 = Gossip.hear(r, Kind.DANGER, g1.getUniqueId(), Temperament.GOSSIP, 1.0);
        Villager g2 = null;
        for (int i = 0; i < 200 && g2 == null; i++) {
            Villager cand = BukkitKit.villager(10, 10, Temperament.GOSSIP);
            if (!Gossip.hear(r, Kind.DANGER, cand.getUniqueId(), Temperament.GOSSIP, 1.0).equals(t1)) {
                g2 = cand;
            }
        }
        expect(g2 != null, "found a second gossip who tells it differently");
        differ.service.hear(cy, g1);
        cy.clear();
        differ.service.hear(cy, g2);
        expect(cy.text().contains("doesn't quite match what you heard before"), "and the player is told it doesn't match: " + cy.text());
        Heard h = differ.store.book(cy.getUniqueId()).heard.get(f.id);
        expect(h.versions.size() == 2 && !h.unanimous() && h.consensus() != null, "the notes keep both");

        // memory is limited, and what you just learned is never what is forgotten
        BukkitKit.Rig small = rig("hear.max-remembered", 2);
        BukkitKit.TestPlayer di = BukkitKit.join("Di");
        di.setSneaking(true);
        for (int i = 0; i < 4; i++) {
            plant(small, 0, 0, "thing " + i);
        }
        Villager big = BukkitKit.villager(10, 10, Temperament.GOSSIP);
        List<Villager> many = new ArrayList<>();
        many.add(big);
        for (int i = 0; i < 5; i++) {
            many.add(BukkitKit.villager(10, 10, Temperament.GOSSIP));
        }
        for (Villager vv : many) {
            for (int i = 0; i < 3; i++) {
                small.seconds(100);
                small.service.hear(di, vv);
            }
        }
        expect(small.store.book(di.getUniqueId()).heard.size() == 2, "the notebook holds only the latest two: " + small.store.book(di.getUniqueId()).heard.size());

        // lowering the limit on a reload: what you have just heard is never what goes
        BukkitKit.Rig shrink = rig();
        BukkitKit.TestPlayer ed = BukkitKit.join("Ed");
        ed.setSneaking(true);
        Fact x = plant(shrink, 0, 0, "lava");
        Fact y = plant(shrink, 0, 0, "fire");
        learn(shrink, ed, x, 1).lastAt = shrink.now.get();
        learn(shrink, ed, y, 1).lastAt = shrink.now.get();
        YamlConfiguration one = BukkitKit.shipped();
        one.set("hear.max-remembered", 1);
        one.set("mutation.retell-chance", 0.0);
        shrink.service.setConfig(new RumorsConfig(one));
        Villager only = null;
        for (int i = 0; i < 100 && only == null; i++) {
            Villager cand = BukkitKit.villager(10, 10, Temperament.PLAIN);
            if (shrink.service.repertoire(cand).get(0).factId().equals(x.id)) {
                only = cand;
            }
        }
        expect(only != null, "found a villager who tells the first rumor");
        shrink.service.hear(ed, only);
        expect(shrink.store.book(ed.getUniqueId()).heard.keySet().equals(java.util.Set.of(x.id)), "the one just heard stays: " + shrink.store.book(ed.getUniqueId()).heard.keySet());
    }

    static void telling() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        Fact f = plant(rig, 0, 0, "lava");
        learn(rig, ann, f, 2);
        rig.dice.always = 0.9;
        expect(rig.service.tell(ann, "Bo", 1) == null, "Ann tells Bo");
        Heard h = rig.store.book(bo.getUniqueId()).heard.get(f.id);
        expect(h != null && h.versions.get(0).teller().equals("p:Ann") && h.versions.get(0).hops() == 3 && h.versions.get(0).telling().equals(f.exact()),
                "Bo has it, from Ann, one step further than her best source");
        expect(bo.text().contains("Ann tells you") && ann.text().contains("You pass it on to Bo"), "both are told");
        expect(rig.service.tell(ann, "Bo", 1).contains("only just spoken"), "a cooldown on talking");
        rig.seconds(301);
        expect(rig.service.tell(ann, "Bo", 1) == null && rig.store.book(bo.getUniqueId()).heard.get(f.id).versions.size() == 1,
                "telling the same thing again adds nothing new");
        rig.seconds(301);
        rig.dice.always = null;
        rig.dice.next(0.0);
        expect(rig.service.tell(ann, "Bo", 1) == null && rig.store.book(bo.getUniqueId()).heard.get(f.id).versions.size() == 2,
                "but a retelling that slips is a second account");

        expect(rig.service.tell(ann, "Bo", 5).contains("haven't heard a rumor #5") && rig.service.tell(ann, "Bo", 0).contains("haven't heard"), "bad number");
        expect(rig.service.tell(ann, "Nobody", 1).contains("Nobody called Nobody"), "no such player");
        expect(rig.service.tell(ann, null, 1).contains("Nobody called null"), "no name");
        expect(rig.service.tell(ann, "Ann", 1).contains("already know"), "not yourself");
        bo.moveTo(BukkitKit.world(), 100, 0);
        rig.seconds(301);
        expect(rig.service.tell(ann, "Bo", 1).contains("too far"), "out of earshot");
        bo.stubSetLocation(new Location(BukkitKit.nether(), 0, 70, 0));
        expect(rig.service.tell(ann, "Bo", 1).contains("too far"), "another world is too far");
    }

    /** Puts the exact fact into a player's notes as if a villager {@code hops} steps away had told her. */
    static Heard learn(BukkitKit.Rig rig, BukkitKit.TestPlayer p, Fact f, int hops) {
        Heard h = rig.store.book(p.getUniqueId()).heard.computeIfAbsent(f.id, k -> new Heard(f.id, f.kind));
        h.add(new Heard.Version("v:" + UUID.randomUUID() + ":PLAIN", hops, rig.now.get(), f.exact()), 6);
        return h;
    }

    static void posting() {
        BukkitKit.Rig rig = rig();
        rig.dice.always = 0.9;
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        Fact f = plant(rig, 400, 0, "lava");
        learn(rig, ann, f, 1);
        expect(rig.service.post(ann, 1).contains("no villager close enough"), "no villager near");
        Villager v = BukkitKit.villager(20, 20);
        expect(rig.service.post(ann, 1) == null, "posting to a villager");
        List<Rumor> mill = rig.store.mill(new Region("world", 0, 0));
        expect(mill.size() == 1 && mill.get(0).factId().equals(f.id) && mill.get(0).hops() == 2 && mill.get(0).telling().equals(f.exact()),
                "it enters the villagers' talk here, two steps from the event");
        expect(ann.text().contains("You tell the villagers"), "and she is told");
        expect(rig.service.post(ann, 1).contains("already told"), "once per rumor");
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        learn(rig, bo, f, 1);
        expect(rig.service.post(bo, 1).contains("already know"), "if the villagers have it, there is nothing to add");
        expect(!rig.store.book(bo.getUniqueId()).posted.contains(f.id), "and that doesn't count against Bo");
        expect(rig.service.post(bo, 9).contains("haven't heard"), "bad number");
        rig.minutes(181);
        BukkitKit.TestPlayer cy = BukkitKit.join("Cy");
        learn(rig, cy, f, 1);
        expect(rig.service.post(cy, 1).contains("old news"), "nobody wants stale news");
        Heard gone = new Heard("f404", Kind.DANGER);
        gone.add(ver(f.exact()), 6);
        rig.store.book(cy.getUniqueId()).heard.put("f404", gone);
        expect(rig.service.entries(cy.getUniqueId()).size() == 2 && rig.service.post(cy, 2).contains("old news"), "nor news nobody remembers");
        BukkitKit.TestPlayer di = BukkitKit.join("Di");
        di.stubSetLocation(new Location(BukkitKit.nether(), 0, 70, 0));
        Fact g = plant(rig, 400, 0, "fire");
        learn(rig, di, g, 1);
        expect(rig.service.post(di, 1).contains("no villager"), "no villagers to tell in the Nether");
        // and a villager somewhere only the Nether
        Villager far = new Villager();
        far.stubSetLocation(new Location(BukkitKit.nether(), 5, 70, 5));
        Bukkit.ENTITIES.put(far.getUniqueId(), far);
        expect(rig.service.post(di, 1).contains("no villager"), "a Nether villager does not count");
    }

    static Heard.Version ver(Telling t) {
        return new Heard.Version("v:x:PLAIN", 1, 0, t);
    }

    static void spreading() {
        BukkitKit.Rig rig = rig("spread.chance", 1.0);
        rig.dice.always = 0.0;
        Fact f = plant(rig, 0, 0, "lava");
        YamlConfiguration y = BukkitKit.shipped();
        y.set("spread.chance", 1.0);
        y.set("spread.mutation-chance", 0.0);
        y.set("spread.max-hops", 2);
        rig.service.setConfig(new RumorsConfig(y));
        int moved = rig.service.spread();
        expect(moved == 1 && rig.store.rumorCount() == 2, "the rumor crosses into one neighbouring region: " + moved);
        Rumor copy = null;
        Region at = null;
        for (var e : rig.store.mills.entrySet()) {
            if (!e.getKey().equals(new Region("world", 0, 0).key())) {
                copy = e.getValue().get(0);
                at = Region.parse(e.getKey());
            }
        }
        expect(copy != null && copy.hops() == 1 && copy.telling().equals(f.exact()) && at.ring() == 1, "one hop on, unchanged (mutation 0)");
        rig.service.spread();
        rig.service.spread();
        rig.service.spread();
        int maxHops = 0;
        for (var e : rig.store.mills.values()) {
            for (Rumor r : e) {
                maxHops = Math.max(maxHops, r.hops());
            }
        }
        expect(maxHops == 2, "and never further than max-hops: " + maxHops);
        for (var e : rig.store.mills.values()) {
            expect(e.stream().map(Rumor::factId).distinct().count() == e.size(), "no region hears the same fact twice");
        }

        // it changes on the way
        BukkitKit.Rig drift = rig();
        drift.dice.always = 0.0;
        Fact g = plant(drift, 0, 0, "lava");
        YamlConfiguration z = BukkitKit.shipped();
        z.set("spread.chance", 1.0);
        z.set("spread.mutation-chance", 1.0);
        drift.service.setConfig(new RumorsConfig(z));
        drift.service.spread();
        boolean changed = false;
        for (var e : drift.store.mills.entrySet()) {
            for (Rumor r : e.getValue()) {
                if (r.hops() == 1 && !r.telling().equals(g.exact())) {
                    changed = true;
                }
            }
        }
        expect(changed, "with mutation-chance 1 the telling changes in transit");

        // chance gate
        BukkitKit.Rig none = rig();
        none.dice.always = 0.9;
        plant(none, 0, 0, "lava");
        expect(none.service.spread() == 0 && none.store.rumorCount() == 1, "usually it stays put (chance 0.35)");

        // expired stories do not travel
        BukkitKit.Rig dead = rig();
        dead.dice.always = 0.0;
        plant(dead, 0, 0, "lava");
        dead.service.setConfig(new RumorsConfig(z));
        dead.minutes(181);
        expect(dead.service.spread() == 0, "news that is over does not spread");
        // a rumor whose fact was lost does not either
        dead.store.facts.clear();
        expect(dead.service.spread() == 0, "nor one about an unknown fact");
        // garbage mill keys are ignored
        dead.store.mills.put("garbage", new ArrayList<>(List.of(new Rumor("f1", ModelTest.market(new Region("world", 0, 0), 1), 0))));
        expect(dead.service.spread() == 0, "damaged region keys are ignored");
    }

    static void upkeep() {
        BukkitKit.Rig rig = rig();
        rig.dice.always = 0.0;
        plant(rig, 0, 0, "lava");
        YamlConfiguration y = BukkitKit.shipped();
        y.set("spread.chance", 1.0);
        rig.service.setConfig(new RumorsConfig(y));
        rig.minutes(5);
        rig.service.upkeep();
        expect(rig.store.rumorCount() == 1, "before the interval, nothing moves");
        rig.minutes(5);
        rig.service.upkeep();
        expect(rig.store.rumorCount() == 2, "after ten minutes it spreads");
        rig.service.upkeep();
        expect(rig.store.rumorCount() == 2, "and waits another ten");

        // purge
        BukkitKit.Rig p = rig();
        plant(p, 0, 0, "lava");
        Fact m = p.service.report(Kind.MARKET, at(400, 0), "a Mending book", "librarian", 3, "", null).fact();
        p.minutes(181);
        p.service.purge();
        expect(p.store.facts.size() == 2 && p.store.rumorCount() == 1, "an over danger leaves the talk but stays on record for a while");
        expect(!p.store.mills.containsKey(new Region("world", 0, 0).key()), "and an empty region's talk is dropped");
        p.minutes(121);
        p.service.purge();
        expect(p.store.facts.size() == 1 && p.store.facts.containsKey(m.id), "later still it is forgotten, but the market story lives on (12 hours)");
        p.service.saveIfDirty();
        expect(!p.store.dirty(), "saveIfDirty saves");
        long stamp = new java.io.File(p.dir, "facts.yml").lastModified();
        p.service.saveIfDirty();
        expect(new java.io.File(p.dir, "facts.yml").lastModified() == stamp, "and only when something changed");
    }

    static void checking() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        Fact f = plant(rig, 0, 0, "lava");
        Heard h = learn(rig, ann, f, 1);
        ann.moveTo(BukkitKit.world(), 300, 0);
        rig.service.checkTick();
        expect(ann.messages.isEmpty(), "elsewhere, nothing happens");
        ann.moveTo(BukkitKit.world(), 5, 5);
        rig.service.checkTick();
        expect(ann.text().contains("You're where the rumor says") && ann.text().contains("around spawn") && ann.text().contains("15 seconds"), "arriving: you're told to stay: " + ann.text());
        ann.clear();
        rig.service.checkTick();
        expect(ann.messages.isEmpty(), "no nagging");
        ann.moveTo(BukkitKit.world(), 300, 0);
        rig.service.checkTick();
        ann.moveTo(BukkitKit.world(), 5, 5);
        rig.service.checkTick();
        rig.service.checkTick();
        expect(rig.store.book(ann.getUniqueId()).heard.containsKey(f.id), "leaving starts the count again");
        rig.service.checkTick();
        expect(!rig.store.book(ann.getUniqueId()).heard.containsKey(f.id), "fifteen seconds in the right place settles it");
        expect(ann.text().contains("It was true") && ann.text().contains("+30 experience") && ann.xpGiven == 30, "true: " + ann.text());
        RumorStore.Book b = rig.store.book(ann.getUniqueId());
        expect(b.trueCount == 1 && b.checked() == 1, "the record");

        // exaggerated: the right place, a stretched number
        BukkitKit.Rig r2 = rig();
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        bo.moveTo(BukkitKit.world(), 5, 5);
        Fact g = plant(r2, 0, 0, "lava");
        Heard hb = new Heard(g.id, Kind.DANGER);
        hb.add(ver(g.exact().withNumber(5)), 6);
        r2.store.book(bo.getUniqueId()).heard.put(g.id, hb);
        Verdict v = r2.service.resolve(bo, hb);
        expect(v == Verdict.EXAGGERATED && bo.xpGiven == 10 && bo.text().contains("grew in the telling") && bo.text().contains("What really happened"), "exaggerated: " + bo.text());
        expect(r2.store.book(bo.getUniqueId()).exaggeratedCount == 1, "counted");

        // false: wrong place
        BukkitKit.Rig r3 = rig();
        BukkitKit.TestPlayer cy = BukkitKit.join("Cy");
        Fact h3 = plant(r3, 0, 0, "lava");
        Heard hc = new Heard(h3.id, Kind.DANGER);
        hc.add(ver(h3.exact().withRegion(new Region("world", 1, 0))), 6);
        r3.store.book(cy.getUniqueId()).heard.put(h3.id, hc);
        cy.moveTo(BukkitKit.world(), 192, 0);
        r3.service.checkTick();
        r3.service.checkTick();
        r3.service.checkTick();
        expect(r3.store.book(cy.getUniqueId()).falseCount == 1 && cy.xpGiven == 0 && cy.text().contains("Nothing like that here") && !cy.text().contains("experience"), "false: " + cy.text());
        expect(!cy.text().contains("What really happened"), "and it doesn't give away what was true: " + cy.text());

        // stale
        BukkitKit.Rig r4 = rig();
        BukkitKit.TestPlayer di = BukkitKit.join("Di");
        di.moveTo(BukkitKit.world(), 5, 5);
        Fact s = plant(r4, 0, 0, "lava");
        learn(r4, di, s, 1);
        r4.minutes(181);
        r4.service.checkTick();
        r4.service.checkTick();
        r4.service.checkTick();
        expect(r4.store.book(di.getUniqueId()).staleCount == 1 && di.text().contains("Too late") && di.xpGiven == 0, "stale: " + di.text());
        expect(Texts.ledger("Di", r4.store.book(di.getUniqueId())).size() == 2, "a stale record still shows");

        // rewards by command
        BukkitKit.Rig r5 = rig("rewards.commands-true", List.of("give %player% diamond", " "));
        BukkitKit.TestPlayer ed = BukkitKit.join("Ed");
        ed.moveTo(BukkitKit.world(), 5, 5);
        Fact k = plant(r5, 0, 0, "lava");
        learn(r5, ed, k, 1);
        for (int i = 0; i < 3; i++) {
            r5.service.checkTick();
        }
        expect(Bukkit.DISPATCHED.equals(List.of("give Ed diamond")), "a true rumor can pay in items: " + Bukkit.DISPATCHED);
        Bukkit.DISPATCHED.clear();
        BukkitKit.Rig r6 = rig("rewards.commands-true", List.of("give %player% diamond"));
        BukkitKit.TestPlayer fi = BukkitKit.join("Fi");
        fi.moveTo(BukkitKit.world(), 5, 5);
        Fact k2 = plant(r6, 0, 0, "lava");
        Heard hf = new Heard(k2.id, Kind.DANGER);
        hf.add(ver(k2.exact().withNumber(9)), 6);
        r6.store.book(fi.getUniqueId()).heard.put(k2.id, hf);
        for (int i = 0; i < 3; i++) {
            r6.service.checkTick();
        }
        expect(Bukkit.DISPATCHED.isEmpty(), "but not an exaggerated one");

        // forgetting a rumor and hearing it again starts the count from nothing
        BukkitKit.Rig r8 = rig();
        BukkitKit.TestPlayer ki = BukkitKit.join("Ki");
        ki.moveTo(BukkitKit.world(), 5, 5);
        Fact k8 = plant(r8, 0, 0, "lava");
        learn(r8, ki, k8, 1);
        learn(r8, ki, plant(r8, 400, 0, "fire"), 1);
        r8.service.checkTick();
        r8.service.checkTick();
        r8.store.book(ki.getUniqueId()).heard.remove(k8.id);
        r8.service.checkTick();
        learn(r8, ki, k8, 1);
        r8.service.checkTick();
        r8.service.checkTick();
        expect(r8.store.book(ki.getUniqueId()).heard.containsKey(k8.id), "an earlier visit doesn't count towards a rumor heard anew");
        r8.service.checkTick();
        expect(!r8.store.book(ki.getUniqueId()).heard.containsKey(k8.id), "but fifteen seconds of this one does");

        // players with nothing to check, in other worlds, or nowhere
        BukkitKit.Rig r7 = rig();
        BukkitKit.TestPlayer gi = BukkitKit.join("Gi");
        r7.service.checkTick();
        BukkitKit.TestPlayer hi = BukkitKit.join("Hi");
        Fact k3 = plant(r7, 0, 0, "lava");
        learn(r7, hi, k3, 1);
        hi.stubSetLocation(new Location(BukkitKit.nether(), 0, 70, 0));
        for (int i = 0; i < 4; i++) {
            r7.service.checkTick();
        }
        expect(r7.store.book(hi.getUniqueId()).heard.containsKey(k3.id) && gi.messages.isEmpty() && hi.messages.isEmpty(), "nothing in the Nether, and no trouble for empty notebooks");
        BukkitKit.Rig off = rig("enabled", false);
        BukkitKit.TestPlayer jo = BukkitKit.join("Jo");
        jo.moveTo(BukkitKit.world(), 5, 5);
        learn(off, jo, k3, 1);
        for (int i = 0; i < 4; i++) {
            off.service.checkTick();
        }
        expect(!off.store.book(jo.getUniqueId()).heard.isEmpty(), "switched off, no checking");
    }

    static void notebook() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        Fact a = plant(rig, 0, 0, "lava");
        rig.minutes(1);
        Fact b = plant(rig, 400, 0, "fire");
        rig.minutes(1);
        Fact c = plant(rig, 800, 0, "hunger");
        Heard ha = learn(rig, ann, a, 1);
        ha.lastAt = 10;
        Heard hb = learn(rig, ann, b, 1);
        hb.lastAt = 30;
        Heard hc = learn(rig, ann, c, 1);
        hc.lastAt = 20;
        expect(rig.service.entries(ann.getUniqueId()).stream().map(h -> h.factId).toList().equals(List.of(b.id, c.id, a.id)), "newest first");
        expect(!rig.service.forget(ann.getUniqueId(), 0) && !rig.service.forget(ann.getUniqueId(), 4), "forgetting a rumor that isn't there");
        expect(rig.service.forget(ann.getUniqueId(), 1) && rig.service.entries(ann.getUniqueId()).size() == 2
                && !rig.store.book(ann.getUniqueId()).heard.containsKey(b.id), "forget by number");
        expect(rig.service.forgetAll(ann.getUniqueId()) == 2 && rig.service.entries(ann.getUniqueId()).isEmpty(), "forget all");
        expect(rig.service.forgetAll(UUID.randomUUID()) == 0, "forgetting nothing");

        // hiding takes sightings out of circulation
        BukkitKit.Rig h = rig();
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        Fact s = h.service.report(Kind.SIGHTING, at(0, 0), "", "", 0, "Bo", bo.getUniqueId()).fact();
        Fact other = h.service.report(Kind.SIGHTING, at(0, 0), "", "", 0, "Cy", UUID.randomUUID()).fact();
        Fact lava = plant(h, 0, 0, "lava");
        expect(!h.service.isHidden(bo.getUniqueId()), "visible by default");
        h.service.setHidden(bo, true);
        expect(h.service.isHidden(bo.getUniqueId()) && !h.store.facts.containsKey(s.id) && h.store.facts.containsKey(other.id)
                && h.store.mill(new Region("world", 0, 0)).size() == 2 && h.store.facts.containsKey(lava.id),
                "hiding removes only her own sighting from the villagers' talk");
        h.service.setHidden(bo, false);
        expect(!h.service.isHidden(bo.getUniqueId()) && !h.service.isHidden(UUID.randomUUID()), "and she can show again");
        expect(h.store.book(bo.getUniqueId()).name.equals("Bo"), "her name is on her record");
    }
}
