package com.nexuscraft.nexusrumors;

/** One square of the map, counted in steps of region-size from the world's origin cell (cell 0,0 is around spawn). */
record Region(String world, int cx, int cz) {

    String key() {
        return world + ":" + cx + ":" + cz;
    }

    /** The cell {@code dx},{@code dz} steps away. */
    Region step(int dx, int dz) {
        return new Region(world, cx + dx, cz + dz);
    }

    /** How many cells away from spawn (diagonals count as one step). */
    int ring() {
        return Math.max(Math.abs(cx), Math.abs(cz));
    }

    static Region parse(String key) {
        if (key == null) {
            return null;
        }
        int b = key.lastIndexOf(':');
        int a = key.lastIndexOf(':', b - 1);
        if (a <= 0) {
            return null;
        }
        try {
            return new Region(key.substring(0, a), Integer.parseInt(key.substring(a + 1, b)), Integer.parseInt(key.substring(b + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
