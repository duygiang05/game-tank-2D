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
    
    public TankSnapshotDTO(int id, double x, double y, double angle, int hp, boolean isAlive) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.hp = hp;
        this.isAlive = isAlive;
    }
    
    public int getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getAngle() { return angle; }
    public int getHp() { return hp; }
    public boolean isAlive() { return isAlive; }
}
