package com.tank2d.common.dto.game;

/**
 * DTO ảnh chụp trạng thái viên đạn đang bay trên sàn đấu (Bullet Snapshot).
 */
public class BulletSnapshotDTO {
    private int id;
    private int ownerId;
    private double x;
    private double y;
    private double vx;
    private double vy;
    /** Loại đạn: NORMAL hoặc ROCKET */
    private String type;

    public BulletSnapshotDTO() {}

    /**
     * Khởi tạo snapshot trạng thái đạn.
     *
     * @param id mã định danh viên đạn
     * @param ownerId mã xe tăng bắn đạn
     * @param x tọa độ X
     * @param y tọa độ Y
     * @param vx vận tốc theo trục X
     * @param vy vận tốc theo trục Y
     * @param type loại đạn
     */
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