package com.tank2d.common.dto.game;

/**
 * DTO chứa thông tin hiệu ứng sự kiện trong trận đấu (GAME_EVENT_EFFECT) gửi từ server tới client.
 * Dùng để phát các hiệu ứng đồ họa như vụ nổ (EXPLOSION) tại tọa độ cụ thể.
 */
public class GameEventEffectDTO {
    private String eventType;
    private double x;
    private double y;
    private int targetTankId;

    /**
     * Khởi tạo gói dữ liệu hiệu ứng sự kiện.
     *
     * @param eventType loại sự kiện hiệu ứng (ví dụ: EXPLOSION)
     * @param x tọa độ X nơi xảy ra hiệu ứng
     * @param y tọa độ Y nơi xảy ra hiệu ứng
     * @param targetTankId mã xe tăng chịu tác động
     */
    public GameEventEffectDTO(String eventType, double x, double y, int targetTankId) {
        this.eventType = eventType;
        this.x = x;
        this.y = y;
        this.targetTankId = targetTankId;
    }

    public String getEventType() { return eventType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public int getTargetTankId() { return targetTankId; }
}