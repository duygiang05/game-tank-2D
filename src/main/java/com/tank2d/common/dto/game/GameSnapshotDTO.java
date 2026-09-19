package com.tank2d.common.dto.game;
import java.util.List;

public class GameSnapshotDTO {
    private long tick;
    private List<TankSnapshotDTO> tanks;
    private List<BulletSnapshotDTO> bullets;
    private List<ItemSnapshotDTO> items; // MỚI

    public GameSnapshotDTO(long tick, List<TankSnapshotDTO> tanks, List<BulletSnapshotDTO> bullets, List<ItemSnapshotDTO> items) {
        this.tick = tick;
        this.tanks = tanks;
        this.bullets = bullets;
        this.items = items;
    }

    public long getTick() { return tick; }
    public List<TankSnapshotDTO> getTanks() { return tanks; }
    public List<BulletSnapshotDTO> getBullets() { return bullets; }
    public List<ItemSnapshotDTO> getItems() { return items; }
}