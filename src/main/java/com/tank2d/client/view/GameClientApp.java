package com.tank2d.client.view;

import com.google.gson.Gson;
import com.tank2d.client.controller.InterpolationEngine;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameClientApp extends Application {

    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 600;
    public static final double TANK_SIZE = 36.0;
    private static final int MAX_HP = 100;

    private GraphicsContext gc;
    private ClientSocket clientSocket;
    private final Gson gson = new Gson();

    // Dữ liệu Snapshot từ Server & Dữ liệu Render LERP
    private final Map<Integer, TankSnapshotDTO> targetTanks = new ConcurrentHashMap<>();
    private final Map<Integer, TankRenderState> renderTanks = new ConcurrentHashMap<>();
    private final List<BulletSnapshotDTO> bullets = new CopyOnWriteArrayList<>();
    private final List<Explosion> explosions = new CopyOnWriteArrayList<>();

    // Lắng nghe phím
    private final Set<KeyCode> activeKeys = new HashSet<>();
    private boolean spacePressed = false;

    // Lớp nội bộ lưu trạng thái xe đang được LERP
    private static class TankRenderState {
        int id;
        double x, y, angle;
        int hp;
        boolean isAlive;

        TankRenderState(TankSnapshotDTO dto) {
            this.id = dto.getId();
            this.x = dto.getX();
            this.y = dto.getY();
            this.angle = dto.getAngle();
            this.hp = dto.getHp();
            this.isAlive = dto.isAlive();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT);

        // --- TASK 7: BÀN PHÍM ---
        scene.setOnKeyPressed(e -> {
            activeKeys.add(e.getCode());
            if (e.getCode() == KeyCode.SPACE && !spacePressed) {
                spacePressed = true;
                sendShootRequest();
            }
        });

        scene.setOnKeyReleased(e -> {
            activeKeys.remove(e.getCode());
            if (e.getCode() == KeyCode.SPACE) {
                spacePressed = false;
            }
        });

        // Loop 60 FPS
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                sendInputToServer();
                updateInterpolation();
                updateEffects();
                render();
            }
        }.start();

        primaryStage.setTitle("Tank 2D Online - Sprint 2 (Socket Realtime)");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(e -> {
            if (clientSocket != null) clientSocket.close();
        });
        primaryStage.show();

        // Khởi tạo luồng mạng kết nối Server
        initNetwork();
    }

    private void initNetwork() {
        clientSocket = new ClientSocket();
        new Thread(() -> {
            try {
                clientSocket.connect();
                while (clientSocket.isConnected()) {
                    Packet packet = clientSocket.receivePacket();
                    if (packet != null) {
                        handleIncomingPacket(packet);
                    }
                }
            } catch (IOException e) {
                System.err.println("[Network] Mất kết nối tới Server: " + e.getMessage());
            }
        }).start();
    }

    private void handleIncomingPacket(Packet packet) {
        if (packet.getType() == null || packet.getData() == null) return;

        if (packet.getType() == PacketType.GAME_SNAPSHOT) {
            GameSnapshotDTO snapshot = gson.fromJson(packet.getData(), GameSnapshotDTO.class);
            if (snapshot != null) {
                onSnapshotReceived(snapshot);
            }
        } else if (packet.getType() == PacketType.GAME_EVENT_EFFECT) {
            double[] coords = gson.fromJson(packet.getData(), double[].class);
            if (coords != null && coords.length >= 2) {
                Platform.runLater(() -> explosions.add(new Explosion(coords[0], coords[1])));
            }
        }
    }

    private void sendInputToServer() {
        if (activeKeys.isEmpty() || clientSocket == null || !clientSocket.isConnected()) return;

        boolean up = activeKeys.contains(KeyCode.W) || activeKeys.contains(KeyCode.UP);
        boolean down = activeKeys.contains(KeyCode.S) || activeKeys.contains(KeyCode.DOWN);
        boolean left = activeKeys.contains(KeyCode.A) || activeKeys.contains(KeyCode.LEFT);
        boolean right = activeKeys.contains(KeyCode.D) || activeKeys.contains(KeyCode.RIGHT);

        Map<String, Boolean> inputData = new HashMap<>();
        inputData.put("up", up);
        inputData.put("down", down);
        inputData.put("left", left);
        inputData.put("right", right);

        try {
            clientSocket.sendPacket(new Packet(PacketType.PLAYER_INPUT, gson.toJson(inputData)));
        } catch (IOException e) {
            System.err.println("[Input] Lỗi gửi PLAYER_INPUT: " + e.getMessage());
        }
    }

    private void sendShootRequest() {
        if (clientSocket == null || !clientSocket.isConnected()) return;
        try {
            clientSocket.sendPacket(new Packet(PacketType.PLAYER_SHOOT_REQ, "{}"));
        } catch (IOException e) {
            System.err.println("[Input] Lỗi gửi PLAYER_SHOOT_REQ: " + e.getMessage());
        }
    }

    // --- TASK 8: NỘI SUY LERP ---
    private void updateInterpolation() {
        double lerpAlpha = 0.25;
        for (Map.Entry<Integer, TankSnapshotDTO> entry : targetTanks.entrySet()) {
            int id = entry.getKey();
            TankSnapshotDTO target = entry.getValue();

            if (!target.isAlive()) {
                renderTanks.remove(id);
                continue;
            }

            TankRenderState renderState = renderTanks.computeIfAbsent(id, k -> new TankRenderState(target));

            renderState.x = InterpolationEngine.lerp(renderState.x, target.getX(), lerpAlpha);
            renderState.y = InterpolationEngine.lerp(renderState.y, target.getY(), lerpAlpha);
            renderState.angle = InterpolationEngine.lerpAngle(renderState.angle, target.getAngle(), lerpAlpha);
            renderState.hp = target.getHp();
            renderState.isAlive = target.isAlive();
        }
    }

    private void updateEffects() {
        explosions.forEach(Explosion::update);
        explosions.removeIf(e -> !e.isActive());
    }

    private void render() {
        // Clear màn hình
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // 1. Vẽ Đạn
        gc.setFill(Color.YELLOW);
        for (BulletSnapshotDTO bullet : bullets) {
            gc.fillOval(bullet.getX() - 3, bullet.getY() - 3, 6, 6);
        }

        // 2. Vẽ Xe tăng & Thanh máu (Áp dụng đúng Sprite Design + Quy ước rotate 90 của bạn)
        for (TankRenderState tank : renderTanks.values()) {
            if (tank.isAlive) {
                drawTank(tank);
                drawHealthBar(tank);
            }
        }

        // 3. Vẽ Hiệu ứng nổ
        for (Explosion exp : explosions) {
            exp.render(gc);
        }

        // 4. Bảng điểm mini
        drawMiniScoreboard();
    }

    private void drawTank(TankRenderState tank) {
        gc.save();
        gc.translate(tank.x, tank.y);
        
        // Dùng đúng góc xoay lệch 90 độ chuẩn từ Sprint 1
        gc.rotate(tank.angle + 90.0);

        double halfSize = TANK_SIZE / 2.0;

        // Thân xe
        gc.setFill(Color.FORESTGREEN);
        gc.fillRect(-halfSize, -halfSize, TANK_SIZE, TANK_SIZE);

        // Xích xe
        double treadWidth = TANK_SIZE * 0.14;
        gc.setFill(Color.DARKSLATEGRAY);
        gc.fillRect(-halfSize - treadWidth, -halfSize, treadWidth, TANK_SIZE);
        gc.fillRect(halfSize, -halfSize, treadWidth, TANK_SIZE);

        // Nòng súng
        double cannonWidth = TANK_SIZE * 0.16;
        double cannonLength = TANK_SIZE * 0.55;
        gc.setFill(Color.ORANGE);
        gc.fillRect(-cannonWidth / 2, -halfSize - cannonLength + (TANK_SIZE * 0.2), cannonWidth, cannonLength);

        // Tháp pháo
        double turretSize = TANK_SIZE * 0.5;
        gc.setFill(Color.LIMEGREEN);
        gc.fillOval(-turretSize / 2, -turretSize / 2, turretSize, turretSize);

        gc.restore();
    }

    private void drawHealthBar(TankRenderState tank) {
        double barW = 36;
        double barH = 5;
        double x = tank.x - barW / 2;
        double y = tank.y - 28;

        double hpRatio = Math.max(0, (double) tank.hp / MAX_HP);

        gc.setFill(Color.RED);
        gc.fillRect(x, y, barW, barH);

        gc.setFill(Color.LIME);
        gc.fillRect(x, y, barW * hpRatio, barH);

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeRect(x, y, barW, barH);
    }

    private void drawMiniScoreboard() {
        int sbWidth = 160;
        int sbHeight = 25 + (renderTanks.size() * 18);
        int startX = CANVAS_WIDTH - sbWidth - 10;
        int startY = 10;

        gc.setFill(Color.rgb(0, 0, 0, 0.65));
        gc.fillRect(startX, startY, sbWidth, sbHeight);
        gc.setStroke(Color.GRAY);
        gc.strokeRect(startX, startY, sbWidth, sbHeight);

        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        gc.setFill(Color.GOLD);
        gc.fillText("TANKS ALIVE", startX + 10, startY + 18);

        int lineY = startY + 34;
        for (TankRenderState tank : renderTanks.values()) {
            gc.setFill(Color.WHITE);
            gc.fillText("Tank #" + tank.id + " | HP: " + tank.hp, startX + 10, lineY);
            lineY += 18;
        }
    }

    public void onSnapshotReceived(GameSnapshotDTO snapshot) {
        targetTanks.clear();
        if (snapshot.getTanks() != null) {
            for (TankSnapshotDTO t : snapshot.getTanks()) {
                targetTanks.put(t.getId(), t);
            }
        }
        this.bullets.clear();
        if (snapshot.getBullets() != null) {
            this.bullets.addAll(snapshot.getBullets());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}