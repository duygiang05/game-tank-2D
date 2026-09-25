/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tank2d.common.dto.game;

/**
 *
 * @author Admin
 */
public class TankSnapshotDTO {
    private int id;
    private double x;
    private double y;
    private double angle;
    private int hp;
    private boolean isAlive;
    
    private boolean isGhost;      // Trạng thái mờ/bảo hộ sau hồi sinh
    private boolean hasShield;    // Trạng thái khiên bảo vệ
    private boolean hasNitro;     // Trạng thái tăng tốc Nitro
    
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
    }

    // Constructor cũ 
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

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setHp(int hp) { this.hp = hp; }
    public void setGhost(boolean ghost) { this.isGhost = ghost; }
    public void setShield(boolean shield) { this.hasShield = shield; }
    public void setNitro(boolean nitro) { this.hasNitro = nitro; }
}
