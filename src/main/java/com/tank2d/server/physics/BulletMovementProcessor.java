package com.tank2d.server.physics;

import com.tank2d.server.model.BulletEntity;

/**
 * Xử lý cập nhật tọa độ tịnh tiến của viên đạn dựa trên vector vận tốc và thời gian tick.
 */
public final class BulletMovementProcessor {

    private BulletMovementProcessor() {}

    /**
     * Tịnh tiến viên đạn theo vector vận tốc:
     * <p>
     * {@code x = x + vx * deltaTime}
     * <br>
     * {@code y = y + vy * deltaTime}
     *
     * @param bullet    thực thể viên đạn cần cập nhật
     * @param deltaTime khoảng thời gian của tick vật lý (giây)
     */
    public static void update(BulletEntity bullet, double deltaTime) {
        if (bullet == null || !bullet.isAlive()) {
            return;
        }
        bullet.setX(bullet.getX() + bullet.getVx() * deltaTime);
        bullet.setY(bullet.getY() + bullet.getVy() * deltaTime);
    }
}