package com.tank2d.common.dto.game;

/** Khớp đúng packet GAME_EVENT_EFFECT trong PACKET_SPEC.md — dùng để Giang gửi hiệu ứng nổ cho client. */
public class GameEventEffectDTO {
    private String eventType; // "EXPLOSION"...
    private double x;
    private double y;
    private int targetTankId;

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