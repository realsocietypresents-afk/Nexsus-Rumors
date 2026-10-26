package com.nexuscraft.nexusrumors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Test doubles: players that remember what they were told, a clock that only moves when told to, dice that can be loaded. */
final class BukkitKit {

    private BukkitKit() {
    }

    static final class TestPlugin extends JavaPlugin {
    }

    static class TestPlayer extends Player {
        final String name;
        final List<String> messages = new ArrayList<>();
        final Set<String> perms = new HashSet<>();

        TestPlayer(String name) {
            this.name = name;
            perms.add("nexusrumors.use");
        }

        @Override public String getName() { return name; }
        @Override public boolean isOnline() { return true; }
        @Override public boolean hasPermission(String node) { return perms.contains(node); }
        @Override public void sendMessage(String m) { messages.add(m); }

        String text() {
            return Kit.strip(String.join("\n", messages));
        }

        void clear() {
            messages.clear();
        }

        void moveTo(World w, double x, double z) {
            stubSetLocation(new Location(w, x, 70, z));
        }
    }

    /** A command sender that is not a player. */
    static final class Console implements org.bukkit.command.CommandSender {
        final List<String> out = new ArrayList<>();
        final Set<String> perms = new HashSet<>();

        @Override public boolean hasPermission(String node) { return perms.contains(node); }
        @Override public void sendMessage(String message) { out.add(message); }
        @Override public void sendMessage(net.kyori.adventure.text.Component message) { }
        @Override public String getName() { return "CONSOLE"; }

        String text() {
            return Kit.strip(String.join("\n", out));
        }
    }

    /** Dice: ordinary seeded randomness until a number is loaded for the next nextDouble(). */
    static final class Dice extends Random {
        private static final long serialVersionUID = 1L;
        private final transient Deque<Double> loaded = new ArrayDeque<>();
        Double always;

        Dice(long seed) {
            super(seed);
        }

        void next(double... values) {
            for (double v : values) {
                loaded.add(v);
            }
        }

        @Override
        public double nextDouble() {
            if (!loaded.isEmpty()) {
                return loaded.poll();
            }
            return always != null ? always : super.nextDouble();
        }
    }

    static File reset() {
        Bukkit.WORLDS.clear();
        Bukkit.PLAYERS.clear();
        Bukkit.ENTITIES.clear();
        Bukkit.DISPATCHED.clear();
        Bukkit.BROADCASTS.clear();
        BukkitScheduler.LATER.clear();
        BukkitScheduler.REPEATING.clear();
        BukkitScheduler.HANDLES.clear();
        BukkitScheduler.DELAYS.clear();
        BukkitScheduler.PERIODS.clear();
        PluginManager.REGISTERED.clear();
        PluginManager.PLUGINS.clear();
        Bukkit.getServicesManager().clear();
        return freshDir();
    }

    static File freshDir() {
        try {
            File dir = Files.createTempDirectory("nxrumors").toFile();
            JavaPlugin.DATA_FOLDER = dir;
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The shipped config.yml, loaded through the sandbox's own file format (see tools/yaml-to-stub.py). */
    static YamlConfiguration shipped() {
        String path = System.getProperty("shipped");
        if (path == null) {
            throw new IllegalStateException("run through tools/run-tests.sh: -Dshipped is missing");
        }
        return YamlConfiguration.loadConfiguration(new File(path));
    }

    static World world() {
        return Bukkit.createWorld(new WorldCreator("world"));
    }

    static World nether() {
        World w = Bukkit.createWorld(new WorldCreator("world_nether").environment(World.Environment.NETHER));
        return w;
    }

    static TestPlayer join(String name) {
        TestPlayer p = new TestPlayer(name);
        p.stubSetLocation(new Location(world(), 0, 70, 0));
        Bukkit.PLAYERS.put(p.getUniqueId(), p);
        return p;
    }

    /** A villager standing at x,z (block coordinates in the overworld). */
    static Villager villager(double x, double z) {
        Villager v = new Villager();
        v.stubSetLocation(new Location(world(), x, 70, z));
        Bukkit.ENTITIES.put(v.getUniqueId(), v);
        return v;
    }

    /** A villager of a chosen temperament. */
    static Villager villager(double x, double z, Temperament wanted) {
        for (int i = 0; i < 10_000; i++) {
            Villager v = villager(x, z);
            if (Temperament.of(v.getUniqueId()) == wanted) {
                return v;
            }
            Bukkit.ENTITIES.remove(v.getUniqueId());
        }
        throw new IllegalStateException("no " + wanted + " villager found");
    }

    static Command command(String name) {
        return new Command() {
            @Override
            public String getName() {
                return name;
            }
        };
    }

    /** A world whose biome depends on where you stand (so region labels have something to say). */
    static void biome(World w, Biome b) {
        w.stubSetBiomes((x, z) -> b);
    }

    /** One running server, wired as NexusRumorsPlugin wires it, with a clock that only moves when told to. */
    static final class Rig {
        final File dir = freshDir();
        final AtomicLong now = new AtomicLong(1_000_000L);
        final Dice dice = new Dice(42);
        final Console console = new Console();
        RumorsConfig config;
        RumorStore store;
        RumorService service;
        RumorCommand command;
        final TestPlugin plugin = new TestPlugin();

        Rig() {
            this(shipped());
        }

        Rig(YamlConfiguration cfg) {
            Bukkit.CONSOLE = console;
            console.perms.add("nexusrumors.use");
            console.perms.add("nexusrumors.admin");
            build(cfg);
        }

        void build(YamlConfiguration cfg) {
            this.config = new RumorsConfig(cfg);
            this.store = new RumorStore(dir, plugin.getLogger());
            store.load();
            this.service = new RumorService(config, store, now::get, dice);
            this.command = new RumorCommand(service);
        }

        /** Pretend the server restarted: same data folder, brand new objects. */
        Rig restart() {
            build(shipped());
            return this;
        }

        void minutes(long m) {
            now.addAndGet(m * 60_000L);
        }

        void seconds(long s) {
            now.addAndGet(s * 1000L);
        }

        String say(TestPlayer p, String... args) {
            p.clear();
            command.onCommand(p, command("rumors"), "rumors", args);
            return p.text();
        }

        String sayConsole(String... args) {
            console.out.clear();
            command.onCommand(console, command("rumors"), "rumors", args);
            return console.text();
        }
    }
}
