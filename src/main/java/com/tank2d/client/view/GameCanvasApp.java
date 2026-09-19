package com.tank2d.client.view;

import com.google.gson.Gson;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.dto.game.BulletSnapshotDTO;
import com.tank2d.common.dto.game.GameEventEffectDTO;
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
import javafx.stage.Stage;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameCanvasApp extends Application {

    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 600;
    private static final double TANK_SIZE = 36.0;
    private static final double BULLET_SIZE = 8.0;
    private static final double LERP_FACTOR = 0.3;
    private static final double MAX_HP = 3.0;

    private Canvas canvas;
    private GraphicsContext gc;
    private final Gson gson = new Gson();
    private ClientSocket clientSocket;

    private final Set<KeyCode> activeKeys = new HashSet<>();
    private boolean spacePressed = false;

    // --- DỮ LIỆU ĐỒNG BỘ TỪ SERVER ---
    private final Map<Integer, TankSnapshotDTO> displayTanks = new ConcurrentHashMap<>();
    private final Map<Integer, TankSnapshotDTO> targetTanks = new ConcurrentHashMap<>();
    private final List<BulletSnapshotDTO> bullets = new CopyOnWriteArrayList<>();
    private final List<ExplosionEffect> explosions = new CopyOnWriteArrayList<>();

    // Lớp quản lý hiệu ứng nổ
    private static class ExplosionEffect {

        double x, y;
        int radius = 6;
        int maxRadius = 24;
        boolean finished = false;

        ExplosionEffect(double x, double y) {
            this.x = x;
            this.y = y;
        }

        void update() {
            radius += 3;
            if (radius > maxRadius) {
                finished = true;
            }
        }

        void render(GraphicsContext gc) {
            if (finished) {
                return;
            }

            gc.setFill(Color.ORANGE);
            gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);

            gc.setFill(Color.RED);
            gc.fillOval(
                    x - (radius / 2.0),
                    y - (radius / 2.0),
                    radius,
                    radius
            );

            gc.setFill(Color.YELLOW);
            gc.fillOval(
                    x - (radius / 4.0),
                    y - (radius / 4.0),
                    radius / 2.0,
                    radius / 2.0
            );
        }
    }

    public Scene createGameScene() {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT);

        scene.setOnKeyPressed(e -> {
            activeKeys.add(e.getCode());

            if (e.getCode() == KeyCode.SPACE && !spacePressed) {
                spacePressed = true;
                sendShootRequestToServer();
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

        initNetworkReceiver();
        startRenderLoop();

        return scene;
    }

    // =========================================================================
    // TASK 7: LOGIC NHẬN GÓI TIN TỪ SERVER
    // =========================================================================
    private void initNetworkReceiver() {
        this.clientSocket = ClientSession.getInstance().getClientSocket();

        // Đăng ký trực tiếp bộ xử lý vào luồng duy nhất của ClientSession
        ClientSession.getInstance().addPacketListener(this::processIncomingPacket);
    }

    private void processIncomingPacket(Packet packet) {
        if (packet.getType() == PacketType.GAME_SNAPSHOT) {
            GameSnapshotDTO snapshot = gson.fromJson(packet.getData(), GameSnapshotDTO.class);
            if (snapshot != null) {
                if (snapshot.getBullets() != null) {
                    this.bullets.clear();
                    this.bullets.addAll(snapshot.getBullets());
                }

                if (snapshot.getTanks() != null) {
                    for (TankSnapshotDTO incoming : snapshot.getTanks()) {
                        targetTanks.put(incoming.getId(), incoming);
                        displayTanks.putIfAbsent(incoming.getId(),
                                new TankSnapshotDTO(incoming.getId(), incoming.getX(), incoming.getY(),
                                        incoming.getAngle(), incoming.getHp(), incoming.isAlive()));
                    }
                }
            }
        } else if (packet.getType() == PacketType.GAME_EVENT_EFFECT) {
            GameEventEffectDTO effect = gson.fromJson(packet.getData(), GameEventEffectDTO.class);
            if (effect != null && "EXPLOSION".equalsIgnoreCase(effect.getEventType())) {
                explosions.add(new ExplosionEffect(effect.getX(), effect.getY()));
            }
        }
    }

    private void sendInputToServer() {
        if (clientSocket == null || !clientSocket.isConnected()) {
            return;
        }
        boolean up = activeKeys.contains(KeyCode.W) || activeKeys.contains(KeyCode.UP);
        boolean down = activeKeys.contains(KeyCode.S) || activeKeys.contains(KeyCode.DOWN);
        boolean left = activeKeys.contains(KeyCode.A) || activeKeys.contains(KeyCode.LEFT);
        boolean right = activeKeys.contains(KeyCode.D) || activeKeys.contains(KeyCode.RIGHT);

        Map<String, Boolean> inputMap = new HashMap<>();
        inputMap.put("up", up);
        inputMap.put("down", down);
        inputMap.put("left", left);
        inputMap.put("right", right);

        try {
            clientSocket.sendPacket(new Packet(PacketType.PLAYER_INPUT, gson.toJson(inputMap)));
        } catch (IOException ignored) {
        }
    }

    private void sendShootRequestToServer() {
        if (clientSocket == null || !clientSocket.isConnected()) {
            return;
        }
        try {
            clientSocket.sendPacket(new Packet(PacketType.PLAYER_SHOOT_REQ, "{}"));
        } catch (IOException ignored) {
        }
    }

    // =========================================================================
    // TASK 8: LOGIC CẬP NHẬT TRẠNG THÁI & RENDER RA CANVAS
    // =========================================================================
    private void startRenderLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                sendInputToServer();
                updateClientState();

                gc.setFill(Color.rgb(22, 24, 29));
                gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

                renderBullets();
                renderTanks();
                renderExplosions();
            }
        }.start();
    }

    private void updateClientState() {
        for (Map.Entry<Integer, TankSnapshotDTO> entry : displayTanks.entrySet()) {
            int tankId = entry.getKey();
            TankSnapshotDTO current = entry.getValue();
            TankSnapshotDTO target = targetTanks.get(tankId);

            if (target != null) {
                double newX = current.getX() + (target.getX() - current.getX()) * LERP_FACTOR;
                double newY = current.getY() + (target.getY() - current.getY()) * LERP_FACTOR;

                double diffAngle = (target.getAngle() - current.getAngle() + 540) % 360 - 180;
                double newAngle = (current.getAngle() + diffAngle * LERP_FACTOR + 360) % 360;

                current.setX(newX);
                current.setY(newY);
                current.setAngle(newAngle);

                updateTankHpAndStatus(current, target.getHp(), target.isAlive());
            }
        }

        for (ExplosionEffect exp : explosions) {
            exp.update();
        }
        explosions.removeIf(exp -> exp.finished);
    }

    private void updateTankHpAndStatus(TankSnapshotDTO dto, int hp, boolean isAlive) {
        try {
            java.lang.reflect.Field hpField = TankSnapshotDTO.class.getDeclaredField("hp");
            hpField.setAccessible(true);
            hpField.setInt(dto, hp);

            java.lang.reflect.Field aliveField = TankSnapshotDTO.class.getDeclaredField("isAlive");
            aliveField.setAccessible(true);
            aliveField.setBoolean(dto, isAlive);
        } catch (Exception ignored) {
        }
    }

    private void renderBullets() {
        gc.setFill(Color.YELLOW);
        for (BulletSnapshotDTO bullet : bullets) {
            gc.fillOval(bullet.getX() - BULLET_SIZE / 2.0, bullet.getY() - BULLET_SIZE / 2.0, BULLET_SIZE, BULLET_SIZE);
        }
    }

    private void renderTanks() {
        for (TankSnapshotDTO tank : displayTanks.values()) {
            if (!tank.isAlive()) {
                continue;
            }

            gc.save();
            gc.translate(tank.getX(), tank.getY());
            gc.rotate(tank.getAngle() + 90.0);

            // Xe 1 màu xanh lá tươi, Xe 2 màu đỏ cam
            Color bodyColor = (tank.getId() == 1) ? Color.web("#4CAF50") : Color.web("#F44336");

            // 1. Hai vệt xích đen 2 bên
            gc.setFill(Color.web("#333333"));
            gc.fillRect(-TANK_SIZE / 2.0 - 2, -TANK_SIZE / 2.0, 5, TANK_SIZE);
            gc.fillRect(TANK_SIZE / 2.0 - 3, -TANK_SIZE / 2.0, 5, TANK_SIZE);

            // 2. Thân xe vuông vức
            gc.setFill(bodyColor);
            gc.fillRect(-TANK_SIZE / 2.0 + 3, -TANK_SIZE / 2.0, TANK_SIZE - 6, TANK_SIZE);

            // 3. Nòng súng chỉ hướng bắn
            gc.setFill(Color.BLACK);
            gc.fillRect(-2.5, -TANK_SIZE / 2.0 - 10, 5, 12);

            // 4. Tháp pháo tròn ở giữa
            gc.setFill(Color.web("#212121"));
            gc.fillOval(-7, -7, 14, 14);

            gc.restore();

            // 5. Thanh HP đơn giản
            double barWidth = 36.0;
            double barHeight = 5.0;
            double barX = tank.getX() - barWidth / 2.0;
            double barY = tank.getY() - TANK_SIZE / 2.0 - 12.0;

            gc.setFill(Color.DARKRED);
            gc.fillRect(barX, barY, barWidth, barHeight);

            double hpRatio = Math.max(0.0, Math.min(1.0, (double) tank.getHp() / MAX_HP));
            gc.setFill(Color.LIME);
            gc.fillRect(barX, barY, barWidth * hpRatio, barHeight);

            gc.setStroke(Color.BLACK);
            gc.setLineWidth(1);
            gc.strokeRect(barX, barY, barWidth, barHeight);
        }
    }

    private void renderExplosions() {
        for (ExplosionEffect exp : explosions) {
            exp.render(gc);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setScene(createGameScene());
        primaryStage.setTitle("Tank 2D - Client Render");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
