package com.ashwake.core.client.minimap;

public final class MinimapWaypoint {
    private final String name;
    private final String dimensionKey;
    private final int x;
    private final int y;
    private final int z;
    private final int color;

    public MinimapWaypoint(String name, String dimensionKey, int x, int y, int z, int color) {
        this.name = name;
        this.dimensionKey = dimensionKey;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
    }

    public String name() {
        return this.name;
    }

    public String dimensionKey() {
        return this.dimensionKey;
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int z() {
        return this.z;
    }

    public int color() {
        return this.color;
    }
}
