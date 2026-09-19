package com.tank2d.common.dto.game;

public class ItemSnapshotDTO {
    private int id;
    private String type; // "SHIELD" | "ROCKET_AMMO" | "NITRO" | "HEALTH_PACK"
    private double x;
    private double y;

    public ItemSnapshotDTO(int id, String type, double x, double y) {
        this.id = id; this.type = type; this.x = x; this.y = y;
    }

    public int getId() { return id; }
    public String getType() { return type; }
    public double getX() { return x; }
    public double getY() { return y; }
}