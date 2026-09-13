package com.tank2d.server.physics;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.model.TankEntity;

public final class ShootingProcessor {

    private ShootingProcessor() {}

    public static class ShotResult {
        public final boolean success;
        public final double vx;
        public final double vy;

        private ShotResult(boolean success, double vx, double vy) {
            this.success = success;
            this.vx = vx;
            this.vy = vy;
        }

        static ShotResult fail() { return new ShotResult(false, 0, 0); }
        static ShotResult ok(double vx, double vy) { return new ShotResult(true, vx, vy); }
    }
    public static ShotResult tryShoot(TankEntity tank, long nowMillis) {
        if (tank == null || !tank.isAlive()) {
            return ShotResult.fail();
        }

        long cooldownMs = ConfigLoader.getFireCooldownMs();
        if (nowMillis - tank.getLastShotTimeMillis() < cooldownMs) {
            return ShotResult.fail(); // đang khóa spam đạn
        }

        double bulletSpeed = ConfigLoader.getBulletSpeedPerSecond();
        double radians = Math.toRadians(tank.getAngle());
        double vx = bulletSpeed * Math.cos(radians);
        double vy = bulletSpeed * Math.sin(radians);

        tank.setLastShotTimeMillis(nowMillis); // ghi nhận thời điểm bắn ngay khi hợp lệ
        return ShotResult.ok(vx, vy);
    }
}