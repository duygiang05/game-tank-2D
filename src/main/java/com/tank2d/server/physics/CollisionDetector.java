package com.tank2d.server.physics;

import com.tank2d.server.game.event.CombatEvent;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.model.TankEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class CollisionDetector {

    private CollisionDetector() {}
    public static void resolveTankMapCollision(TankEntity tank, GameMap map, double prevX, double prevY, int tankSize) {
        if (map == null || !tank.isAlive()) return;
        if (isTankBlockedByMap(tank.getX(), tank.getY(), tankSize, map)) {
            tank.setX(prevX);
            tank.setY(prevY);
        }
    }

    private static boolean isTankBlockedByMap(double centerX, double centerY, int tankSize, GameMap map) {
        double half = tankSize / 2.0;
        // Kiểm tra 4 góc bounding box của xe — đủ chính xác cho AABB đơn giản
        return map.isSolid(centerX - half, centerY - half)
                || map.isSolid(centerX + half, centerY - half)
                || map.isSolid(centerX - half, centerY + half)
                || map.isSolid(centerX + half, centerY + half)
                || map.isOutOfBounds(centerX, centerY);
    }

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
        if (prev != null) {
            tank.setX(prev[0]);
            tank.setY(prev[1]);
        }
    }

    private static boolean isOverlapping(double x1, double y1, double x2, double y2, int size) {
        return Math.abs(x1 - x2) < size && Math.abs(y1 - y2) < size;
    }

    public static List<CombatEvent> resolveBulletCollisions(
            Collection<BulletEntity> bullets, Collection<TankEntity> tanks,
            GameMap map, int bulletSize, int tankSize, int normalBulletDamage) {

        List<CombatEvent> events = new ArrayList<>();

        for (BulletEntity bullet : bullets) {
            if (!bullet.isAlive()) continue;

            // 1. Đạn - Xe (kiểm tra trước, ưu tiên gây sát thương nếu trùng cả tường lẫn xe ở biên)
            TankEntity hitTank = findTankHitByBullet(bullet, tanks, bulletSize, tankSize);
            if (hitTank != null) {
                bullet.setAlive(false);
                events.add(new CombatEvent(
                        bullet.getId(), bullet.getOwnerId(), hitTank.getId(),
                        normalBulletDamage, CombatEvent.EventType.BULLET_HIT_TANK
                ));
                continue;
            }

            // 2. Đạn - Tường (chỉ xét nếu chưa trúng xe)
            if (map != null && map.isSolid(bullet.getX(), bullet.getY())) {
                map.hitBrickWall(bullet.getX(), bullet.getY()); // không ảnh hưởng gì nếu là tường đá
                bullet.setAlive(false);
            }

            // 3. Đạn bay ra ngoài biên map -> tự huỷ
            if (map != null && map.isOutOfBounds(bullet.getX(), bullet.getY())) {
                bullet.setAlive(false);
            }
        }

        return events;
    }

    private static TankEntity findTankHitByBullet(BulletEntity bullet, Collection<TankEntity> tanks, int bulletSize, int tankSize) {
        double hitDistance = (bulletSize + tankSize) / 2.0;
        for (TankEntity tank : tanks) {
            if (!tank.isAlive()) continue;
            if (tank.getId() == bullet.getOwnerId()) continue; // không tự bắn trúng chính mình
            double dx = Math.abs(tank.getX() - bullet.getX());
            double dy = Math.abs(tank.getY() - bullet.getY());
            if (dx < hitDistance && dy < hitDistance) {
                return tank;
            }
        }
        return null;
    }
}