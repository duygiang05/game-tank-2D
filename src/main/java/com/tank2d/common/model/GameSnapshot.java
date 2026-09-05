/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tank2d.common.model;

import java.util.List;
/**
 *
 * @author Admin
 */
public class GameSnapshot {
    private long tick;
    private List<TankSnapshotDTO> tanks;
    private List<BulletSnapshotDTO> bullets;

    public GameSnapshot(long tick, List<TankSnapshotDTO> tanks, List<BulletSnapshotDTO> bullets) {
        this.tick = tick;
        this.tanks = tanks;
        this.bullets = bullets;
    }
}
