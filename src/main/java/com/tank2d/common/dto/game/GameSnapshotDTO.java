/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tank2d.common.dto.game;
import java.util.List;
/**
 *
 * @author Admin
 */
public class GameSnapshotDTO {
    private long tick;
    private List<TankSnapshotDTO> tanks;
    private List<BulletSnapshotDTO> bullets;

    public GameSnapshotDTO(long tick, List<TankSnapshotDTO> tanks, List<BulletSnapshotDTO> bullets) {
        this.tick = tick;
        this.tanks = tanks;
        this.bullets = bullets;
    }
    
    public long getTick() { return tick; }
    public List<TankSnapshotDTO> getTanks() { return tanks; }
    public List<BulletSnapshotDTO> getBullets() { return bullets; }
}
