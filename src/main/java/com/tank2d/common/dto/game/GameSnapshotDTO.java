package com.tank2d.common.dto.game;
import java.util.List;

/**
 * DTO ảnh chụp trạng thái toàn cục của trận đấu tại một khung hình logic (Tick).
 * Đồng bộ vị trí tất cả xe tăng, đường đạn và vật phẩm trên bản đồ.
 */
public class GameSnapshotDTO {
    private long tick;
    private List<TankSnapshotDTO> tanks;
    private List<BulletSnapshotDTO> bullets;
    private List<ItemSnapshotDTO> items;

    /**
     * Khởi tạo gói snapshot đồng bộ toàn cục.
     *
     * @param tick chỉ số nhịp thời gian logic
     * @param tanks danh sách trạng thái các xe tăng
     * @param bullets danh sách trạng thái các viên đạn
     * @param items danh sách trạng thái các vật phẩm
     */
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