package com.tank2d.client.view;

import com.google.gson.Gson;
import com.tank2d.common.dto.game.BulletSnapshotDTO;
import com.tank2d.common.dto.game.GameSnapshotDTO;
import com.tank2d.common.dto.game.TankSnapshotDTO;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameCanvasApp extends Application {

    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 600;
    public static final double TANK_SIZE = 36.0;
    private static final int MAX_HP = 100;

    private Canvas canvas;
    private GraphicsContext gc;
    private final Gson gson = new Gson();

    // FPS Counter
    private long lastFpsCheck = System.nanoTime();
    private int frameCounter = 0;
    private int currentFps = 0;

    // Quản lý Input phím bấm
    private final Set<KeyCode> activeKeys = new HashSet<>();

    // Danh sách Snapshot
    private final List<TankSnapshotDTO> tanks = new CopyOnWriteArrayList<>();
    private final List<BulletSnapshotDTO> bullets = new CopyOnWriteArrayList<>();
    private int bulletIdCounter = 1;

    // Tọa độ và trạng thái xe người chơi (Tank #1)
    private double playerX = 250.0;
    private double playerY = 300.0;
    private double playerAngle = 0.0; // 0 độ: quay sang phải, 90 độ: quay xuống dưới, 180 độ: sang trái, 270 độ: lên trên
    private int playerHp = 100;

    // Tọa độ xe đối thủ (Tank #2)
    private double enemyX = 550.0;
    private double enemyY = 300.0;
    private double enemyAngle = 180.0;
    private int enemyHp = 100;

    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT);

        // LẮNG NGHE SỰ KIỆN BÀN PHÍM
        scene.setOnKeyPressed(e -> {
            activeKeys.add(e.getCode());
            if (e.getCode() == KeyCode.SPACE) {
                spawnBullet();
            }
        });

        scene.setOnKeyReleased(e -> activeKeys.remove(e.getCode()));

        primaryStage.setTitle("Tank 2D - Canvas Render Engine (Task 7)");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        startRenderLoop();
    }

    private void startRenderLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateFpsCounter(now);

                // 1. Tính toán chuyển động xe và đạn
                updatePhysics();

                // 2. Xóa màn hình
                gc.setFill(Color.rgb(22, 24, 29));
                gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

                // 3. Vẽ đạn
                renderBullets();

                // 4. Vẽ xe tăng & thanh máu (HP Bar)
                renderTanks();

                // 5. Vẽ bảng điểm thu nhỏ (Mini-Scoreboard)
                renderMiniScoreboard();

                // 6. Hiển thị thông số FPS và phím bấm
                renderOverlayInfo();
            }
        }.start();
    }

    private void updatePhysics() {
        // --- ĐIỀU KHIỂN XE TĂNG ---
        // A/D: Xoay thân & nòng xe
        double rotateSpeed = 2;
        if (activeKeys.contains(KeyCode.A) || activeKeys.contains(KeyCode.LEFT)) {
            playerAngle = (playerAngle - rotateSpeed + 360.0) % 360.0;
        }
        if (activeKeys.contains(KeyCode.D) || activeKeys.contains(KeyCode.RIGHT)) {
            playerAngle = (playerAngle + rotateSpeed) % 360.0;
        }

        // W/S: Tiến / Lùi theo hướng góc xe đang quay
        double moveSpeed = 1;
        double rad = Math.toRadians(playerAngle);
        if (activeKeys.contains(KeyCode.W) || activeKeys.contains(KeyCode.UP)) {
            playerX += Math.cos(rad) * moveSpeed;
            playerY += Math.sin(rad) * moveSpeed;
        }
        if (activeKeys.contains(KeyCode.S) || activeKeys.contains(KeyCode.DOWN)) {
            playerX -= Math.cos(rad) * moveSpeed;
            playerY -= Math.sin(rad) * moveSpeed;
        }

        // Giữ xe trong màn hình
        playerX = Math.max(30, Math.min(CANVAS_WIDTH - 30, playerX));
        playerY = Math.max(30, Math.min(CANVAS_HEIGHT - 30, playerY));

        // --- CẬP NHẬT TỌA ĐỘ ĐẠN BAY (CẢ TRỤC X VÀ TRỤC Y) ---
        for (BulletSnapshotDTO b : bullets) {
            b.setX(b.getX() + b.getVx());
            // Cập nhật trục Y thông qua phản xạ hoặc trực tiếp theo vy
            setBulletY(b, b.getY() + b.getVy());

            // Kiểm tra va chạm đơn giản với xe đối thủ (Tank #2) để demo tụt máu
            double dist = Math.hypot(b.getX() - enemyX, b.getY() - enemyY);
            if (dist < TANK_SIZE / 1.5) {
                enemyHp = Math.max(0, enemyHp - 10);
                b.setX(-999); // Đánh dấu xóa đạn
            }
        }

        // Xóa đạn ra ngoài map hoặc đã trúng mục tiêu
        bullets.removeIf(b -> b.getX() < 0 || b.getX() > CANVAS_WIDTH || b.getY() < 0 || b.getY() > CANVAS_HEIGHT);

        // Cập nhật Snapshot xe
        tanks.clear();
        tanks.add(new TankSnapshotDTO(1, playerX, playerY, playerAngle, playerHp, true));
        tanks.add(new TankSnapshotDTO(2, enemyX, enemyY, enemyAngle, enemyHp, true));
    }

    // Hàm hỗ trợ cập nhật Y nếu DTO thiếu hàm setY()
    private void setBulletY(BulletSnapshotDTO bullet, double newY) {
        try {
            java.lang.reflect.Field fieldY = BulletSnapshotDTO.class.getDeclaredField("y");
            fieldY.setAccessible(true);
            fieldY.setDouble(bullet, newY);
        } catch (Exception ignored) {
            // Trường hợp file BulletSnapshotDTO của bạn đã có hàm setY(), có thể gọi trực tiếp bullet.setY(newY)
        }
    }

    private void spawnBullet() {
        double bulletSpeed = 8.5;
        double rad = Math.toRadians(playerAngle);

        // Vận tốc đạn phân rã theo góc quay nòng pháo
        double vx = Math.cos(rad) * bulletSpeed;
        double vy = Math.sin(rad) * bulletSpeed;

        // Vị trí xuất phát từ đầu nòng pháo (cách tâm xe 24px theo hướng quay)
        double spawnOffset = 24.0;
        double startX = playerX + Math.cos(rad) * spawnOffset;
        double startY = playerY + Math.sin(rad) * spawnOffset;

        bullets.add(new BulletSnapshotDTO(bulletIdCounter++, 1, startX, startY, vx, vy));
    }

    private void renderBullets() {
        gc.setFill(Color.YELLOW);
        for (BulletSnapshotDTO bullet : bullets) {
            gc.fillOval(bullet.getX() - 3.5, bullet.getY() - 3.5, 7.0, 7.0);
        }
    }

    private void renderTanks() {
        for (TankSnapshotDTO tank : tanks) {
            if (tank.isAlive()) {
                drawTankSprite(tank);
                drawHealthBar(tank);
            }
        }
    }

    private void drawTankSprite(TankSnapshotDTO tank) {
        gc.save();
        gc.translate(tank.getX(), tank.getY());

        // Lệch 90 độ chuẩn quy ước sprite: 0 độ = sang phải
        gc.rotate(tank.getAngle() + 90.0);

        double halfSize = TANK_SIZE / 2.0;

        // Thân xe
        gc.setFill(tank.getId() == 1 ? Color.FORESTGREEN : Color.INDIANRED);
        gc.fillRect(-halfSize, -halfSize, TANK_SIZE, TANK_SIZE);

        // Xích xe hai bên
        double treadWidth = TANK_SIZE * 0.14;
        gc.setFill(Color.DARKSLATEGRAY);
        gc.fillRect(-halfSize - treadWidth, -halfSize, treadWidth, TANK_SIZE);
        gc.fillRect(halfSize, -halfSize, treadWidth, TANK_SIZE);

        // Nòng pháo
        double cannonWidth = TANK_SIZE * 0.16;
        double cannonLength = TANK_SIZE * 0.55;
        gc.setFill(Color.ORANGE);
        gc.fillRect(-cannonWidth / 2, -halfSize - cannonLength + (TANK_SIZE * 0.2), cannonWidth, cannonLength);

        // Tháp pháo tròn
        double turretSize = TANK_SIZE * 0.5;
        gc.setFill(tank.getId() == 1 ? Color.LIMEGREEN : Color.CRIMSON);
        gc.fillOval(-turretSize / 2, -turretSize / 2, turretSize, turretSize);

        gc.restore();
    }

    private void drawHealthBar(TankSnapshotDTO tank) {
        double barW = 38.0;
        double barH = 5.0;
        double x = tank.getX() - barW / 2.0;
        double y = tank.getY() - 28.0;

        double hpRatio = Math.max(0, (double) tank.getHp() / MAX_HP);

        // Nền máu màu đỏ
        gc.setFill(Color.RED);
        gc.fillRect(x, y, barW, barH);

        // Lượng máu còn lại màu xanh lá
        gc.setFill(Color.LIME);
        gc.fillRect(x, y, barW * hpRatio, barH);

        // Viền thanh máu
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.0);
        gc.strokeRect(x, y, barW, barH);
    }

    private void renderMiniScoreboard() {
        int sbWidth = 175;
        int sbHeight = 30 + (tanks.size() * 20);
        int startX = CANVAS_WIDTH - sbWidth - 15;
        int startY = 15;

        // Khung nền xám tối
        gc.setFill(Color.rgb(0, 0, 0, 0.75));
        gc.fillRect(startX, startY, sbWidth, sbHeight);
        gc.setStroke(Color.DARKGRAY);
        gc.setLineWidth(1.5);
        gc.strokeRect(startX, startY, sbWidth, sbHeight);

        // Tiêu đề
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        gc.setFill(Color.GOLD);
        gc.fillText("BẢNG ĐIỂM CHIẾN ĐẤU", startX + 12, startY + 20);

        // Danh sách hiển thị máu
        int lineY = startY + 38;
        for (TankSnapshotDTO tank : tanks) {
            gc.setFill(tank.getId() == 1 ? Color.LIGHTGREEN : Color.LIGHTCORAL);
            gc.fillText(String.format("Xe #%d | HP: %3d/100", tank.getId(), tank.getHp()), startX + 12, lineY);
            lineY += 18;
        }
    }

    private void renderOverlayInfo() {
        // FPS
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        gc.setFill(Color.YELLOW);
        gc.fillText("FPS: " + currentFps, 15, 25);

        // Hướng dẫn
        gc.setFont(Font.font("Arial", 12));
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText("A/D: Xoay xe | W/S: Tiến/Lùi | SPACE: Bắn đạn theo góc nòng", 15, 580);
    }

    private void updateFpsCounter(long now) {
        frameCounter++;
        if (now - lastFpsCheck >= 1_000_000_000L) {
            currentFps = frameCounter;
            frameCounter = 0;
            lastFpsCheck = now;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}