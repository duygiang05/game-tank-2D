package com.tank2d.server.demo;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.input.PlayerInputHandler;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.model.TankEntity;

/**
 * DEMO NGHIỆM THU — TASK 1: Xử lý Input điều khiển & Di chuyển đạn.
 * Không cần map/collision — chỉ kiểm chứng đúng công thức di chuyển + bắn đạn.
 */
public class Task1_InputAndMovementDemo {

    public static void main(String[] args) throws InterruptedException {
        GameLoop loop = new GameLoop(30);
        PlayerInputHandler inputHandler = new PlayerInputHandler();

        double tankSpeed = ConfigLoader.getTankSpeedPerSecond();
        TankEntity tank = new TankEntity(1, 100, 100, 0, tankSpeed, 90.0);
        tank.initHp(ConfigLoader.getMaxHp());
        loop.addTank(tank);

        System.out.println("=== TIÊU CHÍ 1: Xe di chuyển & bẻ lái tự do (PLAYER_INPUT) ===");
        inputHandler.handlePlayerInput(tank, "{\"move\":\"FORWARD\",\"rotate\":\"NONE\"}");

        for (int t = 1; t <= 40; t++) {
            if (t == 20) {
                System.out.println("--- Gửi PLAYER_INPUT mới: vừa chạy vừa rẽ trái ---");
                inputHandler.handlePlayerInput(tank, "{\"move\":\"FORWARD\",\"rotate\":\"LEFT\"}");
            }
            loop.tick(1.0 / 30.0);
            System.out.printf("Tick %2d: x=%.2f, y=%.2f, angle=%.2f%n",
                    t, tank.getX(), tank.getY(), tank.getAngle());
            Thread.sleep(20);
        }

        System.out.println("\n=== TIÊU CHÍ 2: Sinh đạn bay thẳng đúng góc nòng pháo (PLAYER_SHOOT_REQ) ===");
        double angleAtShotTime = tank.getAngle();
        boolean fired = inputHandler.handleShootRequest(loop, tank);
        System.out.printf("Bắn lúc angle=%.2f -> kết quả: %s%n", angleAtShotTime, fired ? "THÀNH CÔNG" : "BỊ CHẶN cooldown");

        BulletEntity bullet = loop.getBullets().values().iterator().next();
        double expectedRatio = Math.tan(Math.toRadians(angleAtShotTime)); // vy/vx lý thuyết theo góc bắn
        double actualRatio = bullet.getVy() / bullet.getVx();
        System.out.printf("Vận tốc đạn: vx=%.2f, vy=%.2f | tỉ lệ vy/vx thực tế=%.4f vs lý thuyết tan(angle)=%.4f%n",
                bullet.getVx(), bullet.getVy(), actualRatio, expectedRatio);

        System.out.println("\nTheo dõi đạn bay thẳng qua 10 tick tiếp theo:");
        for (int t = 1; t <= 10; t++) {
            loop.tick(1.0 / 30.0);
            System.out.printf("Tick %2d: bullet x=%.2f, y=%.2f%n", t, bullet.getX(), bullet.getY());
            Thread.sleep(20);
        }
    }
}