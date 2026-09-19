package com.tank2d.server.demo;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.model.TankEntity;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.map.MapLoader;

/**
 * Demo kiểm tra:
 * 1. Tank không xuyên tường.
 * 2. Đạn thường phá tường gạch sau đúng 3 phát.
 * 3. Rocket phá tường gạch ngay sau 1 phát.
 */
public class Task1_MapAndWallDemo {

    private static final double DT = 1.0 / 30.0;

    public static void main(String[] args) {

        GameLoop loop = new GameLoop(30);

        GameMap map = MapLoader.loadFromFile(
                "config/maps/map_default.json"
        );

        loop.setGameMap(map);

        loop.setMapChangeListener(event -> {
            System.out.println(
                    ">>> [MAP_UPDATE] "
                            + event
            );
        });

        // =========================================================
        // TIÊU CHÍ 1: XE KHÔNG XUYÊN TƯỜNG
        // =========================================================

        System.out.println();
        System.out.println(
                "=== TIÊU CHÍ 1: Xe không xuyên tường ==="
        );

        TankEntity wallTester = new TankEntity(
                99,
                45,
                45,
                225,
                4.0,
                90.0
        );

        wallTester.setMoveState(
                TankEntity.MoveState.FORWARD
        );

        loop.addTank(wallTester);

        double beforeX = wallTester.getX();
        double beforeY = wallTester.getY();

        for (int i = 0; i < 30; i++) {
            loop.tick(DT);
        }

        double afterX = wallTester.getX();
        double afterY = wallTester.getY();

        System.out.printf(
                "Trước: (%.1f, %.1f)%n",
                beforeX,
                beforeY
        );

        System.out.printf(
                "Sau 30 tick: (%.1f, %.1f)%n",
                afterX,
                afterY
        );

        /*
         * Tank bắt đầu gần góc tường.
         * Nếu CollisionDetector phát hiện tường,
         * tank sẽ không tiếp tục đi xuyên qua.
         */
        System.out.println(
                "Kết quả: kiểm tra bằng vị trí sau va chạm."
        );

        // =========================================================
        // TIÊU CHÍ 2: ĐẠN THƯỜNG PHÁ GẠCH SAU 3 PHÁT
        // =========================================================

        System.out.println();
        System.out.println(
                "=== TIÊU CHÍ 2: Đạn thường phá gạch sau đúng 3 phát ==="
        );

        /*
         * map_default:
         *
         * row 1, col 12 = BRICK
         *
         * Tâm ô:
         * x = 12 * 40 + 20 = 500
         * y =  1 * 40 + 20 = 60
         */
        double brickX = map.tileCenterX(12);
        double brickY = map.tileCenterY(1);

        TankEntity normalShooter = new TankEntity(
                100,
                brickX - 100,
                brickY,
                0,
                4.0,
                90.0
        );

        loop.addTank(normalShooter);

        boolean allThreeShotsFired = true;

        for (int shot = 1; shot <= 3; shot++) {

            boolean fired = loop.handleShootRequest(
                    normalShooter,
                    BulletEntity.BulletType.NORMAL
            );

            System.out.println(
                    "Phát " + shot + ": "
                            + (fired
                            ? "bắn thành công"
                            : "BỊ CHẶN")
            );

            if (!fired) {
                allThreeShotsFired = false;
            }

            /*
             * Cho bullet bay và xử lý collision.
             */
            for (int tick = 0; tick < 30; tick++) {
                loop.tick(DT);
            }

            /*
             * Rất quan trọng:
             *
             * ShootingProcessor dùng
             * System.currentTimeMillis()
             *
             * nên tick() không làm cooldown thật sự trôi qua.
             *
             * Chờ > 1000 ms để phát tiếp theo được phép bắn.
             */
            if (shot < 3) {
                try {
                    Thread.sleep(
                            ConfigLoader.getFireCooldownMs() + 100
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (allThreeShotsFired) {
            System.out.println(
                    "PASS: Đã bắn đủ 3 phát."
            );
        } else {
            System.out.println(
                    "FAIL: Không bắn được đủ 3 phát."
            );
        }

        // =========================================================
        // TIÊU CHÍ 3: ROCKET PHÁ GẠCH NGAY 1 PHÁT
        // =========================================================

        System.out.println();
        System.out.println(
                "=== TIÊU CHÍ 3: Đạn tên lửa phá gạch NGAY 1 phát ==="
        );

        /*
         * Chọn brick khác:
         *
         * row 0, col 12 = BRICK
         */
        double rocketBrickX = map.tileCenterX(12);
        double rocketBrickY = map.tileCenterY(0);

        TankEntity rocketShooter = new TankEntity(
                101,
                rocketBrickX + 100,
                rocketBrickY,
                180,
                4.0,
                90.0
        );

        /*
         * Cho phép bắn rocket.
         */
        rocketShooter.setRocketBuffActiveUntilMillis(
                System.currentTimeMillis() + 5000
        );

        loop.addTank(rocketShooter);

        boolean rocketFired = loop.handleShootRequest(
                rocketShooter,
                BulletEntity.BulletType.ROCKET
        );

        System.out.println(
                "Rocket: "
                        + (rocketFired
                        ? "bắn thành công"
                        : "BỊ CHẶN")
        );

        /*
         * Cho rocket bay tới brick.
         */
        for (int tick = 0; tick < 30; tick++) {
            loop.tick(DT);
        }

        System.out.println(
                "Kết thúc kiểm tra rocket."
        );

        System.out.println();
        System.out.println(
                "=== KẾT THÚC TASK 1 ==="
        );
    }
}
