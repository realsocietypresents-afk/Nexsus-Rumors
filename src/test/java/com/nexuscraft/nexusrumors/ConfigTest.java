package com.nexuscraft.nexusrumors;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.util.List;

import static com.nexuscraft.nexusrumors.Kit.expect;

/** config.yml: the shipped values, and what happens to silly ones. */
public class ConfigTest {

    public static void main(String[] args) {
        shipped();
        clamps();
        problems();
        Kit.finish();
    }

    static void shipped() {
        RumorsConfig c = new RumorsConfig(BukkitKit.shipped());
        expect(c.enabled && c.regionSize == 192 && c.worlds.isEmpty() && c.maxPerRegion == 12 && c.maxTotal == 1000, "basics");
        expect(c.marketTtl == 720 * 60_000L && c.dangerTtl == 180 * 60_000L && c.sightingTtl == 20 * 60_000L && c.keepMillis == 120 * 60_000L, "lifetimes");
        expect(c.ttl(Kind.MARKET) == c.marketTtl && c.ttl(Kind.DANGER) == c.dangerTtl && c.ttl(Kind.SIGHTING) == c.sightingTtl, "ttl by kind");
        expect(c.spreadEveryMillis == 10 * 60_000L && c.spreadChance == 0.35 && c.maxHops == 4 && c.spreadMutation == 0.5, "spreading");
        expect(c.retellChance == 0.35 && c.requireSneak && c.hearCooldownMillis == 90_000L && c.maxRemembered == 30 && c.maxVersions == 6, "hearing");
        expect(c.checkSeconds == 15 && c.checkIntervalSeconds == 5, "checking");
        expect(c.sightings && c.sightingEverySeconds == 120 && c.sightingChance == 0.25 && c.sightingRadius == 24 && c.namePlayers, "sightings");
        expect(c.market && c.marketChance == 0.6 && c.marketVillagerCooldownMillis == 30 * 60_000L, "market");
        expect(c.notableMaterials.size() == 11 && c.notableMaterials.contains(Material.DIAMOND_PICKAXE) && c.notableMaterials.contains(Material.BELL), "notable items");
        expect(c.notableEnchants.size() == 8 && c.notableEnchants.contains("mending") && c.notableEnchants.contains("silk_touch"), "notable enchants");
        expect(c.danger && c.deaths && !c.nameVictims && !c.pvp, "danger");
        expect(c.dangerMobs.containsAll(List.of(EntityType.WARDEN, EntityType.WITHER, EntityType.RAVAGER, EntityType.ELDER_GUARDIAN)) && c.dangerMobs.size() == 4, "dangerous mobs");
        expect(c.tellRadius == 24 && c.tellCooldownMillis == 300_000L && c.tellMutation == 0.2 && c.postRadius == 32, "telling");
        expect(c.xpTrue == 30 && c.xpExaggerated == 10 && c.commandsTrue.isEmpty(), "rewards");
        expect(c.problems.isEmpty(), "the shipped file has no problems: " + c.problems);
        expect(c.worldAllowed("world", true) && !c.worldAllowed("world_nether", false), "default: normal worlds only");
    }

    static void clamps() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("region-size", 1);
        y.set("max-rumors-per-region", 0);
        y.set("spread.chance", 7.0);
        y.set("mutation.retell-chance", -1.0);
        y.set("spread.mutation-chance", Double.NaN);
        y.set("ttl.market-minutes", 0);
        y.set("spread.interval-minutes", 0);
        y.set("check.seconds", 0);
        y.set("hear.max-versions", 99);
        y.set("sightings.radius", 1000);
        y.set("worlds", List.of("world_nether", " ", "world"));
        RumorsConfig c = new RumorsConfig(y);
        expect(c.regionSize == 32, "tiny regions are raised to 32");
        expect(c.maxPerRegion == 1, "at least one rumor per region");
        expect(c.spreadChance == 1.0 && c.retellChance == 0.0 && c.spreadMutation == 0.0, "chances stay between 0 and 1, and NaN is no chance");
        expect(c.marketTtl == 60_000L && c.spreadEveryMillis == 60_000L, "minutes are at least one");
        expect(c.checkSeconds == 1 && c.maxVersions == 20 && c.sightingRadius == 128, "other limits");
        expect(c.worlds.size() == 2 && c.worldAllowed("world_nether", false) && !c.worldAllowed("other", true), "named worlds replace the default");
        expect(c.enabled && c.requireSneak, "missing keys take their defaults");
        RumorsConfig huge = new RumorsConfig(yamlWith("region-size", 99999));
        expect(huge.regionSize == 4096, "huge regions are capped");
    }

    static YamlConfiguration yamlWith(String key, Object value) {
        YamlConfiguration y = new YamlConfiguration();
        y.set(key, value);
        return y;
    }

    static void problems() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("market.notable-materials", List.of("DIAMOND_PICKAXE", "NOT_A_THING"));
        y.set("danger.mobs", List.of("warden", "SPACE_ALIEN"));
        y.set("market.notable-enchants", List.of(" Mending ", ""));
        RumorsConfig c = new RumorsConfig(y);
        expect(c.problems.size() == 2, "two problems: " + c.problems);
        expect(c.problems.get(0).contains("NOT_A_THING") && c.problems.get(1).contains("SPACE_ALIEN"), "named");
        expect(c.notableMaterials.size() == 1 && c.dangerMobs.size() == 1 && c.dangerMobs.contains(EntityType.WARDEN), "good entries still count (and lower case is fine)");
        expect(c.notableEnchants.size() == 1 && c.notableEnchants.contains("mending"), "enchant keys are trimmed and lower-cased");
    }
}
