package com.nexuscraft.nexusrumors;

import java.util.UUID;

/**
 * Lets other plugins feed the gossip: report that something happened and villagers will start talking about it.
 * Get it with {@code Bukkit.getServicesManager().getRegistration(RumorsApi.class)}.
 */
public interface RumorsApi {

    /**
     * Reports an event.
     *
     * @param kind    "market", "danger" or "sighting"
     * @param world   world name
     * @param x       block x (only the region it falls in is ever kept or told)
     * @param z       block z
     * @param detail  what it is: "a Mending book", "lava", "a Warden"
     * @param extra   market: who ("librarian"); danger: "death", "spawn" or "note"
     * @param number  market: the emerald price; danger: how many times; 0 for none
     * @param subject sighting or death: a name to tell, or "" to stay anonymous
     * @param subjectId sighting: the player's id (one sighting at a time per player, and /rumors hide is respected), or null
     * @return true when a new story was recorded or an existing one grew
     */
    boolean report(String kind, String world, double x, double z, String detail, String extra, int number, String subject, UUID subjectId);

    /** How many rumors this player is currently holding in their notes. */
    int heardCount(UUID player);

    /** Whether this player asked to be kept out of whereabouts rumors. */
    boolean isHidden(UUID player);
}
