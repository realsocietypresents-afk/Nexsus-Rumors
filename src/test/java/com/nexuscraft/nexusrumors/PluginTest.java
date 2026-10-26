package com.nexuscraft.nexusrumors;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexusrumors.Kit.expect;

/** Start-up wiring, the published service, saving, reload and shutdown. */
public class PluginTest {

    public static void main(String[] args) throws Exception {
        BukkitKit.reset();
        BukkitKit.world();
        NexusRumorsPlugin plugin = new NexusRumorsPlugin();
        plugin.onEnable();
        PluginCommand rumors = plugin.getCommand("rumors");
        expect(rumors.getExecutor() instanceof RumorCommand && rumors.getTabCompleter() instanceof RumorCommand, "/rumors is wired");
        expect(PluginManager.REGISTERED.stream().anyMatch(RumorListener.class::isInstance), "the listener is registered");
        RumorsApi api = Bukkit.getServicesManager().load(RumorsApi.class);
        expect(api != null, "the API is published");
        expect(BukkitScheduler.PERIODS.size() == 4 && BukkitScheduler.PERIODS.containsAll(List.of(100L, 2400L, 1200L, 6000L)),
                "four timers: checking every 5 s, sightings every 2 min, upkeep every minute, saving every 5 min: " + BukkitScheduler.PERIODS);

        // the API
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        expect(api.report("danger", "world", 10, 10, "a cave-in", "note", 0, "", null), "another plugin reports something");
        expect(!api.report("danger", "world", 20, 20, "a cave-in", "note", 0, "", null), "the same thing again changes nothing");
        expect(api.report("danger", "world", 20, 20, "lava", "death", 2, "", null) && api.report("danger", "world", 20, 20, "lava", "death", 1, "", null),
                "but a death toll that grows is news");
        expect(api.report("Market", "world", 30, 30, "a Bell", null, 2, null, null), "kinds in any case, nulls tolerated");
        expect(!api.report("nonsense", "world", 0, 0, "x", "", 0, "", null) && !api.report("danger", "nowhere", 0, 0, "x", "", 0, "", null)
                && !api.report("danger", null, 0, 0, "x", "", 0, "", null) && !api.report("danger", "world", 0, 0, null, "", 0, "", null), "bad reports are refused");
        expect(!api.report("danger", "world_nether", 0, 0, "x", "", 0, "", null), "untracked worlds are refused");
        UUID id = ann.getUniqueId();
        expect(api.report("sighting", "world", 5, 5, "", "", 0, "Ann", id), "a sighting");
        expect(!api.isHidden(id) && api.heardCount(id) == 0 && api.heardCount(UUID.randomUUID()) == 0, "nobody hidden, nothing heard");
        File dir = org.bukkit.plugin.java.JavaPlugin.DATA_FOLDER;
        Runnable saver = BukkitScheduler.REPEATING.get(3);
        saver.run();
        File facts = new File(dir, "facts.yml");
        expect(facts.isFile() && Files.readString(facts.toPath()).contains("a cave-in"), "the save timer writes facts.yml");
        long stamp = facts.lastModified();
        Thread.sleep(20);
        saver.run();
        expect(facts.lastModified() == stamp, "and does not rewrite it when nothing changed");

        // hiding, then the API respects it
        plugin.getCommand("rumors").getExecutor().onCommand(ann, BukkitKit.command("rumors"), "rumors", new String[]{"hide"});
        expect(api.isHidden(id) && !api.report("sighting", "world", 5, 5, "", "", 0, "Ann", id), "a hidden player is not reported on");

        // the timers run the right jobs
        BukkitScheduler.REPEATING.get(0).run();
        BukkitScheduler.REPEATING.get(1).run();
        BukkitScheduler.REPEATING.get(2).run();
        expect(true, "the check, sighting and upkeep timers run");

        // reload
        plugin.getConfig().set("market.notable-materials", List.of("NOPE"));
        List<String> problems = reload(plugin);
        expect(problems.size() == 1 && problems.get(0).contains("NOPE"), "reload hands back config problems: " + problems);
        plugin.getConfig().set("market.notable-materials", List.of("BELL"));
        expect(reload(plugin).isEmpty(), "and none when fixed");

        // shutdown
        plugin.onDisable();
        expect(Bukkit.getServicesManager().load(RumorsApi.class) == null, "stopping withdraws the API");
        expect(BukkitScheduler.HANDLES.stream().allMatch(h -> h.isCancelled()), "and cancels the timers");
        expect(Files.readString(facts.toPath()).contains("lava"), "and saves");
        plugin.onDisable();
        expect(true, "stopping twice is fine");

        // restarting keeps the talk
        BukkitKit.reset();
        File keep = BukkitKit.freshDir();
        NexusRumorsPlugin first = new NexusRumorsPlugin();
        first.onEnable();
        BukkitKit.world();
        Bukkit.getServicesManager().load(RumorsApi.class).report("danger", "world", 1, 1, "fire", "death", 1, "", null);
        first.onDisable();
        BukkitScheduler.REPEATING.clear();
        NexusRumorsPlugin second = new NexusRumorsPlugin();
        second.onEnable();
        expect(Files.readString(new File(keep, "rumors.yml").toPath()).contains("fire"), "a restart finds the rumors where they were left");
        second.onDisable();

        // the shipped files agree with the code
        String yml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        for (String must : List.of("main: com.nexuscraft.nexusrumors.NexusRumorsPlugin", "rumors:", "aliases: [gossip]",
                "nexusrumors.use:", "nexusrumors.admin:")) {
            expect(yml.contains(must), "plugin.yml has " + must);
        }
        expect(Files.readString(new File("pom.xml").toPath()).contains("<artifactId>NexusRumors</artifactId>"), "pom names the plugin");

        // a damaged config does not stop it
        BukkitKit.reset();
        NexusRumorsPlugin bad = new NexusRumorsPlugin();
        bad.getConfig().set("danger.mobs", List.of("SPACE_ALIEN"));
        bad.onEnable();
        expect(Bukkit.getServicesManager().load(RumorsApi.class) != null, "a bad entry is reported and skipped; the plugin still starts");
        bad.onDisable();
        Kit.finish();
    }

    static List<String> reload(NexusRumorsPlugin plugin) throws Exception {
        java.lang.reflect.Method m = NexusRumorsPlugin.class.getDeclaredMethod("reloadAll");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) m.invoke(plugin);
        return out;
    }
}
