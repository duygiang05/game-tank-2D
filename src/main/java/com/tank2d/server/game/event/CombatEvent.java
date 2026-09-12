package com.tank2d.server.game.event;

public class CombatEvent {
    public enum EventType { BULLET_HIT_TANK }

    private final int bulletId;
    private final int shooterId;
    private final int targetTankId;
    private final int damage;
    private final EventType type;

    public CombatEvent(int bulletId, int shooterId, int targetTankId, int damage, EventType type) {
        this.bulletId = bulletId;
        this.shooterId = shooterId;
        this.targetTankId = targetTankId;
        this.damage = damage;
        this.type = type;
    }

    public int getBulletId() { return bulletId; }
    public int getShooterId() { return shooterId; }
    public int getTargetTankId() { return targetTankId; }
    public int getDamage() { return damage; }
    public EventType getType() { return type; }
}