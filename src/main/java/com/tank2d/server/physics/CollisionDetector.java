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

/**
 * Bộ xử lý va chạm hình học không gian (Collision Detection &amp; Resolution).
 * <p>
 * Cung cấp các thuật toán:
 * <ul>
 *     <li>Giải quyết va chạm Xe - Bản đồ theo cơ chế trượt 2 trục độc lập (2-Axis Sliding Collision).</li>
 *     <li>Kiểm tra giao cắt hình hộp song song trục tọa độ (Axis-Aligned Bounding Box - AABB).</li>
 *     <li>Phát hiện va chạm Đạn - Xe, Đạn - Tường và kích hoạt sự kiện tác động combat/bản đồ tương ứng.</li>
 *     <li>Xử lý sự kiện nhặt vật phẩm bổ trợ (Power-up Item Pickup).</li>
 * </ul>
 */
public final class CollisionDetector {

    private CollisionDetector() {}

    /**
     * Giải quyết va chạm giữa xe tăng và các khối chướng ngại vật/biên bản đồ theo cơ chế trượt (Sliding Collision).
     * <p>
     * <b>Thuật toán:</b>
     * <ol>
     *     <li>Tách chuyển động thành 2 trục độc lập {@code dx} và {@code dy}.</li>
     *     <li>Thử nghiệm vị trí {@code (newX, prevY)}. Nếu 4 góc của xe giao với vật cản hoặc ra ngoài biên,
     *         trục X bị hủy bỏ và giữ nguyên vị trí cũ {@code newX = prevX}.</li>
     *     <li>Thử nghiệm vị trí {@code (newX, newY)}. Nếu kiểm tra va chạm trả về true,
     *         trục Y bị hủy bỏ và giữ nguyên vị trí cũ {@code newY = prevY}.</li>
     *     <li>Điều này cho phép xe tiếp tục trượt mượt mà dọc theo vách tường thay vì bị khựng lại toàn bộ.</li>
     * </ol>
     *
     * @param tank     thực thể xe tăng đang di chuyển
     * @param map      bản đồ trận đấu chứa ma trận các ô gạch/đá
     * @param prevX    tọa độ X hợp lệ của xe ở tick trước
     * @param prevY    tọa độ Y hợp lệ của xe ở tick trước
     * @param tankSize kích thước cạnh hình vuông bao quanh thân xe (pixel)
     */
    public static void resolveTankMapCollision(TankEntity tank, GameMap map, double prevX, double prevY, int tankSize) {
        if (map == null || tank == null || !tank.isAlive()) {
            return;
        }

        double newX = tank.getX();
        double newY = tank.getY();

        if (isTankBlockedByMap(newX, prevY, tankSize, map)) {
            newX = prevX;
        }

        if (isTankBlockedByMap(newX, newY, tankSize, map)) {
            newY = prevY;
        }

        tank.setX(newX);
        tank.setY(newY);
    }

    /**
     * Kiểm tra xem hình bao của xe tăng tại tọa độ tâm (centerX, centerY) có giao với khối cản hoặc ra ngoài biên map hay không.
     * <p>
     * Kiểm tra 4 đỉnh của hình vuông bao quanh thân xe:
     * {@code (centerX ± halfSize, centerY ± halfSize)}
     *
     * @param centerX  tọa độ tâm X của xe
     * @param centerY  tọa độ tâm Y của xe
     * @param tankSize độ dài cạnh thân xe
     * @param map      bản đồ trận đấu
     * @return {@code true} nếu bất kỳ đỉnh nào chạm khối kiên cố (đá/gạch) hoặc nằm ngoài biên
     */
    private static boolean isTankBlockedByMap(double centerX, double centerY, int tankSize, GameMap map) {
        double half = tankSize / 2.0;
        return map.isSolid(centerX - half, centerY - half)
                || map.isSolid(centerX + half, centerY - half)
                || map.isSolid(centerX - half, centerY + half)
                || map.isSolid(centerX + half, centerY + half)
                || map.isOutOfBounds(centerX, centerY);
    }

    /**
     * Giải quyết va chạm thân xe giữa các xe tăng với nhau theo chuẩn AABB.
     * Nếu 2 xe còn sống giao nhau, cả hai sẽ bị lùi về vị trí hợp lệ ở tick trước.
     *
     * @param tanks         tập hợp tất cả xe tăng trong phòng
     * @param tankSize      kích thước cạnh xe tăng
     * @param prevPositions bản đồ lưu tọa độ hợp lệ trước đó theo ID xe
     */
    public static void resolveTankTankCollision(Collection<TankEntity> tanks, int tankSize, Map<Integer, double[]> prevPositions) {
        if (tanks == null || tanks.isEmpty()) {
            return;
        }

        List<TankEntity> aliveTanks = new ArrayList<>(tanks.size());
        for (TankEntity t : tanks) {
            if (t.isAlive()) {
                aliveTanks.add(t);
            }
        }

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

    /**
     * Kiểm tra giao cắt AABB giữa 2 hình vuông cùng kích thước tâm (x1, y1) và (x2, y2).
     * <p>
     * Điều kiện giao nhau:
     * <p>
     * {@code |x1 - x2| &lt; size &amp;&amp; |y1 - y2| &lt; size}
     *
     * @param x1   tọa độ X tâm đối tượng 1
     * @param y1   tọa độ Y tâm đối tượng 1
     * @param x2   tọa độ X tâm đối tượng 2
     * @param y2   tọa độ Y tâm đối tượng 2
     * @param size độ dài cạnh hình vuông
     * @return {@code true} nếu 2 hình bao cắt nhau
     */
    private static boolean isOverlapping(double x1, double y1, double x2, double y2, int size) {
        return Math.abs(x1 - x2) < size && Math.abs(y1 - y2) < size;
    }

    /**
     * Kết quả tổng hợp sau khi xử lý va chạm của toàn bộ đạn trong tick.
     */
    public static class BulletCollisionResult {
        public final List<CombatEvent> combatEvents;
        public final List<MapChangeEvent> mapChangeEvents;

        public BulletCollisionResult(List<CombatEvent> combatEvents, List<MapChangeEvent> mapChangeEvents) {
            this.combatEvents = combatEvents;
            this.mapChangeEvents = mapChangeEvents;
        }
    }

    /**
     * Xử lý va chạm giữa đạn với xe tăng, đạn với tường bản đồ hoặc đạn bay ra ngoài biên.
     *
     * @param bullets            danh sách đạn còn tồn tại
     * @param tanks              danh sách xe tăng
     * @param map                bản đồ trận đấu
     * @param bulletSize         kích thước đạn
     * @param tankSize           kích thước xe tăng
     * @param normalBulletDamage lượng sát thương chuẩn của đạn thường
     * @return đối tượng {@link BulletCollisionResult} chứa các sự kiện chiến đấu và thay đổi map
     */
    public static BulletCollisionResult resolveBulletCollisions(
            Collection<BulletEntity> bullets, Collection<TankEntity> tanks,
            GameMap map, int bulletSize, int tankSize, int normalBulletDamage) {

        List<CombatEvent> combatEvents = new ArrayList<>();
        List<MapChangeEvent> mapEvents = new ArrayList<>();
        int rocketDamage = ConfigLoader.getRocketBulletDamage();
        long now = System.currentTimeMillis();

        for (BulletEntity bullet : bullets) {
            if (!bullet.isAlive()) {
                continue;
            }

            TankEntity hitTank = findTankHitByBullet(bullet, tanks, bulletSize, tankSize);
            if (hitTank != null) {
                bullet.setAlive(false);
                int damage = bullet.getType() == BulletEntity.BulletType.ROCKET ? rocketDamage : normalBulletDamage;

                boolean shieldUp = hitTank.getShieldActiveUntilMillis() > now;
                if (shieldUp && bullet.getType() == BulletEntity.BulletType.NORMAL) {
                    combatEvents.add(new CombatEvent(bullet.getId(), bullet.getOwnerId(), hitTank.getId(), 0, CombatEvent.EventType.SHIELD_BLOCKED));
                } else if (shieldUp && bullet.getType() == BulletEntity.BulletType.ROCKET) {
                    combatEvents.add(new CombatEvent(bullet.getId(), bullet.getOwnerId(), hitTank.getId(), damage, CombatEvent.EventType.SHIELD_BROKEN));
                } else {
                    combatEvents.add(new CombatEvent(bullet.getId(), bullet.getOwnerId(), hitTank.getId(), damage, CombatEvent.EventType.BULLET_HIT_TANK));
                }
                continue;
            }

            if (map != null && map.isSolid(bullet.getX(), bullet.getY())) {
                boolean instaBreak = bullet.getType() == BulletEntity.BulletType.ROCKET;
                GameMap.WallHitResult result = map.hitBrickWall(bullet.getX(), bullet.getY(), instaBreak);

                if (result.hit) {
                    if (result.broken) {
                        mapEvents.add(new MapChangeEvent(result.row, result.col, 0));
                    } else {
                        combatEvents.add(new CombatEvent(
                            bullet.getId(),
                            bullet.getOwnerId(),
                            result.row * 10000 + result.col,
                            1,
                            CombatEvent.EventType.WALL_HIT
                        ));
                    }
                }
                bullet.setAlive(false);
            }

            if (map != null && map.isOutOfBounds(bullet.getX(), bullet.getY())) {
                bullet.setAlive(false);
            }
        }

        return new BulletCollisionResult(combatEvents, mapEvents);
    }

    /**
     * Tìm xe tăng đầu tiên bị viên đạn bắn trúng theo hình bao AABB.
     * Đạn sẽ bỏ qua xe của chính chủ bắn, xe đã chết hoặc xe đang trong trạng thái bảo hộ hồi sinh (Ghost).
     */
    private static TankEntity findTankHitByBullet(BulletEntity bullet, Collection<TankEntity> tanks, int bulletSize, int tankSize) {
        double hitDistance = (bulletSize + tankSize) / 2.0;
        for (TankEntity tank : tanks) {
            if (!tank.isAlive() || tank.getId() == bullet.getOwnerId() || tank.isProtected()) {
                continue;
            }

            double dx = Math.abs(tank.getX() - bullet.getX());
            double dy = Math.abs(tank.getY() - bullet.getY());
            if (dx < hitDistance && dy < hitDistance) {
                return tank;
            }
        }
        return null;
    }

    /**
     * Xử lý va chạm nhặt vật phẩm hỗ trợ giữa xe tăng và các vật phẩm rơi trên bản đồ.
     * Mỗi vật phẩm chỉ có thể được nhặt bởi tối đa 1 xe trong cùng tick.
     *
     * @param tanks    danh sách xe tăng
     * @param items    danh sách vật phẩm
     * @param tankSize kích thước xe
     * @param itemSize kích thước vật phẩm
     * @return danh sách các sự kiện nhặt vật phẩm {@link ItemPickupEvent}
     */
    public static List<ItemPickupEvent> resolveItemPickups(Collection<TankEntity> tanks, Collection<ItemEntity> items, int tankSize, int itemSize) {
        List<ItemPickupEvent> events = new ArrayList<>();
        double half = (tankSize + itemSize) / 2.0;

        for (ItemEntity item : items) {
            if (!item.isActive()) {
                continue;
            }
            for (TankEntity tank : tanks) {
                if (!tank.isAlive() || tank.isProtected()) {
                    continue;
                }
                if (Math.abs(tank.getX() - item.getX()) < half && Math.abs(tank.getY() - item.getY()) < half) {
                    item.setActive(false);
                    events.add(new ItemPickupEvent(tank.getId(), item.getId(), item.getType()));
                    break;
                }
            }
        }
        return events;
    }
}
