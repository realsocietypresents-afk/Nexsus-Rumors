package com.nexuscraft.nexusrumors;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Every setting, read once from config.yml. A reload builds a new one. */
final class RumorsConfig {

    final boolean enabled;
    final int regionSize;
    final Set<String> worlds = new LinkedHashSet<>();
    final int maxPerRegion;
    final int maxTotal;
    final long marketTtl;
    final long dangerTtl;
    final long sightingTtl;
    final long keepMillis;
    final long spreadEveryMillis;
    final double spreadChance;
    final int maxHops;
    final double spreadMutation;
    final double retellChance;
    final boolean requireSneak;
    final long hearCooldownMillis;
    final int maxRemembered;
    final int maxVersions;
    final int checkSeconds;
    final int checkIntervalSeconds;
    final boolean sightings;
    final int sightingEverySeconds;
    final double sightingChance;
    final double sightingRadius;
    final boolean namePlayers;
    final boolean market;
    final double marketChance;
    final long marketVillagerCooldownMillis;
    final Set<Material> notableMaterials = new LinkedHashSet<>();
    final Set<String> notableEnchants = new LinkedHashSet<>();
    final boolean danger;
    final boolean deaths;
    final boolean nameVictims;
    final boolean pvp;
    final Set<EntityType> dangerMobs = new LinkedHashSet<>();
    final double tellRadius;
    final long tellCooldownMillis;
    final double tellMutation;
    final double postRadius;
    final int xpTrue;
    final int xpExaggerated;
    final List<String> commandsTrue;
    /** Things in config.yml that could not be used, for the log and /rumors reload. */
    final List<String> problems = new ArrayList<>();

    RumorsConfig(FileConfiguration c) {
        this.enabled = c.getBoolean("enabled", true);
        this.regionSize = clamp(c.getInt("region-size", 192), 32, 4096);
        for (String w : c.getStringList("worlds")) {
            if (!w.isBlank()) {
                worlds.add(w.trim());
            }
        }
        this.maxPerRegion = clamp(c.getInt("max-rumors-per-region", 12), 1, 100);
        this.maxTotal = clamp(c.getInt("max-rumors-total", 1000), 10, 100_000);
        this.marketTtl = minutes(c.getInt("ttl.market-minutes", 720), 1, 100_000);
        this.dangerTtl = minutes(c.getInt("ttl.danger-minutes", 180), 1, 100_000);
        this.sightingTtl = minutes(c.getInt("ttl.sighting-minutes", 20), 1, 100_000);
        this.keepMillis = minutes(c.getInt("ttl.keep-minutes", 120), 0, 100_000);
        this.spreadEveryMillis = minutes(c.getInt("spread.interval-minutes", 10), 1, 1440);
        this.spreadChance = unit(c.getDouble("spread.chance", 0.35));
        this.maxHops = clamp(c.getInt("spread.max-hops", 4), 0, 20);
        this.spreadMutation = unit(c.getDouble("spread.mutation-chance", 0.5));
        this.retellChance = unit(c.getDouble("mutation.retell-chance", 0.35));
        this.requireSneak = c.getBoolean("hear.require-sneak", true);
        this.hearCooldownMillis = clamp(c.getInt("hear.cooldown-seconds", 90), 0, 86_400) * 1000L;
        this.maxRemembered = clamp(c.getInt("hear.max-remembered", 30), 1, 200);
        this.maxVersions = clamp(c.getInt("hear.max-versions", 6), 1, 20);
        this.checkSeconds = clamp(c.getInt("check.seconds", 15), 1, 600);
        this.checkIntervalSeconds = clamp(c.getInt("check.interval-seconds", 5), 1, 60);
        this.sightings = c.getBoolean("sightings.enabled", true);
        this.sightingEverySeconds = clamp(c.getInt("sightings.interval-seconds", 120), 5, 3600);
        this.sightingChance = unit(c.getDouble("sightings.chance", 0.25));
        this.sightingRadius = clamp(c.getInt("sightings.radius", 24), 4, 128);
        this.namePlayers = c.getBoolean("sightings.name-players", true);
        this.market = c.getBoolean("market.enabled", true);
        this.marketChance = unit(c.getDouble("market.chance", 0.6));
        this.marketVillagerCooldownMillis = minutes(c.getInt("market.villager-cooldown-minutes", 30), 0, 100_000);
        for (String name : c.getStringList("market.notable-materials")) {
            Material m = Material.matchMaterial(name.trim());
            if (m == null) {
                problems.add("market.notable-materials: " + name + " is not a material");
            } else {
                notableMaterials.add(m);
            }
        }
        for (String key : c.getStringList("market.notable-enchants")) {
            if (!key.isBlank()) {
                notableEnchants.add(key.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.danger = c.getBoolean("danger.enabled", true);
        this.deaths = c.getBoolean("danger.deaths", true);
        this.nameVictims = c.getBoolean("danger.name-victims", false);
        this.pvp = c.getBoolean("danger.pvp", false);
        for (String name : c.getStringList("danger.mobs")) {
            try {
                dangerMobs.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                problems.add("danger.mobs: " + name + " is not a creature");
            }
        }
        this.tellRadius = clamp(c.getInt("tell.radius", 24), 2, 128);
        this.tellCooldownMillis = clamp(c.getInt("tell.cooldown-seconds", 300), 0, 86_400) * 1000L;
        this.tellMutation = unit(c.getDouble("tell.mutation-chance", 0.2));
        this.postRadius = clamp(c.getInt("post.radius", 32), 2, 128);
        this.xpTrue = clamp(c.getInt("rewards.xp-true", 30), 0, 10_000);
        this.xpExaggerated = clamp(c.getInt("rewards.xp-exaggerated", 10), 0, 10_000);
        this.commandsTrue = new ArrayList<>(c.getStringList("rewards.commands-true"));
    }

    boolean worldAllowed(String name, boolean normalEnvironment) {
        return worlds.isEmpty() ? normalEnvironment : worlds.contains(name);
    }

    long ttl(Kind kind) {
        return switch (kind) {
            case MARKET -> marketTtl;
            case DANGER -> dangerTtl;
            case SIGHTING -> sightingTtl;
        };
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static long minutes(int v, int lo, int hi) {
        return clamp(v, lo, hi) * 60_000L;
    }

    private static double unit(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }
}
