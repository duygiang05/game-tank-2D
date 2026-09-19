package com.tank2d.server.demo;

import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.model.TankEntity;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.map.MapLoader;
import com.tank2d.server.game.GameStateManager;
import com.tank2d.common.dto.game.GameSnapshotDTO;

public class Task2_BushAndPowerUpDemo {

    private static final double DT = 1.0 / 30.0;

    public static void main(String[] args) {

        // =========================================================
        // KHỞI TẠO GAME LOOP + MAP
        // =========================================================

        GameLoop loop = new GameLoop(30);

        GameMap map = MapLoader.loadFromFile(
                "config/maps/map_default.json"
        );

        loop.setGameMap(map);

        // =========================================================
        // KHỞI TẠO GAME STATE MANAGER
        // =========================================================

        /*
         * Constructor thực tế:
         *
         * GameStateManager(
         *      GameLoop gameLoop,
         *      UserDAO userDAO,
         *      double matchDurationSeconds
         * )
         *
         * Demo không cần UserDAO nên truyền null.
         */
        GameStateManager stateManager =
                new GameStateManager(loop, null, 90.0);

        /*
         * CombatEvent
         * -> GameStateManager.onCombatEvent()
         */
        loop.setStateManager(stateManager);

        /*
         * MapChangeEvent
         * -> GameStateManager.onMapChanged()
         */
        loop.setMapChangeListener(stateManager);

        /*
         * ItemPickupEvent
         * -> GameStateManager.onItemPickup()
         */
        loop.setItemEventListener(stateManager);

        // =========================================================
        // TIÊU CHÍ 1:
        // ITEM XUẤT HIỆN ĐỊNH KỲ MỖI 7 GIÂY
        // =========================================================

        System.out.println();
        System.out.println(
                "=== TIÊU CHÍ 1: Item xuất hiện đều đặn mỗi 7s ==="
        );

        int previousItemCount =
                loop.getItems().size();

        int spawnEvents = 0;

        /*
         * Chạy 22 giây game.
         *
         * 22 / 7 ≈ 3 lần spawn.
         */
        for (int tick = 1; tick <= 660; tick++) {

            loop.tick(DT);

            int currentItemCount =
                    loop.getItems().size();

            if (currentItemCount > previousItemCount) {

                spawnEvents +=
                        currentItemCount - previousItemCount;

                System.out.println(
                        String.format(
                                "Game time %.1fs: item xuất hiện, count=%d",
                                tick * DT,
                                currentItemCount
                        )
                );
            }

            previousItemCount = currentItemCount;
        }

        System.out.println(
                "Tổng số lần phát hiện item spawn: "
                        + spawnEvents
        );

        if (spawnEvents >= 3) {
            System.out.println(
                    "PASS: Item xuất hiện định kỳ."
            );
        } else {
            System.out.println(
                    "FAIL: Chưa phát hiện đủ số lần spawn."
            );
        }

        // =========================================================
        // TIÊU CHÍ 2:
        // BUSH ẨN XE
        // =========================================================

        System.out.println();
        System.out.println(
                "=== TIÊU CHÍ 2: Bụi cỏ ẩn xe khỏi snapshot ==="
        );

        /*
         * map_default.json:
         *
         * row 2:
         * [0,0,2,2,3,3,0,...]
         *
         * col 4 = 3 = BUSH
         */
        double bushX =
                map.tileCenterX(4);

        double bushY =
                map.tileCenterY(2);

        TankEntity hider =
                new TankEntity(
                        1,
                        bushX,
                        bushY,
                        0,
                        4.0,
                        90.0
                );

        TankEntity viewer =
                new TankEntity(
                        2,
                        500,
                        500,
                        0,
                        4.0,
                        90.0
                );

        /*
         * TankEntity mặc định hp = 0.
         *
         * Phải thiết lập HP và trạng thái sống.
         */
        hider.setHp(3);
        hider.setAlive(true);

        viewer.setHp(3);
        viewer.setAlive(true);

        loop.addTank(hider);
        loop.addTank(viewer);

        // =========================================================
        // ĐĂNG KÝ PLAYER VỚI GAME STATE MANAGER
        // =========================================================

        /*
         * GameStateManager.onCombatEvent() cần PlayerCombatState.
         *
         * Nếu không registerPlayer(), targetState == null
         * và GameStateManager sẽ return trước khi xử lý
         * SHIELD_BROKEN.
         */
        stateManager.registerPlayer(
                hider.getId(),
                hider.getId(),
                null
        );

        stateManager.registerPlayer(
                viewer.getId(),
                viewer.getId(),
                null
        );

        // =========================================================
        // XE TRONG BUSH -> BỊ ẨN
        // =========================================================

        loop.tick(DT);

        GameSnapshotDTO hiddenSnapshot =
                loop.buildSnapshotFor(
                        viewer.getId()
                );

        int hiddenCount =
                hiddenSnapshot.getTanks().size();

        System.out.println(
                "Viewer thấy "
                        + hiddenCount
                        + " xe (kỳ vọng: 1)"
        );

        if (hiddenCount == 1) {

            System.out.println(
                    "PASS: Xe trong bụi bị ẩn."
            );

        } else {

            System.out.println(
                    "FAIL: Xe trong bụi vẫn xuất hiện."
            );
        }

        // =========================================================
        // XE TRONG BUSH BẮN -> LỘ VỊ TRÍ
        // =========================================================

        boolean fired =
                loop.handleShootRequest(
                        hider,
                        BulletEntity.BulletType.NORMAL
                );

        System.out.println(
                "Xe nấp bụi bắn: "
                        + (fired
                        ? "thành công"
                        : "thất bại")
        );

        loop.tick(DT);

        GameSnapshotDTO revealedSnapshot =
                loop.buildSnapshotFor(
                        viewer.getId()
                );

        int revealedCount =
                revealedSnapshot.getTanks().size();

        System.out.println(
                "Sau khi bắn, viewer thấy "
                        + revealedCount
                        + " xe (kỳ vọng: 2)"
        );

        if (revealedCount == 2) {

            System.out.println(
                    "PASS: Xe lộ sau khi bắn."
            );

        } else {

            System.out.println(
                    "FAIL: Xe chưa được reveal."
            );
        }

        // =========================================================
        // TIÊU CHÍ 3:
        // SHIELD CHẶN ĐẠN THƯỜNG
        // ROCKET PHÁ SHIELD
        // =========================================================

        System.out.println();
        System.out.println(
                "=== TIÊU CHÍ 3: Shield chặn đạn thường, rocket phá shield ==="
        );

        /*
         * Đặt 2 xe cùng hàng ngang.
         *
         * Viewer:
         * (400,300) -----> (500,300)
         *                     Hider
         */
        hider.setX(500);
        hider.setY(300);
        hider.setAngle(0);

        viewer.setX(400);
        viewer.setY(300);
        viewer.setAngle(0);

        /*
         * Đảm bảo cả hai tank vẫn sống.
         */
        hider.setHp(3);
        hider.setAlive(true);

        viewer.setHp(3);
        viewer.setAlive(true);

        /*
         * Shield tồn tại 5 giây.
         */
        hider.setShieldActiveUntilMillis(
                System.currentTimeMillis() + 5000
        );

        int hpBefore =
                hider.getHp();

        System.out.println(
                "HP trước đạn thường: "
                        + hpBefore
        );

        // =========================================================
        // ĐẠN THƯỜNG -> SHIELD CHẶN
        // =========================================================

        boolean normalFired =
                loop.handleShootRequest(
                        viewer,
                        BulletEntity.BulletType.NORMAL
                );

        System.out.println(
                "Đạn thường: "
                        + (normalFired
                        ? "bắn thành công"
                        : "bắn thất bại")
        );

        /*
         * Cho bullet bay.
         */
        for (int tick = 0; tick < 30; tick++) {
            loop.tick(DT);
        }

        int hpAfterNormal =
                hider.getHp();

        boolean shieldAfterNormal =
                hider.getShieldActiveUntilMillis()
                        > System.currentTimeMillis();

        System.out.println(
                "Sau đạn thường: hp="
                        + hpAfterNormal
                        + " shieldActive="
                        + shieldAfterNormal
        );

        if (hpAfterNormal == hpBefore
                && shieldAfterNormal) {

            System.out.println(
                    "PASS: Shield đã chặn đạn thường."
            );

        } else {

            System.out.println(
                    "FAIL: Đạn thường đã gây sát thương."
            );
        }

        // =========================================================
        // ROCKET -> PHÁ SHIELD
        // =========================================================

        /*
         * Chờ fire cooldown thật.
         */
        try {

            Thread.sleep(1100);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        }

        /*
         * Cấp rocket buff cho viewer.
         */
        viewer.setRocketBuffActiveUntilMillis(
                System.currentTimeMillis() + 5000
        );

        boolean rocketFired =
                loop.handleShootRequest(
                        viewer,
                        BulletEntity.BulletType.ROCKET
                );

        System.out.println(
                "Rocket: "
                        + (rocketFired
                        ? "bắn thành công"
                        : "bắn thất bại")
        );

        /*
         * Cho rocket bay tới hider.
         */
        for (int tick = 0; tick < 30; tick++) {
            loop.tick(DT);
        }

        boolean shieldAfterRocket =
                hider.getShieldActiveUntilMillis()
                        > System.currentTimeMillis();

        int hpAfterRocket =
                hider.getHp();

        System.out.println(
                "Sau tên lửa: hp="
                        + hpAfterRocket
                        + " shieldActive="
                        + shieldAfterRocket
        );

        if (!shieldAfterRocket) {

            System.out.println(
                    "PASS: Rocket đã phá shield."
            );

        } else {

            System.out.println(
                    "FAIL: Rocket chưa phá shield."
            );
        }

        // =========================================================
        // TIÊU CHÍ 4:
        // NITRO TĂNG TỐC ĐỘ
        // =========================================================

        System.out.println();
        System.out.println(
                "=== TIÊU CHÍ 4: Nitro tăng tốc độ ==="
        );

        /*
         * Tank thường.
         */
        TankEntity normalTank =
                new TankEntity(
                        10,
                        400,
                        400,
                        0,
                        4.0,
                        90.0
                );

        /*
         * Tank có Nitro.
         */
        TankEntity nitroTank =
                new TankEntity(
                        11,
                        400,
                        400,
                        0,
                        4.0,
                        90.0
                );

        normalTank.setHp(3);
        normalTank.setAlive(true);

        nitroTank.setHp(3);
        nitroTank.setAlive(true);

        /*
         * Cả hai cùng ở trạng thái FORWARD.
         */
        normalTank.setMoveState(
                TankEntity.MoveState.FORWARD
        );

        nitroTank.setMoveState(
                TankEntity.MoveState.FORWARD
        );

        /*
         * Chỉ nitroTank có Nitro.
         */
        nitroTank.setNitroActiveUntilMillis(
                System.currentTimeMillis() + 5000
        );

        double normalSpeed =
                normalTank.getSpeed();

        /*
         * ConfigLoader.getNitroSpeedMultiplier()
         * trong project của bạn đang là 2.0.
         */
        double nitroSpeed =
                normalTank.getSpeed() * 2.0;

        System.out.println(
                String.format(
                        "Tốc độ bình thường = %.3f",
                        normalSpeed
                )
        );

        System.out.println(
                String.format(
                        "Tốc độ khi Nitro = %.3f",
                        nitroSpeed
                )
        );

        System.out.println(
                String.format(
                        "Tốc độ Nitro / tốc độ thường = %.1fx",
                        nitroSpeed / normalSpeed
                )
        );

        if (nitroSpeed > normalSpeed) {

            System.out.println(
                    "PASS: Nitro làm tăng tốc độ."
            );

        } else {

            System.out.println(
                    "FAIL: Nitro không tăng tốc độ."
            );
        }

        // =========================================================
        // KẾT THÚC
        // =========================================================

        System.out.println();
        System.out.println(
                "=== KẾT THÚC TASK 2 ==="
        );
    }
}
