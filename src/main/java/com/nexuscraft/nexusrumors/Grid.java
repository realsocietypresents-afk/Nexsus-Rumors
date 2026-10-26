package com.nexuscraft.nexusrumors;

import java.util.Locale;

/** Turns places into regions and regions into words. Nothing here ever keeps or says a coordinate. */
final class Grid {

    private Grid() {
    }

    /** The region holding block x,z when cell 0,0 is centred on the origin block. */
    static Region regionOf(String world, double x, double z, int originX, int originZ, int size) {
        int bx = (int) Math.floor(x) - originX;
        int bz = (int) Math.floor(z) - originZ;
        int half = size / 2;
        return new Region(world, Math.floorDiv(bx + half, size), Math.floorDiv(bz + half, size));
    }

    /** "around spawn", "just north of spawn", "far south-east of spawn". North is -z, east is +x. */
    static String where(Region r) {
        if (r.cx() == 0 && r.cz() == 0) {
            return "around spawn";
        }
        String ns = r.cz() < 0 ? "north" : r.cz() > 0 ? "south" : "";
        String ew = r.cx() > 0 ? "east" : r.cx() < 0 ? "west" : "";
        String dir = ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew;
        int ring = r.ring();
        String how = ring == 1 ? "just " : ring == 2 ? "a fair way " : "far ";
        return how + dir + " of spawn";
    }

    /** "the dark forest, just north of spawn" when the biome is known, else just the direction. */
    static String label(Region r, String biome) {
        if (biome == null || biome.isBlank()) {
            return where(r);
        }
        return "the " + biome.toLowerCase(Locale.ROOT).replace('_', ' ') + ", " + where(r);
    }
}
