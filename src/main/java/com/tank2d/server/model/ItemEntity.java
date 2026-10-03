package com.tank2d.server.model;

import com.tank2d.server.item.ItemType;

/**
 * Thực thể biểu diễn một vật phẩm hỗ trợ (Power-up Item) xuất hiện trên bản đồ.
 */
public class ItemEntity {

    private final int id;
    private final ItemType type;
    private final double x;
    private final double y;
    private final long spawnTimeMillis;
    private boolean active = true;

    /**
     * Khởi tạo một vật phẩm hỗ trợ mới.
     *
     * @param id               mã định danh vật phẩm
     * @param type             loại vật phẩm (SHIELD, NITRO, ROCKET_AMMO, HEALTH_PACK)
     * @param x                tọa độ tâm X trên bản đồ
     * @param y                tọa độ tâm Y trên bản đồ
     * @param spawnTimeMillis  mốc thời gian xuất hiện tính bằng mili-giây
     */
    public ItemEntity(int id, ItemType type, double x, double y, long spawnTimeMillis) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.spawnTimeMillis = spawnTimeMillis;
    }

    public int getId() { return id; }
    public ItemType getType() { return type; }
    public double getX() { return x; }
    public double getY() { return y; }
    public long getSpawnTimeMillis() { return spawnTimeMillis; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}