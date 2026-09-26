package com.tank2d.common.dto.game;

public class BulletSnapshotDTO {
    private int id;
    private int ownerId;
    private double x;
    private double y;
    private double vx;
    private double vy;
    private String type; // "NORMAL" hoặc "ROCKET"

    // Constructor mặc định cho Gson deserialize
    public BulletSnapshotDTO() {}

    public BulletSnapshotDTO(int id, int ownerId, double x, double y, double vx, double vy, String type) {
        this.id = id;
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.type = type;
    }
    
    public int getId() { return id; }
    public int getOwnerId() { return ownerId; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public String getType() { return type; }
    
    public void setX(double x) { this.x = x; }
    public void setType(String type) { this.type = type; }
}