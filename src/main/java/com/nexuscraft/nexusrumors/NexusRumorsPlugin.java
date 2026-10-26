package com.nexuscraft.nexusrumors;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * NexusRumors: a gossip economy. Villagers and players spread rumors about markets, dangers and whereabouts; some are true
 * and some are exaggerated, and it is up to you which to trust.
 */
public final class NexusRumorsPlugin extends JavaPlugin {

    private RumorsConfig config;
    private RumorStore store;
    private RumorService service;
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new RumorsConfig(getConfig());
        this.store = new RumorStore(getDataFolder(), getLogger());
        store.load();
        this.service = new RumorService(config, store, System::currentTimeMillis, new Random());
        getServer().getPluginManager().registerEvents(new RumorListener(service), this);
        RumorCommand command = new RumorCommand(service);
        command.setReload(this::reloadAll);
        getCommand("rumors").setExecutor(command);
        getCommand("rumors").setTabCompleter(command);
        getServer().getServicesManager().register(RumorsApi.class, new ApiImpl(service), this, ServicePriority.Normal);
        tasks.add(getServer().getScheduler().runTaskTimer(this, service::checkTick, 100L, config.checkIntervalSeconds * 20L));
        tasks.add(getServer().getScheduler().runTaskTimer(this, service::sampleSightings, 200L, config.sightingEverySeconds * 20L));
        tasks.add(getServer().getScheduler().runTaskTimer(this, service::upkeep, 1200L, 1200L));
        tasks.add(getServer().getScheduler().runTaskTimer(this, service::saveIfDirty, 6000L, 6000L));
        for (String problem : config.problems) {
            getLogger().warning("config.yml: " + problem);
        }
        getLogger().info("NexusRumors enabled -- " + store.facts.size() + " fact(s), " + store.rumorCount() + " rumor(s) in circulation.");
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : tasks) {
            t.cancel();
        }
        tasks.clear();
        if (store != null) {
            store.save();
        }
        Bukkit.getServicesManager().unregisterAll(this);
    }

    /** Re-reads config.yml. Returns what in the file could not be used. */
    private List<String> reloadAll() {
        reloadConfig();
        this.config = new RumorsConfig(getConfig());
        service.setConfig(config);
        return config.problems;
    }
}
