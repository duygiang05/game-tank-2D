package com.tank2d.server.physics;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.model.TankEntity;

public final class ShootingProcessor {

    private ShootingProcessor() {}

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

        static ShotResult fail() { return new ShotResult(false, 0, 0, BulletEntity.BulletType.NORMAL); }
        static ShotResult ok(double vx, double vy, BulletEntity.BulletType type) { return new ShotResult(true, vx, vy, type); }
    }

    /**
     * @param requestedType loại đạn CLIENT muốn bắn — chỉ thật sự bắn ROCKET nếu tank đang có buff tên lửa còn hiệu lực,
     *                       ngược lại server tự hạ về NORMAL (không tin tưởng client tự xưng có đạn tên lửa).
     */
    public static ShotResult tryShoot(TankEntity tank, BulletEntity.BulletType requestedType, long nowMillis) {
        if (tank == null || !tank.isAlive()) {
            return ShotResult.fail();
        }

        long cooldownMs = ConfigLoader.getFireCooldownMs();
        if (nowMillis - tank.getLastShotTimeMillis() < cooldownMs) {
            return ShotResult.fail(); // đang khóa cooldown
        }

        // TỰ ĐỘNG BẬT ROCKET NẾU XE CÒN BUFF TRÊN SERVER (KHÔNG PHỤ THUỘC CLIENT GỬI GÌ)
        BulletEntity.BulletType actualType = BulletEntity.BulletType.NORMAL;
        if (tank.getRocketBuffActiveUntilMillis() > nowMillis) {
            actualType = BulletEntity.BulletType.ROCKET;
            // Vừa bấm bắn đạn Rocket xong là tiêu hao Buff ngay lập tức (về 0)
            tank.setRocketBuffActiveUntilMillis(0L);
        }

        // LẤY TỐC ĐỘ TỪ CONFIGLOADER TƯƠNG ỨNG TỪNG LOẠI ĐẠN
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