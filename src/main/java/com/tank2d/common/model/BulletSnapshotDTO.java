/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tank2d.common.model;

/**
 *
 * @author Admin
 */
public class BulletSnapshotDTO {
    private int id;
    private int ownerId;
    private double x;
    private double y;
    private double vx;
    private double vy;

    public BulletSnapshotDTO(int id, int ownerId, double x, double y, double vx, double vy) {
        this.id = id;
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
    }
}
