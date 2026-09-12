package com.tank2d.server.demo;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.game.GameSnapshotDTO;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.input.PlayerInputHandler;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.map.MapLoader;
import com.tank2d.server.model.TankEntity;

public class Task2_CollisionAndSnapshotDemo {

    public static void main(String[] args) throws InterruptedException {
        GameLoop loop = new GameLoop(30);
        GameMap map = MapLoader.loadFromFile("config/maps/map_default.json");
        loop.setGameMap(map);

        // ================== Giả lập "Server Core của Giang" ==================
        loop.setCombatListener(event -> {
            System.out.println(">>> [GIANG NHẬN ĐƯỢC] CombatEvent: bullet#" + event.getBulletId()
                    + " shooter=" + event.getShooterId() + " -> trúng tank#" + event.getTargetTankId()
                    + " (-" + event.getDamage() + " HP)  [tick=" + loop.getTickCount() + "]");

            // MÔ PHỎNG lại phần xử lý Game State mà Giang sẽ làm thật (trừ HP, set alive).
            // CollisionDetector của Hoàng KHÔNG tự làm việc này — chỉ báo tin qua CombatEvent.
            TankEntity target = loop.getTank(event.getTargetTankId());
            if (target != null) {
                int newHp = Math.max(target.getHp() - event.getDamage(), 0);
                target.setHp(newHp);
                if (newHp <= 0) {
                    target.setAlive(false);
                }
            }
        });

        // ================== Giả lập "Client của Tùng" ==================
        // Thực tế Client sẽ nhận MỌI tick; ở đây chỉ log mỗi 10 tick cho đỡ rối console.
        loop.setSnapshotListener(snapshot -> {
            if (snapshot.getTick() % 10 == 0) {
                System.out.println(">>> [CLIENT NHẬN SNAPSHOT] tick=" + snapshot.getTick()
                        + " | tanks=" + snapshot.getTanks().size()
                        + " | bullets=" + snapshot.getBullets().size());
            }
        });

        double tankSpeed = ConfigLoader.getTankSpeedPerSecond();
        PlayerInputHandler inputHandler = new PlayerInputHandler();

        // ================== TIÊU CHÍ 1: Đạn-Xe -> sự kiện lập tức ==================
        System.out.println("=== TIÊU CHÍ 1: Đạn-Xe -> sự kiện lập tức ===");
        TankEntity shooter = new TankEntity(1, 100, 100, 0, tankSpeed, 90.0);
        shooter.setHp(ConfigLoader.getMaxHp());

        TankEntity target = new TankEntity(2, 140, 100, 180, tankSpeed, 90.0); // đứng bên phải shooter
        target.setHp(ConfigLoader.getMaxHp());

        loop.addTank(shooter);
        loop.addTank(target);

        boolean fired = inputHandler.handleShootRequest(loop, shooter);
        System.out.println("Bắn: " + (fired ? "THÀNH CÔNG" : "BỊ CHẶN"));

        for (int t = 1; t <= 15; t++) {
            loop.tick(1.0 / 30.0);
        }
        System.out.println("Kết quả target: hp=" + target.getHp() + " alive=" + target.isAlive());

        // ================== TIÊU CHÍ 2: Xe-Tường/Biên map -> chặn xuyên tường ==================
        System.out.println("\n=== TIÊU CHÍ 2: Xe-Tường/Biên map -> chặn xuyên tường ===");
        // (45,45) nằm gần góc trên-trái map_default.json; góc 225° hướng về phía tường viền (Tây-Bắc)
        TankEntity wallTester = new TankEntity(3, 45, 45, 225, tankSpeed, 90.0);
        wallTester.setHp(ConfigLoader.getMaxHp());
        loop.addTank(wallTester);

        inputHandler.handlePlayerInput(wallTester, "{\"move\":\"FORWARD\",\"rotate\":\"NONE\"}");

        double startX = wallTester.getX();
        double startY = wallTester.getY();

        for (int t = 1; t <= 30; t++) {
            loop.tick(1.0 / 30.0);
        }

        System.out.printf("Xe trước khi đi: (%.1f, %.1f) | Sau 30 tick lao vào tường: (%.1f, %.1f)%n",
                startX, startY, wallTester.getX(), wallTester.getY());
        System.out.println("(Nếu bị chặn đúng, tọa độ sẽ dừng lại ở rìa tường, KHÔNG vượt qua tọa độ âm/ra ngoài map)");

        // ================== TIÊU CHÍ 3: Snapshot broadcast định kỳ ==================
        System.out.println("\n=== TIÊU CHÍ 3: Snapshot broadcast định kỳ ===");
        System.out.println("(Xem các dòng '[CLIENT NHẬN SNAPSHOT]' phía trên — phải xuất hiện đều mỗi 10 tick, không bị gián đoạn)");

        GameSnapshotDTO finalSnapshot = loop.buildSnapshot();
        System.out.println("\nSnapshot cuối cùng (mẫu JSON thật sẽ gửi qua mạng):");
        System.out.println(new com.google.gson.Gson().toJson(finalSnapshot));
    }
}