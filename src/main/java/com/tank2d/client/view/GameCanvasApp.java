package com.tank2d.client.view;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.client.util.AssetLoader;
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
import javafx.scene.image.Image;
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

public class GameCanvasApp extends Application {

    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 600;
    private static final double TANK_SIZE = 36.0;
//    private static final double BULLET_SIZE = 8.0;
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

    // --- DỮ LIỆU ĐỊA HÌNH & THỜI GIAN ---
    private int[][] mapMatrix;
    private int[][] brickHitsLeft;
    private int tileSize = 40;
    private int matchRemainingTime = 60; // Thời gian đếm ngược trận đấu (giây)
    
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
            if (radius > maxRadius) finished = true;
        }

        void render(GraphicsContext gc) {
            if (finished) return;
            gc.setFill(Color.ORANGE);
            gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            gc.setFill(Color.RED);
            gc.fillOval(x - (radius / 2.0), y - (radius / 2.0), radius, radius);
            gc.setFill(Color.YELLOW);
            gc.fillOval(x - (radius / 4.0), y - (radius / 4.0), radius / 2.0, radius / 2.0);
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

        loadMapConfigFromFile("map_default");
        initNetworkReceiver();
        startRenderLoop();

        return scene;
    }

private void loadMapConfigFromFile(String mapName) {
        JsonObject mapJson = AssetLoader.loadMapConfig(mapName);
        if (mapJson != null && mapJson.has("matrix")) {
            JsonArray rows = mapJson.getAsJsonArray("matrix");
            int h = rows.size();
            int w = rows.get(0).getAsJsonArray().size();
            mapMatrix = new int[h][w];
            brickHitsLeft = new int[h][w];

            for (int r = 0; r < h; r++) {
                JsonArray cols = rows.get(r).getAsJsonArray();
                for (int c = 0; c < w; c++) {
                    int type = cols.get(c).getAsInt();
                    mapMatrix[r][c] = type;
                    if (type == 2) { // BRICK_WALL (Tường gạch)
                        brickHitsLeft[r][c] = 3; // Máu gạch mặc định = 3
                    }
                }
            }
        }
    }

    // TASK 7 SPRINT 2: LOGIC NHẬN GÓI TIN TỪ SERVER
    private void initNetworkReceiver() {
        this.clientSocket = ClientSession.getInstance().getClientSocket();
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
        if (clientSocket == null || !clientSocket.isConnected()) return;
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
        } catch (IOException ignored) {}
    }

    private void sendShootRequestToServer() {
        if (clientSocket == null || !clientSocket.isConnected()) return;
        try {
            clientSocket.sendPacket(new Packet(PacketType.PLAYER_SHOOT_REQ, "{}"));
        } catch (IOException ignored) {}
    }

// =========================================================================
    // VÒNG LẶP RENDER 60 FPS
    // =========================================================================
private void startRenderLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                sendInputToServer();
                updateClientState();

                // 1. Clear nền tối
                gc.setFill(Color.rgb(22, 24, 29));
                gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

                // 2. Render Ma trận Tường (Tường đá, Tường gạch)
                renderTerrainAndWalls();

                // 3. Render Đạn, Xe tăng & Hiệu ứng nổ
                renderBullets();
                renderTanks();
                renderExplosions();

                // 4. Render Bụi cỏ đè lên xe (Tạo hiệu ứng tàng hình/che khuất)
                renderBushes();

                // 5. Render Bảng HUD & Đồng hồ đếm ngược
                renderHUD();
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
        } catch (Exception ignored) {}
    }

// =========================================================================
    // TASK 7: RENDER MA TRẬN ĐỊA HÌNH & TƯỜNG (VẾT NỨT GẠCH 1/3, 2/3)
    // =========================================================================
    private void renderTerrainAndWalls() {
        if (mapMatrix == null) return;

        Image stoneImg = AssetLoader.getImage("tiles/stone_wall.png");
        Image brickImg = AssetLoader.getImage("tiles/brick_wall.png");

        for (int r = 0; r < mapMatrix.length; r++) {
            for (int c = 0; c < mapMatrix[r].length; c++) {
                int tileType = mapMatrix[r][c];
                double x = c * tileSize;
                double y = r * tileSize;

                if (tileType == 1) { // TƯỜNG ĐÁ (STONE_WALL)
                    if (stoneImg != null) {
                        gc.drawImage(stoneImg, x, y, tileSize, tileSize);
                    } else {
                        gc.setFill(Color.GRAY);
                        gc.fillRect(x, y, tileSize, tileSize);
                    }
                } else if (tileType == 2) { // TƯỜNG GẠCH (BRICK_WALL)
                    if (brickImg != null) {
                        gc.drawImage(brickImg, x, y, tileSize, tileSize);
                    } else {
                        gc.setFill(Color.CHOCOLATE);
                        gc.fillRect(x, y, tileSize, tileSize);
                    }

                    // VẼ VẾT NỨT THEO ĐỘ BỀN CÒN LẠI (1/3, 2/3)
                    int hitsLeft = brickHitsLeft[r][c];
                    if (hitsLeft < 3) {
                        gc.setStroke(Color.BLACK);
                        gc.setLineWidth(2);
                        // Vết nứt 1 (khi trúng phát 1)
                        gc.strokeLine(x + 8, y + 8, x + 18, y + 22);
                        if (hitsLeft <= 1) {
                            // Vết nứt 2 (khi trúng phát 2 - sắp vỡ)
                            gc.strokeLine(x + 24, y + 10, x + 32, y + 32);
                            gc.strokeLine(x + 10, y + 26, x + 28, y + 34);
                        }
                    }
                }
            }
        }
    }
    // =========================================================================
    // TASK 7: RENDER BỤI CỎ (VẼ BỤI CỎ SAU CÙNG ĐỂ CHE KHUẤT XE TĂNG)
    // =========================================================================
    private void renderBushes() {
        if (mapMatrix == null) return;
        Image bushImg = AssetLoader.getImage("tiles/grass.png");

        for (int r = 0; r < mapMatrix.length; r++) {
            for (int c = 0; c < mapMatrix[r].length; c++) {
                if (mapMatrix[r][c] == 3) { // BỤI CỎ (BUSH)
                    double x = c * tileSize;
                    double y = r * tileSize;
                    if (bushImg != null) {
                        gc.drawImage(bushImg, x, y, tileSize, tileSize);
                    } else {
                        gc.setFill(Color.rgb(34, 139, 34, 0.75));
                        gc.fillRect(x, y, tileSize, tileSize);
                    }
                }
            }
        }
    }
    
// =========================================================================
    // TASK 7: RENDER ĐẠN (PHÂN BIỆT ĐẠN THƯỜNG VS ĐẠN TÊN LỬA MÀ KHÔNG CẦN FIELD TYPE)
    // =========================================================================
    private void renderBullets() {
        Image missileImg = AssetLoader.getImage("bullets/missile.png");

        for (BulletSnapshotDTO bullet : bullets) {
            // TÍNH VẬN TỐC LƯỢNG GIÁC CỦA ĐẠN
            double speed = Math.hypot(bullet.getVx(), bullet.getVy());
            
            // QUY ƯỚC PHÂN BIỆT: Đạn tên lửa có vận tốc cao hơn đạn thường (hoặc căn cứ theo vx/vy)
            boolean isMissile = speed > 450.0; 

            if (isMissile) {
                // RENDER ĐẠN TÊN LỬA (Hình thoi dài kèm đuôi lửa)
                gc.save();
                gc.translate(bullet.getX(), bullet.getY());
                double angle = Math.toDegrees(Math.atan2(bullet.getVy(), bullet.getVx()));
                gc.rotate(angle);

                if (missileImg != null) {
                    gc.drawImage(missileImg, -10, -5, 20, 10);
                } else {
                    // Mẫu vẽ đạn tên lửa sắc nét
                    gc.setFill(Color.ORANGE);
                    gc.fillPolygon(new double[]{-10, 10, -10}, new double[]{-5, 0, 5}, 3);
                    // Đuôi lửa phát sáng phía sau
                    gc.setFill(Color.RED);
                    gc.fillOval(-16, -3, 6, 6);
                }
                gc.restore();
            } else {
                // RENDER ĐẠN THƯỜNG (Hình tròn nhỏ màu vàng)
                gc.setFill(Color.YELLOW);
                gc.fillOval(bullet.getX() - 4, bullet.getY() - 4, 8, 8);
            }
        }
    }
    
// =========================================================================
    // TASK 7: RENDER XE TĂNG & THANH MÁU 3 VẠCH
    // =========================================================================
    private void renderTanks() {
        for (TankSnapshotDTO tank : displayTanks.values()) {
            if (!tank.isAlive()) continue;

            // 1. Thân & Nòng xe
            gc.save();
            gc.translate(tank.getX(), tank.getY());
            gc.rotate(tank.getAngle() + 90.0);

            Color bodyColor = (tank.getId() == 1) ? Color.web("#4CAF50") : Color.web("#F44336");

            // Xích xe 2 bên
            gc.setFill(Color.web("#333333"));
            gc.fillRect(-TANK_SIZE / 2.0 - 2, -TANK_SIZE / 2.0, 5, TANK_SIZE);
            gc.fillRect(TANK_SIZE / 2.0 - 3, -TANK_SIZE / 2.0, 5, TANK_SIZE);

            // Thân xe
            gc.setFill(bodyColor);
            gc.fillRect(-TANK_SIZE / 2.0 + 3, -TANK_SIZE / 2.0, TANK_SIZE - 6, TANK_SIZE);

            // Nòng súng
            gc.setFill(Color.BLACK);
            gc.fillRect(-2.5, -TANK_SIZE / 2.0 - 10, 5, 12);

            // Tháp pháo
            gc.setFill(Color.web("#212121"));
            gc.fillOval(-7, -7, 14, 14);
            gc.restore();

            // 2. THANH MÁU 3 VẠCH (3-bar HP)
            double barWidth = 36.0;
            double barHeight = 6.0;
            double barX = tank.getX() - barWidth / 2.0;
            double barY = tank.getY() - TANK_SIZE / 2.0 - 12.0;

            // Viền nền màu đỏ đậm
            gc.setFill(Color.DARKRED);
            gc.fillRect(barX, barY, barWidth, barHeight);

            // Tỉ lệ máu hiện tại (0 -> 3 vạch)
            int currentHp = (int)Math.max(0, Math.min(MAX_HP, tank.getHp()));
            double hpWidth = (barWidth / MAX_HP) * currentHp;
            gc.setFill(Color.LIME);
            gc.fillRect(barX, barY, hpWidth, barHeight);

            // Kẻ 3 vạch chia thanh máu
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(1);
            gc.strokeRect(barX, barY, barWidth, barHeight);
            for (int i = 1; i < MAX_HP; i++) {
                double lineX = barX + (barWidth / MAX_HP) * i;
                gc.strokeLine(lineX, barY, lineX, barY + barHeight);
            }
        }
    }

    private void renderExplosions() {
        for (ExplosionEffect exp : explosions) {
            exp.render(gc);
        }
    }

    // =========================================================================
    // TASK 7: RENDER THANH HUD, ĐỒNG HỒ ĐẾM NGƯỢC & BẢNG ĐIỂM THU NHỎ
    // =========================================================================
// =========================================================================
    // TASK 7: RENDER THANH HUD, ĐỒNG HỒ ĐẾM NGƯỢC & BẢNG ĐIỂM (BÁN TRONG SUỐT)
    // =========================================================================
    private void renderHUD() {
        gc.save(); // Lưu trạng thái GraphicsContext để không ảnh hưởng các nét vẽ khác

        // 1. ĐỒNG HỒ ĐẾM NGƯỢC THỜI GIAN (Góc trên ở giữa - Nền mờ 30%, Chữ mờ 70%)
        // Nền khung đồng hồ (Opacity = 0.3)
        gc.setFill(Color.rgb(0, 0, 0, 0.3));
        gc.fillRoundRect(340, 10, 120, 35, 10, 10);
        
        // Viền đồng hồ (Opacity = 0.5)
        gc.setStroke(Color.rgb(255, 215, 0, 0.5));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(340, 10, 120, 35, 10, 10);

        // Chữ thời gian (Opacity = 0.75 để vẫn đủ nhìn rõ)
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        gc.setFill(Color.rgb(255, 255, 0, 0.75));
        String timeStr = String.format("%02d:%02d", matchRemainingTime / 60, matchRemainingTime % 60);
        gc.fillText("⏱ " + timeStr, 365, 34);

        // 2. BẢNG XẾP HẠNG THU NHỎ (Góc trái màn hình - Nền mờ 25%, Chữ mờ 65%)
        // Nền khung Bảng điểm (Opacity = 0.25 giúp thấy rõ tank nấp phía sau)
        gc.setFill(Color.rgb(15, 18, 24, 0.25));
        gc.fillRoundRect(10, 10, 210, 115, 8, 8);
        
        // Viền Bảng điểm (Opacity = 0.4)
        gc.setStroke(Color.rgb(58, 63, 77, 0.4));
        gc.strokeRoundRect(10, 10, 210, 115, 8, 8);

        // Tiêu đề Bảng điểm
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        gc.setFill(Color.rgb(255, 255, 255, 0.7));
        gc.fillText("BẢNG ĐIỂM TRẬN ĐẤU", 20, 28);
        gc.setStroke(Color.rgb(128, 128, 128, 0.4));
        gc.strokeLine(20, 34, 200, 34);

        // Danh sách thông số xe tăng
        gc.setFont(Font.font("Consolas", FontWeight.NORMAL, 12));
        int startY = 52;
        if (displayTanks.isEmpty()) {
            gc.setFill(Color.rgb(211, 211, 211, 0.6));
            gc.fillText("Đang chờ người chơi...", 20, startY);
        } else {
            for (TankSnapshotDTO tank : displayTanks.values()) {
                String name = "Tank #" + tank.getId();
                // Xe 1 màu Xanh lá mờ, Xe khác màu Đỏ mờ
                Color textColor = (tank.getId() == 1) 
                        ? Color.rgb(76, 175, 80, 0.75) 
                        : Color.rgb(244, 67, 54, 0.75);
                
                gc.setFill(textColor);
                gc.fillText(String.format("%-8s | HP:%d | K:0 H:0", name, tank.getHp()), 20, startY);
                startY += 18;
            }
        }

        gc.restore(); // Khôi phục trạng thái cọ vẽ nguyên bản
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