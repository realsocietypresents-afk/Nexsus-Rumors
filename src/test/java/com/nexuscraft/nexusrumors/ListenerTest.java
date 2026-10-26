package com.nexuscraft.nexusrumors;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static com.nexuscraft.nexusrumors.Kit.expect;

/** What the events do. */
public class ListenerTest {

    public static void main(String[] args) {
        BukkitKit.reset();
        BukkitKit.world();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        RumorListener l = new RumorListener(rig.service);
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");

        // asking a villager
        Villager v = BukkitKit.villager(10, 10, Temperament.PLAIN);
        PlayerInteractEntityEvent plain = new PlayerInteractEntityEvent(ann, v);
        l.onAsk(plain);
        expect(!plain.isCancelled() && ann.messages.isEmpty(), "an ordinary right-click is left to the trading screen");
        ann.setSneaking(true);
        PlayerInteractEntityEvent ask = new PlayerInteractEntityEvent(ann, v);
        l.onAsk(ask);
        expect(ask.isCancelled() && ann.text().contains("Nothing worth repeating"), "sneak and right-click is a request for news, and the trading screen stays shut");
        ann.clear();
        PlayerInteractEntityEvent off = new PlayerInteractEntityEvent(ann, v);
        off.setHand(EquipmentSlot.OFF_HAND);
        l.onAsk(off);
        expect(!off.isCancelled() && ann.messages.isEmpty(), "the off-hand click that follows is ignored");
        PlayerInteractEntityEvent cow = new PlayerInteractEntityEvent(ann, new Zombie());
        l.onAsk(cow);
        expect(!cow.isCancelled(), "other creatures are none of our business");
        PlayerInteractEntityEvent trader = new PlayerInteractEntityEvent(ann, new WanderingTrader());
        l.onAsk(trader);
        expect(!trader.isCancelled(), "nor are wandering traders (they don't live anywhere)");
        ann.perms.clear();
        PlayerInteractEntityEvent denied = new PlayerInteractEntityEvent(ann, v);
        l.onAsk(denied);
        expect(!denied.isCancelled(), "without permission, villagers just trade");
        ann.perms.add("nexusrumors.use");

        // trades
        rig.dice.always = 0.0;
        Villager lib = BukkitKit.villager(10, 10);
        lib.setProfession(Villager.Profession.LIBRARIAN);
        l.onTrade(new VillagerAcquireTradeEvent(lib, ServiceTest.recipe(new ItemStack(Material.DIAMOND_PICKAXE), 12)));
        expect(rig.store.facts.size() == 1 && rig.store.facts.get("f1").detail.equals("a Diamond Pickaxe"), "a villager gaining a trade");

        // deaths
        ann.setSneaking(false);
        ann.moveTo(BukkitKit.world(), 400, 0);
        ann.stubSetLastDamage(new EntityDamageEvent(ann, EntityDamageEvent.DamageCause.LAVA));
        l.onDeath(new PlayerDeathEvent(ann, List.of(), 0, Component.text("Ann tried to swim in lava")));
        expect(rig.store.facts.size() == 2 && rig.store.facts.get("f2").detail.equals("lava"), "a death");

        // spawns
        LivingEntity warden = new LivingEntity();
        warden.stubSetType(EntityType.WARDEN);
        warden.stubSetLocation(ServiceTest.at(0, 400));
        l.onSpawn(new CreatureSpawnEvent(warden, CreatureSpawnEvent.SpawnReason.NATURAL));
        expect(rig.store.facts.size() == 3 && rig.store.facts.get("f3").detail.equals("a Warden"), "a Warden");

        // quitting forgets progress towards checks
        Fact f = rig.store.facts.get("f2");
        ServiceTest.learn(rig, ann, f, 1);
        ann.moveTo(BukkitKit.world(), 400, 0);
        rig.service.checkTick();
        l.onQuit(new PlayerQuitEvent(ann, "left"));
        ann.clear();
        rig.service.checkTick();
        rig.service.checkTick();
        expect(rig.store.book(ann.getUniqueId()).heard.containsKey(f.id), "after quitting the count starts again");
        Kit.finish();
    }
}
