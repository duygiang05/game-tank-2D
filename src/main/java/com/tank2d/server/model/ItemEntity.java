package com.tank2d.server.model;

import com.tank2d.server.item.ItemType;

public class ItemEntity {
    private final int id;
    private final ItemType type;
    private final double x;
    private final double y;
    private final long spawnTimeMillis;
    private boolean active = true;

    public ItemEntity(int id, ItemType type, double x, double y, long spawnTimeMillis) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.spawnTimeMillis = spawnTimeMillis;
    }

    public int getId() { return id; }
    public ItemType getType() { return type; }
    public double getX() { return x; }
    public double getY() { return y; }
    public long getSpawnTimeMillis() { return spawnTimeMillis; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}