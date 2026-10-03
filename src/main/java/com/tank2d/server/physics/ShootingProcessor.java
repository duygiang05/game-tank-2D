package com.tank2d.server.physics;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.model.TankEntity;

/**
 * Xử lý cơ chế khai hỏa và tính toán vector đạn của xe tăng.
 * <p>
 * Hệ thống hoạt động theo nguyên tắc Server-Authoritative:
 * <ul>
 *     <li>Server tự kiểm tra thời gian hồi chiêu (cooldown) để chống spam packet từ client.</li>
 *     <li>Server tự xác định loại đạn (NORMAL hoặc ROCKET) dựa trên buff hiện hành của xe,
 *         không tin tưởng dữ liệu client tự khai báo.</li>
 *     <li>Tốc độ và hướng bay của viên đạn được tính toán bằng lượng giác dựa trên góc xoay nòng xe.</li>
 * </ul>
 */
public final class ShootingProcessor {

    private ShootingProcessor() {}

    /**
     * Kết quả xử lý yêu cầu bắn đạn.
     */
    public static class ShotResult {
        public final boolean success;
        public final double vx;
        public final double vy;
        public final BulletEntity.BulletType type;

        private ShotResult(boolean success, double vx, double vy, BulletEntity.BulletType type) {
            this.success = success;
            this.vx = vx;
            this.vy = vy;
            this.type = type;
        }

        static ShotResult fail() {
            return new ShotResult(false, 0.0, 0.0, BulletEntity.BulletType.NORMAL);
        }

        static ShotResult ok(double vx, double vy, BulletEntity.BulletType type) {
            return new ShotResult(true, vx, vy, type);
        }
    }

    /**
     * Thử nghiệm bắn một viên đạn từ xe tăng tại thời điểm hiện tại.
     * <p>
     * <b>Quy trình xử lý:</b>
     * <ol>
     *     <li>Kiểm tra tính hợp lệ: xe phải còn sống và đã hết thời gian hồi chiêu ({@code now - lastShot &gt;= cooldown}).</li>
     *     <li>Kiểm tra trạng thái tên lửa: nếu {@code rocketBuffActiveUntilMillis &gt; now}, loại đạn là {@code ROCKET},
     *         ngay sau đó tiêu hao buff bằng cách reset về 0. Ngược lại loại đạn là {@code NORMAL}.</li>
     *     <li>Tính toán vector vận tốc bằng lượng giác:
     *         <ul>
     *             <li>{@code vx = bulletSpeed * cos(radians)}</li>
     *             <li>{@code vy = bulletSpeed * sin(radians)}</li>
     *         </ul>
     *     </li>
     *     <li>Cập nhật {@code lastShotTimeMillis} của xe.</li>
     * </ol>
     *
     * @param tank      thực thể xe tăng thực hiện phát bắn
     * @param nowMillis thời điểm hiện tại của server tính bằng mili-giây
     * @return {@link ShotResult} chứa cờ thành công, thành phần vector vận tốc (vx, vy) và loại đạn
     */
    public static ShotResult tryShoot(TankEntity tank, long nowMillis) {
        if (tank == null || !tank.isAlive()) {
            return ShotResult.fail();
        }

        long cooldownMs = ConfigLoader.getFireCooldownMs();
        if (nowMillis - tank.getLastShotTimeMillis() < cooldownMs) {
            return ShotResult.fail();
        }

        BulletEntity.BulletType actualType = BulletEntity.BulletType.NORMAL;
        if (tank.getRocketBuffActiveUntilMillis() > nowMillis) {
            actualType = BulletEntity.BulletType.ROCKET;
            tank.setRocketBuffActiveUntilMillis(0L);
        }

        double bulletSpeed = (actualType == BulletEntity.BulletType.ROCKET)
                ? ConfigLoader.getMissileBulletSpeedPerSecond()
                : ConfigLoader.getBulletSpeedPerSecond();

        double radians = Math.toRadians(tank.getAngle());
        double vx = bulletSpeed * Math.cos(radians);
        double vy = bulletSpeed * Math.sin(radians);

        tank.setLastShotTimeMillis(nowMillis);
        return ShotResult.ok(vx, vy, actualType);
    }
}