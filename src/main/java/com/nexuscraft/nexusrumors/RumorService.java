package com.nexuscraft.nexusrumors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;

/** The whole gossip economy: what happens becomes facts, facts become rumors, rumors spread, players hear them and go and look. */
final class RumorService {

    private static final int[][] AROUND = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    private RumorsConfig config;
    private final RumorStore store;
    private final LongSupplier clock;
    private final Random rnd;
    private final Map<String, Long> hearUntil = new HashMap<>();
    private final Map<UUID, Long> tellUntil = new HashMap<>();
    private final Map<UUID, Long> tradeUntil = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> dwell = new HashMap<>();
    private long lastSpread;

    RumorService(RumorsConfig config, RumorStore store, LongSupplier clock, Random rnd) {
        this.config = config;
        this.store = store;
        this.clock = clock;
        this.rnd = rnd;
        this.lastSpread = clock.getAsLong();
    }

    RumorsConfig config() {
        return config;
    }

    void setConfig(RumorsConfig config) {
        this.config = config;
    }

    RumorStore store() {
        return store;
    }

    long now() {
        return clock.getAsLong();
    }

    // ---------------------------------------------------------------- places

    private boolean worldOk(World w) {
        return w != null && config.worldAllowed(w.getName(), w.getEnvironment() == World.Environment.NORMAL);
    }

    private int[] origin(World w) {
        int[] o = store.origins.get(w.getName());
        if (o == null) {
            Location s = w.getSpawnLocation();
            o = s == null ? new int[]{0, 0} : new int[]{s.getBlockX(), s.getBlockZ()};
            store.origins.put(w.getName(), o);
            store.touch();
        }
        return o;
    }

    Region regionAt(Location at) {
        int[] o = origin(at.getWorld());
        return Grid.regionOf(at.getWorld().getName(), at.getX(), at.getZ(), o[0], o[1], config.regionSize);
    }

    private static String biomeAt(Location at) {
        try {
            return at.getWorld().getBiome(at.getBlockX(), at.getBlockY(), at.getBlockZ()).getKey().getKey();
        } catch (RuntimeException unknown) {
            return "";
        }
    }

    // ---------------------------------------------------------------- facts and rumors

    /**
     * Records that something happened. The same thing happening again in the same place within its lifetime adds to one story
     * (a death toll, a count of sightings) instead of starting another. Says which story it joined, and whether that is something new.
     */
    Reported report(Kind kind, Location at, String detail, String extra, int number, String subject, UUID subjectId) {
        if (!config.enabled || at == null || !worldOk(at.getWorld())) {
            return Reported.NONE;
        }
        long now = now();
        Region region = regionAt(at);
        for (Fact f : store.facts.values()) {
            if (f.expired(now)) {
                continue;
            }
            if (kind == Kind.SIGHTING && f.kind == Kind.SIGHTING && subjectId != null && subjectId.equals(f.subjectId)) {
                return new Reported(f, false);
            }
            if (kind != Kind.SIGHTING && f.sameStory(kind, region, detail, extra)) {
                if (kind == Kind.DANGER) {
                    f.expiresAt = now + config.ttl(kind);
                    store.touch();
                    if (number > 0) {
                        f.number += number;
                        refreshHome(f);
                        return new Reported(f, true);
                    }
                }
                return new Reported(f, false);
            }
        }
        Fact f = new Fact("f" + store.nextFact++, kind, region, biomeAt(at), detail, extra, number, subject, subjectId,
                now, now + config.ttl(kind));
        store.facts.put(f.id, f);
        plant(region, new Rumor(f.id, f.exact(), 0));
        store.touch();
        return new Reported(f, true);
    }

    /** What {@link #report} did: the story it ended up in (null if not recorded) and whether anything new was added to it. */
    record Reported(Fact fact, boolean changed) {
        static final Reported NONE = new Reported(null, false);
    }

    /** The talk in the fact's own region should tell the story as it now stands. */
    private void refreshHome(Fact f) {
        List<Rumor> mill = store.mill(f.region);
        for (int i = 0; i < mill.size(); i++) {
            if (mill.get(i).factId().equals(f.id) && mill.get(i).hops() == 0) {
                mill.set(i, new Rumor(f.id, f.exact(), 0));
            }
        }
    }

    /** Puts a rumor into a region's talk, unless they already have that story. */
    private boolean plant(Region region, Rumor r) {
        List<Rumor> mill = store.mill(region);
        for (Rumor have : mill) {
            if (have.factId().equals(r.factId())) {
                return false;
            }
        }
        mill.add(r);
        while (mill.size() > config.maxPerRegion) {
            mill.remove(0);
        }
        Iterator<List<Rumor>> it = store.mills.values().iterator();
        while (store.rumorCount() > config.maxTotal && it.hasNext()) {
            List<Rumor> l = it.next();
            if (!l.isEmpty() && l != mill) {
                l.remove(0);
            }
        }
        store.touch();
        return true;
    }

    // ---------------------------------------------------------------- sensors

    /** A villager or wandering trader gained a trade. */
    void onTrade(AbstractVillager villager, MerchantRecipe recipe) {
        if (!config.enabled || !config.market || villager == null || recipe == null) {
            return;
        }
        long now = now();
        Long until = tradeUntil.get(villager.getUniqueId());
        if (until != null && now < until) {
            return;
        }
        ItemStack result = recipe.getResult();
        String detail = notable(result);
        if (detail == null || rnd.nextDouble() >= config.marketChance) {
            return;
        }
        String who = villager instanceof Villager v ? v.getProfession().getKey().getKey().replace('_', ' ') : "wandering trader";
        if (who.equals("none")) {
            who = "trader";
        }
        int price = 0;
        for (ItemStack in : recipe.getIngredients()) {
            if (in != null && in.getType() == Material.EMERALD) {
                price += in.getAmount();
            }
        }
        if (report(Kind.MARKET, villager.getLocation(), detail, who, price, "", null).changed()) {
            tradeUntil.put(villager.getUniqueId(), now + config.marketVillagerCooldownMillis);
        }
    }

    /** "a Mending book" / "a Diamond Pickaxe", or null when the item is not worth talking about. */
    private String notable(ItemStack item) {
        if (item == null) {
            return null;
        }
        if (item.getType() == Material.ENCHANTED_BOOK) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof EnchantmentStorageMeta book) {
                for (Map.Entry<Enchantment, Integer> e : book.getStoredEnchants().entrySet()) {
                    String key = e.getKey().getKey().getKey();
                    if (config.notableEnchants.contains(key)) {
                        String roman = Tale.roman(e.getValue());
                        return "a " + Tale.title(key) + (roman.isEmpty() ? "" : " " + roman) + " book";
                    }
                }
            }
            return null;
        }
        return config.notableMaterials.contains(item.getType()) ? Tale.nameWithArticle(item.getType().name()) : null;
    }

    /** A player died. */
    void onDeath(Player player, EntityDamageEvent last) {
        if (!config.enabled || !config.danger || !config.deaths) {
            return;
        }
        String cause = deathCause(last);
        if (cause == null) {
            return;
        }
        boolean named = config.nameVictims && !book(player).hidden;
        report(Kind.DANGER, player.getLocation(), cause, "death", 1, named ? player.getName() : "", null);
    }

    private String deathCause(EntityDamageEvent last) {
        if (last == null) {
            return "something nasty";
        }
        if (last instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() != null) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Player && !config.pvp) {
                return null;
            }
            return Tale.nameWithArticle(damager.getType().name());
        }
        return switch (last.getCause()) {
            case LAVA -> "lava";
            case FIRE, FIRE_TICK, HOT_FLOOR -> "fire";
            case FALL, FLY_INTO_WALL -> "a long fall";
            case DROWNING -> "deep water";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "an explosion";
            case PROJECTILE -> "an arrow";
            case STARVATION -> "hunger";
            case VOID -> "the void";
            case SUFFOCATION, FALLING_BLOCK, CRAMMING -> "a collapse";
            case LIGHTNING -> "lightning";
            case WITHER -> "the withering";
            case FREEZE -> "the cold";
            case POISON, MAGIC, DRAGON_BREATH -> "dark magic";
            case SONIC_BOOM -> "a Warden";
            case SUICIDE, KILL -> null;
            default -> "something nasty";
        };
    }

    /** A creature appeared. */
    void onSpawn(LivingEntity mob) {
        if (!config.enabled || !config.danger || mob == null || !config.dangerMobs.contains(mob.getType())) {
            return;
        }
        report(Kind.DANGER, mob.getLocation(), Tale.nameWithArticle(mob.getType().name()), "spawn", 1, "", null);
    }

    /** Players seen near villagers become rumors about where they were, by region only. */
    void sampleSightings() {
        if (!config.enabled || !config.sightings) {
            return;
        }
        double r = config.sightingRadius;
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (!worldOk(p.getWorld()) || book(p).hidden
                    || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            if (rnd.nextDouble() >= config.sightingChance) {
                continue;
            }
            boolean witnessed = false;
            for (Entity e : p.getNearbyEntities(r, r, r)) {
                if (e instanceof Villager) {
                    witnessed = true;
                    break;
                }
            }
            if (witnessed) {
                report(Kind.SIGHTING, p.getLocation(), "", "", 0, config.namePlayers ? p.getName() : "", p.getUniqueId());
            }
        }
    }

    // ---------------------------------------------------------------- hearing

    private RumorStore.Book book(Player p) {
        RumorStore.Book b = store.book(p.getUniqueId());
        if (!p.getName().equals(b.name)) {
            b.name = p.getName();
            store.touch();
        }
        return b;
    }

    /** Heard-rumors, newest first: the numbering every command uses. */
    List<Heard> entries(UUID player) {
        List<Heard> list = new ArrayList<>(store.book(player).heard.values());
        list.sort(Comparator.comparingLong((Heard h) -> h.lastAt).reversed());
        return list;
    }

    /** What a villager has to tell: the rumors in their region, in an order only they have, as many as their temperament allows. */
    List<Rumor> repertoire(Villager villager) {
        Region region = regionAt(villager.getLocation());
        Temperament temper = Temperament.of(villager.getUniqueId());
        UUID id = villager.getUniqueId();
        List<Rumor> known = new ArrayList<>();
        long now = now();
        for (Rumor r : store.mill(region)) {
            Fact f = store.facts.get(r.factId());
            if (f != null && !f.expired(now)) {
                known.add(r);
            }
        }
        known.sort(Comparator.comparingLong((Rumor r) -> Seeds.mix(id.getMostSignificantBits(), id.getLeastSignificantBits(), Seeds.of(r.factId()))));
        return known.size() > temper.tells() ? new ArrayList<>(known.subList(0, temper.tells())) : known;
    }

    /** Result of asking a villager. */
    enum HearResult { NOT_HANDLED, COOLDOWN, NOTHING, TOLD_ALL, HEARD }

    /**
     * A player sneaks up to a villager and asks. Returns NOT_HANDLED when this was not a request for gossip at all
     * (so trading carries on as normal).
     */
    HearResult hear(Player player, Villager villager) {
        if (!config.enabled || !worldOk(villager.getWorld())) {
            return HearResult.NOT_HANDLED;
        }
        if (config.requireSneak && !player.isSneaking()) {
            return HearResult.NOT_HANDLED;
        }
        long now = now();
        String key = player.getUniqueId() + "/" + villager.getUniqueId();
        Long until = hearUntil.get(key);
        if (until != null && now < until) {
            Texts.send(player, Texts.cooldown((until - now + 999) / 1000));
            return HearResult.COOLDOWN;
        }
        hearUntil.put(key, now + config.hearCooldownMillis);
        Temperament temper = Temperament.of(villager.getUniqueId());
        List<Rumor> repertoire = repertoire(villager);
        if (repertoire.isEmpty()) {
            Texts.send(player, Texts.nothingToSay(temper));
            return HearResult.NOTHING;
        }
        RumorStore.Book book = book(player);
        String teller = "v:" + villager.getUniqueId() + ":" + temper.name();
        for (Rumor r : repertoire) {
            Heard have = book.heard.get(r.factId());
            if (have != null && told(have, teller)) {
                continue;
            }
            Fact fact = store.facts.get(r.factId());
            Telling said = Gossip.hear(r, fact.kind, villager.getUniqueId(), temper, config.retellChance);
            Telling before = have == null ? null : have.consensus();
            learn(book, fact.id, fact.kind, new Heard.Version(teller, r.hops() + 1, now, said));
            long seed = Seeds.mix(villager.getUniqueId().getMostSignificantBits(), Seeds.of(r.factId()));
            Texts.send(player, Texts.told(temper.opener(seed), fact.kind, said, before, have != null));
            return HearResult.HEARD;
        }
        Texts.send(player, Texts.toldAll(temper));
        return HearResult.TOLD_ALL;
    }

    private static boolean told(Heard h, String teller) {
        for (Heard.Version v : h.versions) {
            if (v.teller().equals(teller)) {
                return true;
            }
        }
        return false;
    }

    private void learn(RumorStore.Book book, String factId, Kind kind, Heard.Version v) {
        Heard h = book.heard.computeIfAbsent(factId, k -> new Heard(factId, kind));
        h.add(v, config.maxVersions);
        while (book.heard.size() > config.maxRemembered) {
            String oldest = null;
            long at = Long.MAX_VALUE;
            for (Heard x : book.heard.values()) {
                if (x.lastAt < at && !x.factId.equals(factId)) {
                    at = x.lastAt;
                    oldest = x.factId;
                }
            }
            if (oldest == null) {
                break;
            }
            book.heard.remove(oldest);
        }
        store.touch();
    }

    // ---------------------------------------------------------------- passing rumors on

    /** /rumors tell: returns what went wrong, or null after telling. */
    String tell(Player from, String toName, int number) {
        List<Heard> list = entries(from.getUniqueId());
        if (number < 1 || number > list.size()) {
            return "You haven't heard a rumor #" + number + ". See /rumors.";
        }
        Player to = toName == null ? null : Bukkit.getPlayer(toName);
        if (to == null) {
            return "Nobody called " + toName + " is here to tell.";
        }
        if (to.getUniqueId().equals(from.getUniqueId())) {
            return "You already know that one.";
        }
        if (!from.getWorld().getName().equals(to.getWorld().getName())
                || from.getLocation().distance(to.getLocation()) > config.tellRadius) {
            return to.getName() + " is too far away to hear you.";
        }
        long now = now();
        Long until = tellUntil.get(from.getUniqueId());
        if (until != null && now < until) {
            return "You've only just spoken. Give it " + ((until - now + 999) / 1000) + " more seconds.";
        }
        Heard h = list.get(number - 1);
        Telling said = Gossip.pass(h.consensus(), h.kind, rnd, config.tellMutation);
        int hops = Integer.MAX_VALUE;
        for (Heard.Version v : h.versions) {
            hops = Math.min(hops, v.hops());
        }
        tellUntil.put(from.getUniqueId(), now + config.tellCooldownMillis);
        learn(book(to), h.factId, h.kind, new Heard.Version("p:" + from.getName(), hops + 1, now, said));
        Texts.send(to, Texts.fromPlayer(from.getName(), h.kind, said));
        Texts.send(from, "§7You pass it on to §f" + to.getName() + "§7. Whether they believe it is up to them.");
        return null;
    }

    /** /rumors post: tells the nearest villager, so it starts going round their region. Returns what went wrong, or null. */
    String post(Player from, int number) {
        List<Heard> list = entries(from.getUniqueId());
        if (number < 1 || number > list.size()) {
            return "You haven't heard a rumor #" + number + ". See /rumors.";
        }
        Heard h = list.get(number - 1);
        RumorStore.Book book = book(from);
        Fact fact = store.facts.get(h.factId);
        long now = now();
        if (fact == null || fact.expired(now)) {
            return "That's old news. Nobody wants to hear it.";
        }
        if (book.posted.contains(h.factId)) {
            return "You've already told the villagers that one.";
        }
        Villager near = null;
        double best = Double.MAX_VALUE;
        double r = config.postRadius;
        for (Entity e : from.getNearbyEntities(r, r, r)) {
            if (e instanceof Villager v) {
                double d = v.getLocation().distanceSquared(from.getLocation());
                if (d < best) {
                    best = d;
                    near = v;
                }
            }
        }
        if (near == null || !worldOk(near.getWorld())) {
            return "There's no villager close enough to tell.";
        }
        int hops = Integer.MAX_VALUE;
        for (Heard.Version v : h.versions) {
            hops = Math.min(hops, v.hops());
        }
        Telling said = Gossip.pass(h.consensus(), h.kind, rnd, config.tellMutation);
        if (!plant(regionAt(near.getLocation()), new Rumor(h.factId, said, hops + 1))) {
            return "The villagers here already know that one.";
        }
        book.posted.add(h.factId);
        store.touch();
        Texts.send(from, "§7You tell the villagers. It will go round the " + Temperament.of(near.getUniqueId()).adjective()
                + " lot here and may travel from there.");
        return null;
    }

    /** /rumors forget: drops one rumor from the notebook (false if there is no such number). */
    boolean forget(UUID player, int number) {
        List<Heard> list = entries(player);
        if (number < 1 || number > list.size()) {
            return false;
        }
        store.book(player).heard.remove(list.get(number - 1).factId);
        store.touch();
        return true;
    }

    int forgetAll(UUID player) {
        RumorStore.Book b = store.book(player);
        int n = b.heard.size();
        b.heard.clear();
        store.touch();
        return n;
    }

    /** /rumors hide and show. Hiding also takes any rumor about the player's whereabouts out of circulation. */
    void setHidden(Player player, boolean hidden) {
        RumorStore.Book b = book(player);
        b.hidden = hidden;
        if (hidden) {
            List<String> gone = new ArrayList<>();
            for (Fact f : store.facts.values()) {
                if (f.kind == Kind.SIGHTING && player.getUniqueId().equals(f.subjectId)) {
                    gone.add(f.id);
                }
            }
            for (String id : gone) {
                store.facts.remove(id);
                for (List<Rumor> mill : store.mills.values()) {
                    mill.removeIf(x -> x.factId().equals(id));
                }
            }
        }
        store.touch();
    }

    boolean isHidden(UUID id) {
        RumorStore.Book b = store.books.get(id);
        return b != null && b.hidden;
    }

    // ---------------------------------------------------------------- going to look

    /** Every few seconds: players standing where a rumor says it happened are checking it. */
    void checkTick() {
        if (!config.enabled) {
            return;
        }
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            RumorStore.Book book = store.books.get(p.getUniqueId());
            if (book == null || book.heard.isEmpty() || !worldOk(p.getWorld())) {
                dwell.remove(p.getUniqueId());
                continue;
            }
            Region here = regionAt(p.getLocation());
            Map<String, Integer> mine = dwell.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            mine.keySet().retainAll(book.heard.keySet());
            for (Heard h : new ArrayList<>(book.heard.values())) {
                if (!h.consensus().region().equals(here)) {
                    mine.remove(h.factId);
                    continue;
                }
                int seconds = mine.merge(h.factId, config.checkIntervalSeconds, Integer::sum);
                if (seconds >= config.checkSeconds) {
                    mine.remove(h.factId);
                    resolve(p, h);
                } else if (seconds == config.checkIntervalSeconds) {
                    Texts.send(p, Texts.checking(h.kind, h.consensus(), config.checkSeconds));
                }
            }
        }
    }

    /** Settles a rumor for a player: judges what they believed against what happened, pays out, and takes it off their list. */
    Verdict resolve(Player p, Heard h) {
        RumorStore.Book book = book(p);
        Telling believed = h.consensus();
        Fact fact = store.facts.get(h.factId);
        Verdict v = Verdict.judge(fact, believed, now());
        book.heard.remove(h.factId);
        switch (v) {
            case TRUE -> book.trueCount++;
            case EXAGGERATED -> book.exaggeratedCount++;
            case FALSE -> book.falseCount++;
            case STALE -> book.staleCount++;
        }
        int xp = v == Verdict.TRUE ? config.xpTrue : v == Verdict.EXAGGERATED ? config.xpExaggerated : 0;
        if (xp > 0) {
            p.giveExp(xp);
        }
        if (v == Verdict.TRUE) {
            for (String cmd : config.commandsTrue) {
                if (!cmd.isBlank()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
                }
            }
        }
        store.touch();
        Texts.send(p, Texts.verdict(v, h.kind, believed, fact, xp));
        return v;
    }

    // ---------------------------------------------------------------- upkeep

    /** Rumors cross into neighbouring regions now and then, changing as they go. Returns how many moved. */
    int spread() {
        long now = now();
        int moved = 0;
        List<Object[]> moves = new ArrayList<>();
        for (Map.Entry<String, List<Rumor>> e : store.mills.entrySet()) {
            Region from = Region.parse(e.getKey());
            if (from == null) {
                continue;
            }
            for (Rumor r : e.getValue()) {
                Fact f = store.facts.get(r.factId());
                if (f == null || f.expired(now) || r.hops() >= config.maxHops || rnd.nextDouble() >= config.spreadChance) {
                    continue;
                }
                int[] d = AROUND[rnd.nextInt(AROUND.length)];
                moves.add(new Object[]{from.step(d[0], d[1]), Gossip.carry(r, f.kind, rnd, config.spreadMutation)});
            }
        }
        for (Object[] m : moves) {
            if (plant((Region) m[0], (Rumor) m[1])) {
                moved++;
            }
        }
        lastSpread = now;
        return moved;
    }

    /** Called often; spreads when it is time. */
    void upkeep() {
        long now = now();
        if (now - lastSpread >= config.spreadEveryMillis) {
            purge();
            spread();
        }
    }

    /** Drops rumors about things that are over, and facts nobody can check any more. */
    void purge() {
        long now = now();
        boolean changed = false;
        Iterator<Fact> it = store.facts.values().iterator();
        while (it.hasNext()) {
            Fact f = it.next();
            if (now > f.expiresAt + config.keepMillis) {
                it.remove();
                changed = true;
            }
        }
        Iterator<Map.Entry<String, List<Rumor>>> mills = store.mills.entrySet().iterator();
        while (mills.hasNext()) {
            Map.Entry<String, List<Rumor>> m = mills.next();
            changed |= m.getValue().removeIf(r -> {
                Fact f = store.facts.get(r.factId());
                return f == null || f.expired(now);
            });
            if (m.getValue().isEmpty()) {
                mills.remove();
            }
        }
        if (changed) {
            store.touch();
        }
    }

    void saveIfDirty() {
        if (store.dirty()) {
            store.save();
        }
    }

    void forgetPlayer(UUID id) {
        dwell.remove(id);
    }
}
