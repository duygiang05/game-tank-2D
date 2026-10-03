package com.tank2d.common.dto.game;

/**
 * DTO ảnh chụp trạng thái vật phẩm xuất hiện trên bản đồ (Item Snapshot).
 */
public class ItemSnapshotDTO {
    private int id;
    /** Loại vật phẩm: SHIELD, ROCKET_AMMO, NITRO hoặc HEALTH_PACK */
    private String type;
    private double x;
    private double y;

    /**
     * Khởi tạo snapshot vật phẩm bổ trợ.
     *
     * @param id mã định danh vật phẩm
     * @param type tên loại vật phẩm
     * @param x tọa độ X
     * @param y tọa độ Y
     */
    public ItemSnapshotDTO(int id, String type, double x, double y) {
        this.id = id; 
        this.type = type; 
        this.x = x; 
        this.y = y;
    }

    public int getId() { return id; }
    public String getType() { return type; }
    public double getX() { return x; }
    public double getY() { return y; }
}