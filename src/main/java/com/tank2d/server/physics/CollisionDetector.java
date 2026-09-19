package com.tank2d.server.physics;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.game.event.CombatEvent;
import com.tank2d.server.game.event.ItemPickupEvent;
import com.tank2d.server.game.event.MapChangeEvent;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.model.ItemEntity;
import com.tank2d.server.model.TankEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class CollisionDetector {

    private CollisionDetector() {}

    // ============ XE - TƯỜNG / BIÊN MAP (không đổi) ============
    public static void resolveTankMapCollision(TankEntity tank, GameMap map, double prevX, double prevY, int tankSize) {
        if (map == null || !tank.isAlive()) return;
        if (isTankBlockedByMap(tank.getX(), tank.getY(), tankSize, map)) {
            tank.setX(prevX);
            tank.setY(prevY);
        }
    }

    private static boolean isTankBlockedByMap(double centerX, double centerY, int tankSize, GameMap map) {
        double half = tankSize / 2.0;
        return map.isSolid(centerX - half, centerY - half)
                || map.isSolid(centerX + half, centerY - half)
                || map.isSolid(centerX - half, centerY + half)
                || map.isSolid(centerX + half, centerY + half)
                || map.isOutOfBounds(centerX, centerY);
    }

    // ============ XE - XE (không đổi) ============
    public static void resolveTankTankCollision(Collection<TankEntity> tanks, int tankSize, Map<Integer, double[]> prevPositions) {
        List<TankEntity> aliveTanks = tanks.stream().filter(TankEntity::isAlive).toList();
        for (int i = 0; i < aliveTanks.size(); i++) {
            for (int j = i + 1; j < aliveTanks.size(); j++) {
                TankEntity a = aliveTanks.get(i);
                TankEntity b = aliveTanks.get(j);
                if (isOverlapping(a.getX(), a.getY(), b.getX(), b.getY(), tankSize)) {
                    revertToPrev(a, prevPositions);
                    revertToPrev(b, prevPositions);
                }
            }
        }
    }

    private static void revertToPrev(TankEntity tank, Map<Integer, double[]> prevPositions) {
        double[] prev = prevPositions.get(tank.getId());
        if (prev != null) { tank.setX(prev[0]); tank.setY(prev[1]); }
    }

    private static boolean isOverlapping(double x1, double y1, double x2, double y2, int size) {
        return Math.abs(x1 - x2) < size && Math.abs(y1 - y2) < size;
    }

    // ============ ĐẠN - XE, ĐẠN - TƯỜNG ============
    public static class BulletCollisionResult {
        public final List<CombatEvent> combatEvents;
        public final List<MapChangeEvent> mapChangeEvents;
        public BulletCollisionResult(List<CombatEvent> combatEvents, List<MapChangeEvent> mapChangeEvents) {
            this.combatEvents = combatEvents;
            this.mapChangeEvents = mapChangeEvents;
        }
    }

    public static BulletCollisionResult resolveBulletCollisions(
            Collection<BulletEntity> bullets, Collection<TankEntity> tanks,
            GameMap map, int bulletSize, int tankSize, int normalBulletDamage) {

        List<CombatEvent> combatEvents = new ArrayList<>();
        List<MapChangeEvent> mapEvents = new ArrayList<>();
        int rocketDamage = ConfigLoader.getRocketBulletDamage();
        long now = System.currentTimeMillis();

        for (BulletEntity bullet : bullets) {
            if (!bullet.isAlive()) continue;

            TankEntity hitTank = findTankHitByBullet(bullet, tanks, bulletSize, tankSize);
            if (hitTank != null) {
                bullet.setAlive(false);
                int damage = bullet.getType() == BulletEntity.BulletType.ROCKET ? rocketDamage : normalBulletDamage;

                boolean shieldUp = hitTank.getShieldActiveUntilMillis() > now;
                if (shieldUp && bullet.getType() == BulletEntity.BulletType.NORMAL) {
                    // Khiên chặn đứng đạn thường — không gây sát thương
                    combatEvents.add(new CombatEvent(bullet.getId(), bullet.getOwnerId(), hitTank.getId(), 0, CombatEvent.EventType.SHIELD_BLOCKED));
                } else if (shieldUp && bullet.getType() == BulletEntity.BulletType.ROCKET) {
                    // Tên lửa xuyên khiên: vừa phá khiên vừa gây sát thương
                    combatEvents.add(new CombatEvent(bullet.getId(), bullet.getOwnerId(), hitTank.getId(), damage, CombatEvent.EventType.SHIELD_BROKEN));
                } else {
                    combatEvents.add(new CombatEvent(bullet.getId(), bullet.getOwnerId(), hitTank.getId(), damage, CombatEvent.EventType.BULLET_HIT_TANK));
                }
                continue; // đạn đã tiêu, không xét tường nữa
            }

            if (map != null && map.isSolid(bullet.getX(), bullet.getY())) {
                boolean instaBreak = bullet.getType() == BulletEntity.BulletType.ROCKET;
                GameMap.WallHitResult result = map.hitBrickWall(bullet.getX(), bullet.getY(), instaBreak);
                if (result.broken) {
                    mapEvents.add(new MapChangeEvent(result.row, result.col, /* EMPTY */ 0));
                }
                bullet.setAlive(false);
            }

            if (map != null && map.isOutOfBounds(bullet.getX(), bullet.getY())) {
                bullet.setAlive(false);
            }
        }

        return new BulletCollisionResult(combatEvents, mapEvents);
    }

    private static TankEntity findTankHitByBullet(BulletEntity bullet, Collection<TankEntity> tanks, int bulletSize, int tankSize) {
        double hitDistance = (bulletSize + tankSize) / 2.0;
        for (TankEntity tank : tanks) {
            if (!tank.isAlive()) continue;
            if (tank.getId() == bullet.getOwnerId()) continue;
            if (tank.isProtected()) continue; // Xe đang bảo hộ -> đạn bay xuyên qua, không tính là trúng

            double dx = Math.abs(tank.getX() - bullet.getX());
            double dy = Math.abs(tank.getY() - bullet.getY());
            if (dx < hitDistance && dy < hitDistance) {
                return tank;
            }
        }
        return null;
    }

    // ============ XE - ITEM ============
    public static List<ItemPickupEvent> resolveItemPickups(Collection<TankEntity> tanks, Collection<ItemEntity> items, int tankSize, int itemSize) {
        List<ItemPickupEvent> events = new ArrayList<>();
        double half = (tankSize + itemSize) / 2.0;

        for (ItemEntity item : items) {
            if (!item.isActive()) continue;
            for (TankEntity tank : tanks) {
                if (!tank.isAlive()) continue;
                if (Math.abs(tank.getX() - item.getX()) < half && Math.abs(tank.getY() - item.getY()) < half) {
                    item.setActive(false);
                    events.add(new ItemPickupEvent(tank.getId(), item.getId(), item.getType()));
                    break; // 1 item chỉ 1 xe nhặt được trong cùng 1 tick
                }
            }
        }
        return events;
    }
}