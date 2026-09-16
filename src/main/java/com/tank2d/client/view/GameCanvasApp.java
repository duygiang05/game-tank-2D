package com.tank2d.client.view;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.dto.game.BulletSnapshotDTO;
import com.tank2d.common.dto.game.GameSnapshotDTO;
import com.tank2d.common.dto.game.TankSnapshotDTO;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameCanvasApp extends Application {

    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 600;
    public static final double TANK_SIZE = 36.0;
    private static final int MAX_HP = 100;
    private static final double LERP_FACTOR = 0.25; // Nội suy mượt ở 60 FPS

    private Canvas canvas;
    private GraphicsContext gc;
    private final Gson gson = new Gson();

    private ClientSocket clientSocket;

    // FPS Counter
    private long lastFpsCheck = System.nanoTime();
    private int frameCounter = 0;
    private int currentFps = 0;

    // Quản lý bàn phím
    private final Set<KeyCode> activeKeys = new HashSet<>();
    private boolean spacePressed = false;

    // Danh sách Entity Game & LERP Targets
    private final List<TankSnapshotDTO> tanks = new CopyOnWriteArrayList<>();
    private final Map<Integer, TankSnapshotDTO> targetTanks = new HashMap<>();
    private final List<BulletSnapshotDTO> bullets = new CopyOnWriteArrayList<>();
    
    // Hiệu ứng nổ (Task 8)
    private final List<Explosion> explosions = new CopyOnWriteArrayList<>();

    public Scene createGameScene() {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT);

        initDefaultTanks();

        scene.setOnKeyPressed(e -> {
            activeKeys.add(e.getCode());
            if (e.getCode() == KeyCode.SPACE && !spacePressed) {
                spacePressed = true;
                spawnLocalBullet();
                sendShootRequest();
            }
        });

        scene.setOnKeyReleased(e -> {
            activeKeys.remove(e.getCode());
            if (e.getCode() == KeyCode.SPACE) {
                spacePressed = false;
            }
        });

        canvas.setFocusTraversable(true);
        Platform.runLater(() -> canvas.requestFocus());

        initNetworkFromSession();
        startRenderLoop();

        return scene;
    }

    @Override
    public void start(Stage primaryStage) {
        Scene scene = createGameScene();

        primaryStage.setTitle("Tank 2D Online - Game Screen");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        canvas.requestFocus();
    }

    private void initDefaultTanks() {
        tanks.clear();
        tanks.add(new TankSnapshotDTO(1, 250.0, 300.0, 0.0, 100, true));
        tanks.add(new TankSnapshotDTO(2, 550.0, 300.0, 180.0, 100, true));
    }

    private int localBulletId = 1000;

    private void spawnLocalBullet() {
        if (tanks.isEmpty()) return;

        TankSnapshotDTO player = tanks.get(0);
        double rad = Math.toRadians(player.getAngle());
        double speed = 7.0;

        double startX = player.getX() + Math.cos(rad) * (TANK_SIZE / 2.0 + 5);
        double startY = player.getY() + Math.sin(rad) * (TANK_SIZE / 2.0 + 5);

        double vx = Math.cos(rad) * speed;
        double vy = Math.sin(rad) * speed;

        bullets.add(new BulletSnapshotDTO(localBulletId++, player.getId(), startX, startY, vx, vy));
    }

    // TASK 8: VẬT LÝ ĐẠN BAY + KIỂM TRA VA CHẠM CỤC BỘ (CLIENT-SIDE COLLISION)
    private void updateLocalBulletPhysics() {
        for (BulletSnapshotDTO b : bullets) {
            b.setX(b.getX() + b.getVx());
            setBulletY(b, b.getY() + b.getVy());

            // Xử lý va chạm đạn với các xe khác
            for (TankSnapshotDTO tank : tanks) {
                if (tank.isAlive() && tank.getId() != b.getOwnerId()) {
                    double dx = b.getX() - tank.getX();
                    double dy = b.getY() - tank.getY();
                    double distance = Math.sqrt(dx * dx + dy * dy);

                    // Trúng mục tiêu
                    if (distance <= (TANK_SIZE / 2.0)) {
                        // 1. Tạo hiệu ứng nổ
                        explosions.add(new Explosion(b.getX(), b.getY()));

                        // 2. Trừ HP cục bộ (chờ Server override lại sau)
                        int newHp = Math.max(0, tank.getHp() - 20);
                        updateTankHpAndAlive(tank, newHp);

                        // 3. Xóa viên đạn
                        b.setX(-999);
                        break;
                    }
                }
            }
        }

        bullets.removeIf(b -> b.getX() < 0 || b.getX() > CANVAS_WIDTH || b.getY() < 0 || b.getY() > CANVAS_HEIGHT);
    }

    private void updateTankHpAndAlive(TankSnapshotDTO tank, int newHp) {
        try {
            java.lang.reflect.Field hpField = TankSnapshotDTO.class.getDeclaredField("hp");
            hpField.setAccessible(true);
            hpField.setInt(tank, newHp);

            if (newHp == 0) {
                java.lang.reflect.Field aliveField = TankSnapshotDTO.class.getDeclaredField("isAlive");
                aliveField.setAccessible(true);
                aliveField.setBoolean(tank, false);
            }
        } catch (Exception ignored) {}
    }

    private void setBulletY(BulletSnapshotDTO bullet, double newY) {
        try {
            java.lang.reflect.Field fieldY = BulletSnapshotDTO.class.getDeclaredField("y");
            fieldY.setAccessible(true);
            fieldY.setDouble(bullet, newY);
        } catch (Exception ignored) {}
    }

    private void initNetworkFromSession() {
        this.clientSocket = ClientSession.getInstance().getClientSocket();

        if (this.clientSocket == null || !this.clientSocket.isConnected()) {
            System.out.println("[GameCanvasApp] Tự động tạo và kết nối Socket tới localhost:8888...");
            try {
                this.clientSocket = new ClientSocket("localhost", 8888);
                this.clientSocket.connect();
            } catch (Exception e) {
                System.err.println("[GameCanvasApp] Kết nối Server thất bại: " + e.getMessage());
            }
        }

        if (this.clientSocket != null && this.clientSocket.isConnected()) {
            System.out.println("[GameCanvasApp] Socket kết nối thành công! Đang lắng nghe Snapshot & Event...");

            Thread networkReadThread = new Thread(() -> {
                while (clientSocket != null && clientSocket.isConnected()) {
                    try {
                        Packet packet = clientSocket.receivePacket();
                        if (packet != null) {
                            if (packet.getType() == PacketType.GAME_SNAPSHOT) {
                                handleGameSnapshot(packet.getData());
                            } else if (packet.getType() == PacketType.GAME_EVENT_EFFECT) {
                                handleGameEventEffect(packet.getData());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[GameCanvasApp] Lỗi đọc Packet: " + e.getMessage());
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {}
                    }
                }
            });
            networkReadThread.setDaemon(true);
            networkReadThread.start();
        }
    }

    private void handleGameSnapshot(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isEmpty()) return;

        try {
            GameSnapshotDTO snapshot = gson.fromJson(jsonPayload, GameSnapshotDTO.class);
            if (snapshot != null) {
                if (snapshot.getTanks() != null && !snapshot.getTanks().isEmpty()) {
                    for (TankSnapshotDTO incoming : snapshot.getTanks()) {
                        targetTanks.put(incoming.getId(), incoming);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[GameCanvasApp] Lỗi parse GameSnapshotDTO: " + e.getMessage());
        }
    }

    // TASK 8: NHẬN SỰ KIỆN NỔ TỪ SERVER
    private void handleGameEventEffect(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isEmpty()) return;

        try {
            JsonObject obj = gson.fromJson(jsonPayload, JsonObject.class);
            if (obj.has("x") && obj.has("y")) {
                double x = obj.get("x").getAsDouble();
                double y = obj.get("y").getAsDouble();
                Platform.runLater(() -> explosions.add(new Explosion(x, y)));
            }
        } catch (Exception e) {
            System.err.println("[GameCanvasApp] Lỗi parse GAME_EVENT_EFFECT: " + e.getMessage());
        }
    }

    private void sendInputToServer() {
        if (clientSocket == null || !clientSocket.isConnected()) return;

        boolean up = activeKeys.contains(KeyCode.W) || activeKeys.contains(KeyCode.UP);
        boolean down = activeKeys.contains(KeyCode.S) || activeKeys.contains(KeyCode.DOWN);
        boolean left = activeKeys.contains(KeyCode.A) || activeKeys.contains(KeyCode.LEFT);
        boolean right = activeKeys.contains(KeyCode.D) || activeKeys.contains(KeyCode.RIGHT);

        if (up || down || left || right) {
            Map<String, Boolean> inputMap = new HashMap<>();
            inputMap.put("up", up);
            inputMap.put("down", down);
            inputMap.put("left", left);
            inputMap.put("right", right);

            try {
                clientSocket.sendPacket(new Packet(PacketType.PLAYER_INPUT, gson.toJson(inputMap)));
            } catch (IOException e) {
                System.err.println("[GameCanvasApp] Lỗi gửi PLAYER_INPUT: " + e.getMessage());
            }
        }
    }

    private void sendShootRequest() {
        if (clientSocket == null || !clientSocket.isConnected()) return;

        try {
            clientSocket.sendPacket(new Packet(PacketType.PLAYER_SHOOT_REQ, "{}"));
        } catch (IOException e) {
            System.err.println("[GameCanvasApp] Lỗi gửi PLAYER_SHOOT_REQ: " + e.getMessage());
        }
    }

    private void startRenderLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateFpsCounter(now);

                sendInputToServer();
                updatePhysicsAndLerp();

                gc.setFill(Color.rgb(22, 24, 29));
                gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

                renderBullets();
                renderTanks();
                renderExplosions();
                renderMiniScoreboard();
                renderOverlayInfo();
            }
        }.start();
    }

    // TASK 8: NỘI SUY LERP & CẬP NHẬT TRẠNG THÁI
    private void updatePhysicsAndLerp() {
        // 1. LERP Vị trí & Góc xoay cho Tanks
        for (TankSnapshotDTO currentTank : tanks) {
            TankSnapshotDTO target = targetTanks.get(currentTank.getId());
            if (target != null) {
                double newX = currentTank.getX() + (target.getX() - currentTank.getX()) * LERP_FACTOR;
                double newY = currentTank.getY() + (target.getY() - currentTank.getY()) * LERP_FACTOR;

                double diffAngle = (target.getAngle() - currentTank.getAngle() + 540) % 360 - 180;
                double newAngle = (currentTank.getAngle() + diffAngle * LERP_FACTOR + 360) % 360;

                currentTank.setX(newX);
                currentTank.setY(newY);
                currentTank.setAngle(newAngle);
                updateTankHpAndAlive(currentTank, target.getHp());
            }
        }

        // 2. Dự đoán điều khiển bàn phím (Player Local Prediction)
        if (!tanks.isEmpty()) {
            TankSnapshotDTO playerTank = tanks.get(0);
            double x = playerTank.getX();
            double y = playerTank.getY();
            double angle = playerTank.getAngle();

            double rotateSpeed = 3.0;
            double moveSpeed = 2.5;

            if (activeKeys.contains(KeyCode.A) || activeKeys.contains(KeyCode.LEFT)) {
                angle = (angle - rotateSpeed + 360.0) % 360.0;
            }
            if (activeKeys.contains(KeyCode.D) || activeKeys.contains(KeyCode.RIGHT)) {
                angle = (angle + rotateSpeed) % 360.0;
            }

            double rad = Math.toRadians(angle);
            if (activeKeys.contains(KeyCode.W) || activeKeys.contains(KeyCode.UP)) {
                x += Math.cos(rad) * moveSpeed;
                y += Math.sin(rad) * moveSpeed;
            }
            if (activeKeys.contains(KeyCode.S) || activeKeys.contains(KeyCode.DOWN)) {
                x -= Math.cos(rad) * moveSpeed;
                y -= Math.sin(rad) * moveSpeed;
            }

            x = Math.max(30, Math.min(CANVAS_WIDTH - 30, x));
            y = Math.max(30, Math.min(CANVAS_HEIGHT - 30, y));

            playerTank.setX(x);
            playerTank.setY(y);
            playerTank.setAngle(angle);
        }

        // 3. Vật lý đạn + Va chạm nổ
        updateLocalBulletPhysics();

        // 4. Cập nhật animation nổ
        for (Explosion exp : explosions) {
            exp.update();
        }
        explosions.removeIf(exp -> !exp.isActive());
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

    private void renderExplosions() {
        for (Explosion exp : explosions) {
            exp.render(gc);
        }
    }

    private void drawTankSprite(TankSnapshotDTO tank) {
        gc.save();
        gc.translate(tank.getX(), tank.getY());
        gc.rotate(tank.getAngle() + 90.0);

        double halfSize = TANK_SIZE / 2.0;

        gc.setFill(tank.getId() == 1 ? Color.FORESTGREEN : Color.INDIANRED);
        gc.fillRect(-halfSize, -halfSize, TANK_SIZE, TANK_SIZE);

        double treadWidth = TANK_SIZE * 0.14;
        gc.setFill(Color.DARKSLATEGRAY);
        gc.fillRect(-halfSize - treadWidth, -halfSize, treadWidth, TANK_SIZE);
        gc.fillRect(halfSize, -halfSize, treadWidth, TANK_SIZE);

        double cannonWidth = TANK_SIZE * 0.16;
        double cannonLength = TANK_SIZE * 0.55;
        gc.setFill(Color.ORANGE);
        gc.fillRect(-cannonWidth / 2, -halfSize - cannonLength + (TANK_SIZE * 0.2), cannonWidth, cannonLength);

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

        gc.setFill(Color.RED);
        gc.fillRect(x, y, barW, barH);

        gc.setFill(Color.LIME);
        gc.fillRect(x, y, barW * hpRatio, barH);

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.0);
        gc.strokeRect(x, y, barW, barH);
    }

    private void renderMiniScoreboard() {
        if (tanks.isEmpty()) return;

        int sbWidth = 175;
        int sbHeight = 30 + (tanks.size() * 20);
        int startX = CANVAS_WIDTH - sbWidth - 15;
        int startY = 15;

        gc.setFill(Color.rgb(0, 0, 0, 0.75));
        gc.fillRect(startX, startY, sbWidth, sbHeight);
        gc.setStroke(Color.DARKGRAY);
        gc.setLineWidth(1.5);
        gc.strokeRect(startX, startY, sbWidth, sbHeight);

        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        gc.setFill(Color.GOLD);
        gc.fillText("BẢNG ĐIỂM CHIẾN ĐẤU", startX + 12, startY + 20);

        int lineY = startY + 38;
        for (TankSnapshotDTO tank : tanks) {
            gc.setFill(tank.getId() == 1 ? Color.LIGHTGREEN : Color.LIGHTCORAL);
            gc.fillText(String.format("Xe #%d | HP: %3d/100", tank.getId(), tank.getHp()), startX + 12, lineY);
            lineY += 18;
        }
    }

    private void renderOverlayInfo() {
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        gc.setFill(Color.YELLOW);
        gc.fillText("FPS: " + currentFps, 15, 25);

        gc.setFont(Font.font("Arial", 12));
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText("A/D: Xoay xe | W/S: Tiến/Lùi | SPACE: Bắn đạn", 15, 580);
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