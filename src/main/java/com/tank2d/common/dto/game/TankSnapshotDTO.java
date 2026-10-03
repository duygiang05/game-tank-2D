package com.tank2d.common.dto.game;

/**
 * DTO ảnh chụp trạng thái (Snapshot) của một xe tăng trên sàn đấu.
 * Chứa thông tin vị trí (x, y), góc nòng pháo, máu, trạng thái sống, hiệu ứng khiên, tăng tốc, tàng hình và mất kết nối.
 */
public class TankSnapshotDTO {
    private int id;
    private double x;
    private double y;
    private double angle;
    private int hp;
    private boolean isAlive;
    
    private boolean isGhost;
    private boolean hasShield;
    private boolean hasNitro;
    private boolean isDisconnected;

    /**
     * Khởi tạo bản ghi snapshot đầy đủ của xe tăng bao gồm các hiệu ứng bổ trợ.
     *
     * @param id mã xe tăng
     * @param x tọa độ X
     * @param y tọa độ Y
     * @param angle góc quay (độ)
     * @param hp lượng máu hiện tại
     * @param isAlive trạng thái còn sống
     * @param isGhost trạng thái bảo hộ/vô hình sau hồi sinh
     * @param hasShield trạng thái khiên bảo vệ
     * @param hasNitro trạng thái tăng tốc
     */
    public TankSnapshotDTO(int id, double x, double y, double angle, int hp, boolean isAlive, 
                           boolean isGhost, boolean hasShield, boolean hasNitro) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.hp = hp;
        this.isAlive = isAlive;
        this.isGhost = isGhost;
        this.hasShield = hasShield;
        this.hasNitro = hasNitro;
        this.isDisconnected = false;
    }

    /**
     * Khởi tạo snapshot xe tăng cơ bản không có hiệu ứng bổ trợ.
     *
     * @param id mã xe tăng
     * @param x tọa độ X
     * @param y tọa độ Y
     * @param angle góc quay (độ)
     * @param hp lượng máu
     * @param isAlive trạng thái sống
     */
    public TankSnapshotDTO(int id, double x, double y, double angle, int hp, boolean isAlive) {
        this(id, x, y, angle, hp, isAlive, false, false, false);
    }
    
    public int getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getAngle() { return angle; }
    public int getHp() { return hp; }
    public boolean isAlive() { return isAlive; }

    public boolean isGhost() { return isGhost; }
    public boolean hasShield() { return hasShield; }
    public boolean hasNitro() { return hasNitro; }
    public boolean isDisconnected() { return isDisconnected; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setHp(int hp) { this.hp = hp; }
    public void setGhost(boolean ghost) { this.isGhost = ghost; }
    public void setShield(boolean shield) { this.hasShield = shield; }
    public void setNitro(boolean nitro) { this.hasNitro = nitro; }
    public void setDisconnected(boolean disconnected) { this.isDisconnected = disconnected; }
    public void setAlive(boolean isAlive){
        this.isAlive = isAlive;
    }
}
