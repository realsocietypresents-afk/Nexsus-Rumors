package com.nexuscraft.nexusrumors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** The service other plugins see. */
final class ApiImpl implements RumorsApi {

    private final RumorService service;

    ApiImpl(RumorService service) {
        this.service = service;
    }

    @Override
    public boolean report(String kind, String world, double x, double z, String detail, String extra, int number, String subject, UUID subjectId) {
        Kind k = Kind.parse(kind);
        World w = world == null ? null : Bukkit.getWorld(world);
        if (k == null || w == null || detail == null || (subjectId != null && service.isHidden(subjectId))) {
            return false;
        }
        return service.report(k, new Location(w, x, 64, z), detail, extra == null ? "" : extra, number,
                subject == null ? "" : subject, subjectId).changed();
    }

    @Override
    public int heardCount(UUID player) {
        RumorStore.Book b = service.store().books.get(player);
        return b == null ? 0 : b.heard.size();
    }

    @Override
    public boolean isHidden(UUID player) {
        return service.isHidden(player);
    }
}
