package com.tank2d.server.model;

public class BulletEntity {
    public enum BulletType { NORMAL, ROCKET }

    private final int id;
    private final int ownerId;
    private double x;
    private double y;
    private double vx;
    private double vy;
    private boolean alive = true;
    private final BulletType type;

    public BulletEntity(int id, int ownerId, double x, double y, double vx, double vy, BulletType type) {
        this.id = id;
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.type = type != null ? type : BulletType.NORMAL;
    }

    public int getId() { return id; }
    public int getOwnerId() { return ownerId; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public boolean isAlive() { return alive; }
    public BulletType getType() { return type; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setAlive(boolean alive) { this.alive = alive; }
}