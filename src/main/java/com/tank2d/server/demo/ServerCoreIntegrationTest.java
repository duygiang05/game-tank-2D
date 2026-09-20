package com.tank2d.server.demo;

import com.tank2d.server.dao.MatchDAO;
import com.tank2d.server.dao.UserDAO;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.game.GameStateManager;
import com.tank2d.server.game.event.CombatEvent;
import com.tank2d.server.model.TankEntity;

public class ServerCoreIntegrationTest {

    public static void main(String[] args) {
        System.out.println("========== BẮT ĐẦU KIỂM THỬ TÍCH HỢP TASK 1 & TASK 2 ==========\n");

        UserDAO userDAO = new UserDAO();
        MatchDAO matchDAO = new MatchDAO();
        GameLoop gameLoop = new GameLoop(30);

        // Thiết lập thời gian trận đấu ngắn (5 giây) để test tự động kích hoạt Game Over
        double testMatchDurationSeconds = 5.0;
        GameStateManager stateManager = new GameStateManager(gameLoop, userDAO,matchDAO, testMatchDurationSeconds);
        gameLoop.setStateManager(stateManager);

        // 1. Tạo 2 xe đại diện cho User ID 1 và User ID 2 (có sẵn trong MySQL của bạn)
        TankEntity tank1 = new TankEntity(1, 100, 100, 0, 100, 90);
        tank1.setHp(3);
        tank1.setAlive(true);

        TankEntity tank2 = new TankEntity(2, 600, 600, 180, 100, 90);
        tank2.setHp(3);
        tank2.setAlive(true);

        gameLoop.addTank(tank1);
        gameLoop.addTank(tank2);

        // Đăng ký người chơi vào GameStateManager (dos = null vì test nội bộ không cần socket)
        stateManager.registerPlayer(1, 1, null); // tankId=1, userId=1
        stateManager.registerPlayer(2, 2, null); // tankId=2, userId=2

        // ==========================================
        // KỊCH BẢN 1: Tank 1 bắn trúng Tank 2 (Trừ HP, tăng hit, cộng điểm)
        // ==========================================
        System.out.println("--- [TEST 1] Tank 1 bắn trúng Tank 2 lần 1 (Sát thương 1 HP) ---");
        CombatEvent hitEvent1 = new CombatEvent(101, 1, 2, 1, CombatEvent.EventType.BULLET_HIT_TANK);
        stateManager.onCombatEvent(hitEvent1);

        assert tank2.getHp() == 2 : "LỖI: Tank 2 chưa bị trừ máu! HP hiện tại: " + tank2.getHp();
        assert tank2.isAlive() : "LỖI: Tank 2 bị set chết sai thời điểm!";
        System.out.println("PASSED: Tank 2 còn " + tank2.getHp() + " HP.");

        // ==========================================
        // KỊCH BẢN 2: Tank 1 bắn hạ Tank 2 (Tank 2 chết, chờ hồi sinh)
        // ==========================================
        System.out.println("\n--- [TEST 2] Tank 1 bắn thêm 2 phát tiêu diệt Tank 2 ---");
        CombatEvent hitEvent2 = new CombatEvent(102, 1, 2, 1, CombatEvent.EventType.BULLET_HIT_TANK);
        CombatEvent hitEvent3 = new CombatEvent(103, 1, 2, 1, CombatEvent.EventType.BULLET_HIT_TANK);
        stateManager.onCombatEvent(hitEvent2);
        stateManager.onCombatEvent(hitEvent3);

        assert tank2.getHp() == 0 : "LỖI: Tank 2 đáng lẽ phải hết máu! HP: " + tank2.getHp();
        assert !tank2.isAlive() : "LỖI: Tank 2 hết máu nhưng vẫn còn cờ alive = true!";
        System.out.println("PASSED: Tank 2 đã bị tiêu diệt (alive = false).");

        // ==========================================
        // KỊCH BẢN 3: Đếm ngược hồi sinh (Respawn Timer)
        // ==========================================
        System.out.println("\n--- [TEST 3] Kiểm tra đếm ngược hồi sinh xe (3 giây) ---");
        // Giả lập GameLoop chạy 2 giây: Tank 2 vẫn phải chết
        stateManager.update(2.0);
        assert !tank2.isAlive() : "LỖI: Tank 2 hồi sinh quá sớm (chưa đủ 3 giây)!";

        // Chạy thêm 1.1 giây (tổng > 3s): Tank 2 phải hồi sinh đầy máu
        stateManager.update(1.1);
        assert tank2.isAlive() : "LỖI: Hết 3 giây mà Tank 2 chưa hồi sinh!";
        assert tank2.getHp() == 3 : "LỖI: Tank 2 hồi sinh nhưng không đầy máu! HP: " + tank2.getHp();
        System.out.println("PASSED: Tank 2 đã hồi sinh an toàn với đầy máu (" + tank2.getHp() + " HP).");

        // ==========================================
        // KỊCH BẢN 4: Hết thời gian trận đấu (TIMEOUT -> Lưu CSDL MySQL)
        // ==========================================
        System.out.println("\n--- [TEST 4] Trận đấu kết thúc (TIMEOUT) & Lưu MySQL ---");
        // Đã trôi qua 3.1s, giờ cho chạy thêm 2.5s để vượt quá 5.0s (kích hoạt TIMEOUT)
        stateManager.update(2.5);

        assert stateManager.isGameOver() : "LỖI: Quá 5 giây nhưng cờ isGameOver vẫn là false!";
        System.out.println("PASSED: Game Over đã được kích hoạt thành công.");

        System.out.println("\n========================================================");
        System.out.println("TẤT CẢ LOGIC SERVER CORE HOẠT ĐỘNG HOÀN HẢO!");
        System.out.println("Hãy mở phpMyAdmin kiểm tra bảng 'user_stats' xem User 1 và User 2 đã được cộng dồn điểm, kills, hits chưa!");
        System.out.println("========================================================");
    }
}