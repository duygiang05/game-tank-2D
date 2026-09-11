package com.tank2d.server.demo;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.game.GameSnapshotDTO;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.input.PlayerInputHandler;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.map.MapLoader;
import com.tank2d.server.model.TankEntity;

/**
 * DEMO NGHIỆM THU — TASK 2: Collision Engine & Broadcast Map/Snapshot.
 */
public class Task2_CollisionAndSnapshotDemo {

    public static void main(String[] args) throws InterruptedException {
        GameLoop loop = new GameLoop(30);
        GameMap map = MapLoader.loadFromFile("config/maps/map_default.json");
        loop.setGameMap(map);

        // Giả lập "Server Core của Giang" bằng 1 listener in log — chứng minh event bắn đúng lúc
        loop.setCombatListener(event ->
                System.out.println(">>> [GIANG NHẬN ĐƯỢC] CombatEvent: bullet#" + event.getBulletId()
                        + " shooter=" + event.getShooterId() + " -> trúng tank#" + event.getTargetTankId()
                        + " (-" + event.getDamage() + " HP)  [tick=" + loop.getTickCount() + "]"));

        // Giả lập "Client của Tùng" nhận snapshot định kỳ — chỉ log mỗi 10 tick cho đỡ rối, còn thực tế nhận mỗi tick
        loop.setSnapshotListener(snapshot -> {
            if (snapshot.getTick() % 10 == 0) {
                System.out.println(">>> [CLIENT NHẬN SNAPSHOT] tick=" + snapshot.getTick()
                        + " | tanks=" + snapshot.getTanks().size()
                        + " | bullets=" + snapshot.getBullets().size());
            }
        });

        double tankSpeed = ConfigLoader.getTankSpeedPerSecond();
        PlayerInputHandler inputHandler = new PlayerInputHandler();

        System.out.println("=== TIÊU CHÍ 1: Đạn-Xe -> sự kiện lập tức ===");
        TankEntity shooter = new TankEntity(1, 100, 100, 0, tankSpeed, 90.0);
        shooter.initHp(ConfigLoader.getMaxHp());
        TankEntity target = new TankEntity(2, 140, 100, 180, tankSpeed, 90.0);
        target.initHp(ConfigLoader.getMaxHp());
        loop.addTank(shooter);
        loop.addTank(target);
        inputHandler.handleShootRequest(loop, shooter);

        for (int t = 1; t <= 15; t++) {
            loop.tick(1.0 / 30.0);
        }
        System.out.println("Kết quả target: hp=" + target.getHp() + " alive=" + target.isAlive());

        System.out.println("\n=== TIÊU CHÍ 2: Xe-Tường/Biên map -> chặn xuyên tường ===");
        // (1,1) là góc map trống theo map_default.json, đặt xe sát mép trên-trái rồi cho lùi -> phải bị chặn bởi tường đá viền ngoài
        TankEntity wallTester = new TankEntity(3, 45, 45, 225, tankSpeed, 90.0); // 225° ~ hướng Tây-Bắc, đi thẳng vào tường viền
        wallTester.initHp(ConfigLoader.getMaxHp());
        loop.addTank(wallTester);
        inputHandler.handlePlayerInput(wallTester, "{\"move\":\"FORWARD\",\"rotate\":\"NONE\"}");

        double lastX = wallTester.getX(), lastY = wallTester.getY();
        for (int t = 1; t <= 30; t++) {
            loop.tick(1.0 / 30.0);
        }
        System.out.printf("Xe trước khi đi: (%.1f, %.1f) | Sau 30 tick lao vào tường: (%.1f, %.1f)%n",
                lastX, lastY, wallTester.getX(), wallTester.getY());
        System.out.println("(Nếu bị chặn đúng, tọa độ sẽ dừng lại ở rìa tường, KHÔNG vượt qua tọa độ âm/ra ngoài map)");

        System.out.println("\n=== TIÊU CHÍ 3: Snapshot broadcast định kỳ ===");
        System.out.println("(Xem các dòng '[CLIENT NHẬN SNAPSHOT]' phía trên — phải xuất hiện đều mỗi 10 tick, không bị gián đoạn)");

        GameSnapshotDTO finalSnapshot = loop.buildSnapshot();
        System.out.println("\nSnapshot cuối cùng (mẫu JSON thật sẽ gửi qua mạng):");
        System.out.println(new com.google.gson.Gson().toJson(finalSnapshot));
    }
}