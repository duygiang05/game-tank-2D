package com.tank2d.server.model;

/**
 * Entity đạn — hiện là bản khung tối thiểu để khớp với BulletSnapshotDTO.
 * Logic bay đạn / va chạm Đạn-Xe, Đạn-Tường sẽ được bổ sung ở task riêng (thuật toán va chạm AABB).
 */
public class BulletEntity {
    private final int id;
    private final int ownerId;
    private double x;
    private double y;
    private double vx;
    private double vy;
    private boolean alive = true;

    public BulletEntity(int id, int ownerId, double x, double y, double vx, double vy) {
        this.id = id;
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
    }

    public int getId() { return id; }
    public int getOwnerId() { return ownerId; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public boolean isAlive() { return alive; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setAlive(boolean alive) { this.alive = alive; }
}