package com.nexuscraft.nexusrumors;

import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/** What happens in the world, and the player asking a villager for news. */
public final class RumorListener implements Listener {

    private final RumorService service;

    RumorListener(RumorService service) {
        this.service = service;
    }

    /** Sneak and right-click a villager to ask for news. Ordinary right-clicks still open the trading screen. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAsk(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Villager villager)
                || !e.getPlayer().hasPermission("nexusrumors.use")) {
            return;
        }
        if (service.hear(e.getPlayer(), villager) != RumorService.HearResult.NOT_HANDLED) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(VillagerAcquireTradeEvent e) {
        service.onTrade(e.getEntity(), e.getRecipe());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        service.onDeath(e.getEntity(), e.getEntity().getLastDamageCause());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        service.onSpawn(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        service.forgetPlayer(e.getPlayer().getUniqueId());
    }
}
