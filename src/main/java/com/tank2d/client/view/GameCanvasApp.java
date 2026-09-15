package com.tank2d.client.view;

import com.google.gson.Gson;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.dto.game.BulletSnapshotDTO;
import com.tank2d.common.dto.game.GameSnapshotDTO;
import com.tank2d.common.dto.game.TankSnapshotDTO;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;

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

    private Canvas canvas;
    private GraphicsContext gc;
    private final Gson gson = new Gson();

    private ClientSocket clientSocket;

    // FPS Counter
    private long lastFpsCheck = System.nanoTime();
    private int frameCounter = 0;
    private int currentFps = 0;

    // Quản lý trạng thái bàn phím
    private final Set<KeyCode> activeKeys = new HashSet<>();
    private boolean spacePressed = false;

    // Danh sách Snapshot Render (CopyOnWriteArrayList chống ConcurrentModificationException)
    private final List<TankSnapshotDTO> tanks = new CopyOnWriteArrayList<>();
    private final List<BulletSnapshotDTO> bullets = new CopyOnWriteArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT);

        // KHỞI TẠO XE MẶC ĐỊNH SẴN ĐỂ MÀN HÌNH KHÔNG BỊ ĐEN RỖNG KHI CHỜ SERVER GỬI SNAPSHOT
        initDefaultTanks();

        // LẮNG NGHE SỰ KIỆN BÀN PHÍM
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

        primaryStage.setTitle("Tank 2D - Canvas Render Engine (Task 7)");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
        
        canvas.setFocusTraversable(true);
        canvas.requestFocus();

        // 1. KẾT NỐI MẠNG TỪ CLIENTSESSION
        initNetworkFromSession();

        // 2. VÒNG LẶP RENDER DỰNG HÌNH & GỬI INPUT (60 FPS)
        startRenderLoop();
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

private void updateLocalBulletPhysics() {
    for (BulletSnapshotDTO b : bullets) {
        b.setX(b.getX() + b.getVx());
        setBulletY(b, b.getY() + b.getVy());
    }

    bullets.removeIf(b -> b.getX() < 0 || b.getX() > CANVAS_WIDTH || b.getY() < 0 || b.getY() > CANVAS_HEIGHT);
}

private void setBulletY(BulletSnapshotDTO bullet, double newY) {
    try {
        java.lang.reflect.Field fieldY = BulletSnapshotDTO.class.getDeclaredField("y");
        fieldY.setAccessible(true);
        fieldY.setDouble(bullet, newY);
    } catch (Exception ignored) {}
}

private void initNetworkFromSession() {
    // 1. Lấy socket từ Session
    this.clientSocket = ClientSession.getInstance().getClientSocket();

    // 2. NẾU NULL: Tự khởi tạo và GỌI CONNECT()
    if (this.clientSocket == null || !this.clientSocket.isConnected()) {
        System.out.println("[GameCanvasApp] Tự động tạo và kết nối Socket tới localhost:8888...");
        try {
            this.clientSocket = new ClientSocket("localhost", 8888);
            this.clientSocket.connect(); // <--- ĐÂY LÀ DÒNG QUAN TRỌNG NHẤT BỊ THIẾU!
        } catch (Exception e) {
            System.err.println("[GameCanvasApp] Kết nối Server thất bại: " + e.getMessage());
        }
    }

    // 3. Khởi tạo luồng đọc Snapshot từ Server
    if (this.clientSocket != null && this.clientSocket.isConnected()) {
        System.out.println("[GameCanvasApp] Socket đã kết nối thành công! Đang lắng nghe Snapshot...");
        
        Thread networkReadThread = new Thread(() -> {
            while (clientSocket != null && clientSocket.isConnected()) {
                try {
                    Packet packet = clientSocket.receivePacket();
                    if (packet != null && packet.getType() == PacketType.GAME_SNAPSHOT) {
                        handleGameSnapshot(packet.getData());
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
    } else {
        System.err.println("[GameCanvasApp] CẢNH BÁO: Không thể kết nối tới Server!");
    }
}

    private void handleGameSnapshot(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isEmpty()) return;

        try {
            GameSnapshotDTO snapshot = gson.fromJson(jsonPayload, GameSnapshotDTO.class);
            if (snapshot != null) {
                if (snapshot.getBullets() != null) {
                    this.bullets.clear();
                    this.bullets.addAll(snapshot.getBullets());
                }

                if (snapshot.getTanks() != null && !snapshot.getTanks().isEmpty()) {
                    this.tanks.clear();
                    this.tanks.addAll(snapshot.getTanks());
                }
            }
        } catch (Exception e) {
            System.err.println("[GameCanvasApp] Lỗi parse GameSnapshotDTO: " + e.getMessage());
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

            String jsonPayload = gson.toJson(inputMap);
            try {
                clientSocket.sendPacket(new Packet(PacketType.PLAYER_INPUT, jsonPayload));
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

                // 1. Gửi phím bấm lên Server (cho luồng Server)
                sendInputToServer();

                // 2. Tính toán vật lý cục bộ để di chuyển xe ngay lập tức trên màn hình
                updateLocalPhysics();

                // 3. Xóa và vẽ lại Canvas
                gc.setFill(Color.rgb(22, 24, 29));
                gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

                renderBullets();
                renderTanks();
                renderMiniScoreboard();
                renderOverlayInfo();
            }
        }.start();
    }

    // TÍNH TOÁN DI CHUYỂN CỤC BỘ (Giúp xe phản hồi bàn phím ngay lập tức)
    private void updateLocalPhysics() {
        if (tanks.isEmpty()) return;

        // Lấy xe đầu tiên (Xe Player #1)
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

        // Giới hạn trong khung hình Canvas
        x = Math.max(30, Math.min(CANVAS_WIDTH - 30, x));
        y = Math.max(30, Math.min(CANVAS_HEIGHT - 30, y));

        // Cập nhật lại vị trí tạm thời để Canvas vẽ ngay
        playerTank.setX(x);
        playerTank.setY(y);
        playerTank.setAngle(angle);
        
        updateLocalBulletPhysics();
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