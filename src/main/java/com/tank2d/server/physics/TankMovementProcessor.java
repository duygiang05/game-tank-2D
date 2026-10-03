package com.tank2d.server.physics;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.model.TankEntity;

/**
 * Xử lý động học và chuyển động của xe tăng trên không gian 2D.
 * <p>
 * Hệ thống sử dụng hệ tọa độ màn hình chuẩn:
 * <ul>
 *     <li>Trục X tăng dần từ trái sang phải.</li>
 *     <li>Trục Y tăng dần từ trên xuống dưới.</li>
 *     <li>Góc 0° chỉ sang hướng Đông (+X), góc 90° chỉ xuống hướng Nam (+Y).</li>
 * </ul>
 */
public final class TankMovementProcessor {

    private static final double FULL_ROTATION_DEGREES = 360.0;

    private TankMovementProcessor() {}

    /**
     * Cập nhật góc quay và vị trí mới của xe tăng dựa trên trạng thái điều khiển và thời gian trôi qua.
     * <p>
     * <b>Công thức tính toán:</b>
     * <ol>
     *     <li>Góc quay mới: {@code angleNew = normalize(angleOld ± rotationSpeed * nitroMultiplier * deltaTime)}</li>
     *     <li>Hình chiếu vận tốc dài:
     *         <ul>
     *             <li>{@code vx = speed * cos(angle) * deltaTime}</li>
     *             <li>{@code vy = speed * sin(angle) * deltaTime}</li>
     *         </ul>
     *     </li>
     * </ol>
     *
     * @param tank      thực thể xe tăng cần cập nhật
     * @param deltaTime khoảng thời gian của tick vật lý (giây)
     */
    public static void update(TankEntity tank, double deltaTime) {
        if (tank == null || !tank.isAlive()) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean nitroActive = tank.getNitroActiveUntilMillis() > now;
        double nitroMultiplier = nitroActive ? ConfigLoader.getNitroSpeedMultiplier() : 1.0;

        double angle = tank.getAngle();
        double rotSpeed = tank.getRotationSpeed() * nitroMultiplier;

        if (tank.getRotateState() == TankEntity.RotateState.LEFT) {
            angle -= rotSpeed * deltaTime;
        } else if (tank.getRotateState() == TankEntity.RotateState.RIGHT) {
            angle += rotSpeed * deltaTime;
        }
        angle = normalizeAngle(angle);
        tank.setAngle(angle);

        if (tank.getMoveState() != TankEntity.MoveState.NONE) {
            double speed = tank.getSpeed() * nitroMultiplier;
            double radians = Math.toRadians(angle);
            double vx = speed * Math.cos(radians) * deltaTime;
            double vy = speed * Math.sin(radians) * deltaTime;

            if (tank.getMoveState() == TankEntity.MoveState.FORWARD) {
                tank.setX(tank.getX() + vx);
                tank.setY(tank.getY() + vy);
            } else if (tank.getMoveState() == TankEntity.MoveState.BACKWARD) {
                tank.setX(tank.getX() - vx);
                tank.setY(tank.getY() - vy);
            }
        }
    }

    /**
     * Chuẩn hóa góc quay bất kỳ về nửa khoảng chuẩn [0.0, 360.0) độ.
     * <p>
     * Áp dụng thuật toán modulo số thực:
     * {@code angleNorm = ((angle % 360) + 360) % 360}
     *
     * @param angle góc quay đầu vào tính bằng độ
     * @return góc quay chuẩn hóa trong khoảng [0.0, 360.0)
     */
    public static double normalizeAngle(double angle) {
        angle %= FULL_ROTATION_DEGREES;
        if (angle < 0.0) {
            angle += FULL_ROTATION_DEGREES;
        }
        return angle;
    }
}