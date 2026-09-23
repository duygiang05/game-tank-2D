package com.tank2d.client.view;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.tank2d.client.ClientSession;
import com.tank2d.client.controller.RoomController;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.client.util.AssetLoader;
import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.dto.game.BulletSnapshotDTO;
import com.tank2d.common.dto.game.GameEventEffectDTO;
import com.tank2d.common.dto.game.GameOverDTO;
import com.tank2d.common.dto.game.GameSnapshotDTO;
import com.tank2d.common.dto.game.TankPlayerDTO;
import com.tank2d.common.dto.game.TankSnapshotDTO;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameCanvasApp extends Application {

    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 600;
    private static final double TANK_SIZE = 36.0;
    private static final double LERP_FACTOR = 0.3;
    private static final double MAX_HP = 3.0;
    private int myTankId = -1; 
    
    private Timeline matchTimer;
    private Canvas canvas;
    private GraphicsContext gc;
    private final Gson gson = new Gson();
    private ClientSocket clientSocket;
    private Stage primaryStage;
    private RoomDTO currentRoom;
    private AnimationTimer renderTimer;
    private Consumer<Packet> packetListener;

    private final Set<KeyCode> activeKeys = new HashSet<>();
    private boolean spacePressed = false;

    // --- DỮ LIỆU ĐỒNG BỘ TỪ SERVER ---
    private final Map<Integer, TankSnapshotDTO> displayTanks = new ConcurrentHashMap<>();
    private final Map<Integer, TankSnapshotDTO> targetTanks = new ConcurrentHashMap<>();
    private final List<BulletSnapshotDTO> bullets = new CopyOnWriteArrayList<>();
    private final List<ExplosionEffect> explosions = new CopyOnWriteArrayList<>();
    private final List<TankPlayerDTO> tankPlayers = new CopyOnWriteArrayList<>();
    private final Set<String> processedBulletIds = new HashSet<>();
    private final Map<Integer, TankSnapshotDTO> globalPlayerStates = new ConcurrentHashMap<>();

    // --- DỮ LIỆU ĐỊA HÌNH & THỜI GIAN ---
    private int[][] mapMatrix;
    private int[][] brickHitsLeft;
    private int tileSize = 40;
    private int matchRemainingTime = 60;

    // Quản lý hiệu ứng nổ
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
    
    private void startMatchTimer() {
        if (matchTimer != null) matchTimer.stop();
        matchTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (matchRemainingTime > 0) {
                matchRemainingTime--;
            }
        }));
        matchTimer.setCycleCount(Timeline.INDEFINITE);
        matchTimer.play();
    }

    public void setStage(Stage stage) {
        this.primaryStage = stage;
    }

    public void setRoom(RoomDTO room) {
        this.currentRoom = room;
        if (room != null && room.getDuration() > 0) {
            this.matchRemainingTime = room.getDuration();
        }
    }

    public Scene createGameScene() {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane();
        root.getChildren().add(canvas);

        Button exitButton = new Button("THOÁT");
        exitButton.setStyle("-fx-background-color: #E74C3C; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
        StackPane.setAlignment(exitButton, Pos.TOP_RIGHT);
        StackPane.setMargin(exitButton, new Insets(10));
        exitButton.setOnAction(e -> handleExit());
        root.getChildren().add(exitButton);

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

        // Xóa phím bấm nếu window mất focus để tránh kẹt phím di chuyển
        scene.windowProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                newVal.focusedProperty().addListener((obsF, oldF, isFocused) -> {
                    if (!isFocused) activeKeys.clear();
                });
            }
        });

        canvas.setFocusTraversable(true);
        Platform.runLater(() -> canvas.requestFocus());

        loadMapConfigFromFile("map_default");
        initNetworkReceiver();
        requestTankPlayerInfo();
        
        // Bắt đầu đếm ngược thời gian trận đấu & render loop (chỉ gọi 1 lần)
        startMatchTimer();
        startRenderLoop();
        
        return scene;
    }

    private void requestTankPlayerInfo() {
    if (clientSocket == null || !clientSocket.isConnected()) return;
    try {
        clientSocket.sendPacket(new Packet(PacketType.TANK_PLAYER_INFO_REQ, ""));
    } catch (IOException ignored) {}
}
    
    private boolean isTankInBush(double tankX, double tankY) {
    if (mapMatrix == null) return false;
    int c = (int) (tankX / tileSize);
    int r = (int) (tankY / tileSize);

    if (r >= 0 && r < mapMatrix.length && c >= 0 && c < mapMatrix[0].length) {
        return mapMatrix[r][c] == 3; // 3 là ô bụi cỏ
    }
    return false;
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
                    if (type == 2) {
                        brickHitsLeft[r][c] = 3;
                    }
                }
            }
        }
    }

    private void initNetworkReceiver() {
        this.clientSocket = ClientSession.getInstance().getClientSocket();
        this.packetListener = this::processIncomingPacket;
        ClientSession.getInstance().addPacketListener(packetListener);
    }

    private void removeNetworkReceiver() {
        if (packetListener != null) {
            ClientSession.getInstance().removePacketListener(packetListener);
            packetListener = null;
        }
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
                Set<Integer> currentVisibleTankIds = new HashSet<>();

                for (TankSnapshotDTO incoming : snapshot.getTanks()) {
                    currentVisibleTankIds.add(incoming.getId());
                    targetTanks.put(incoming.getId(), incoming);
                    
                    // 1. Luôn cập nhật trạng thái HP/Sống chết vào Map toàn cục
                    globalPlayerStates.put(incoming.getId(), incoming);

                    // 2. Cập nhật cho displayTanks (Render trên bản đồ)
                    if (displayTanks.containsKey(incoming.getId())) {
                        updateTankHpAndStatus(displayTanks.get(incoming.getId()), incoming.getHp(), incoming.isAlive());
                    } else {
                        displayTanks.put(incoming.getId(), 
                            new TankSnapshotDTO(incoming.getId(), incoming.getX(), incoming.getY(), 
                                                incoming.getAngle(), incoming.getHp(), incoming.isAlive()));
                    }
                }

                if (myTankId == -1) {
                    updateMyTankIdFromSession();
                }

                // CHỈ xóa khỏi displayTanks để ẩn xe khỏi bản đồ khi chui vào cỏ
                displayTanks.keySet().removeIf(id -> !currentVisibleTankIds.contains(id));
                // KHÔNG xóa targetTanks hay globalPlayerStates!
            }
        }
    } 
    else if (packet.getType() == PacketType.TANK_PLAYER_INFO) {
        Type listType = new TypeToken<ArrayList<TankPlayerDTO>>(){}.getType();
        List<TankPlayerDTO> players = gson.fromJson(packet.getData(), listType);
        if (players != null) {
            tankPlayers.clear();
            tankPlayers.addAll(players);
            updateMyTankIdFromSession();
        }
    }
    else if (packet.getType() == PacketType.GAME_EVENT_EFFECT) {
        GameEventEffectDTO effect = gson.fromJson(packet.getData(), GameEventEffectDTO.class);
        if (effect != null) {
            String eventType = effect.getEventType() != null ? effect.getEventType().toUpperCase() : "";
            if ("EXPLOSION".equalsIgnoreCase(eventType)) {
                explosions.add(new ExplosionEffect(effect.getX(), effect.getY()));
            } else if (eventType.contains("BRICK") || eventType.contains("TILE") || eventType.contains("WALL")) {
                int c = (int) Math.floor(effect.getX() / tileSize);
                int r = (int) Math.floor(effect.getY() / tileSize);
                if (mapMatrix != null && r >= 0 && r < mapMatrix.length && c >= 0 && c < mapMatrix[0].length) {
                    if (mapMatrix[r][c] == 2) {
                        brickHitsLeft[r][c]--;
                        if (brickHitsLeft[r][c] <= 0) mapMatrix[r][c] = 0;
                    }
                }
                explosions.add(new ExplosionEffect(effect.getX(), effect.getY()));
            }
        }
    } 
    else if (packet.getType() == PacketType.GAME_OVER_NOTIFY) {
        GameOverDTO gameOver = gson.fromJson(packet.getData(), GameOverDTO.class);
        Platform.runLater(() -> {
            stopAllTimers();
            showGameOverPopup(gameOver);
        });
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

    private void startRenderLoop() {
    renderTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            sendInputToServer();
            updateClientState();

            // 1. RENDER NỀN TỪ ASSETLOADER
            Image bgImg = AssetLoader.getImage("tiles/background.png");
            if (bgImg != null && !bgImg.isError()) {
                // Co giãn ảnh phủ kín diện tích Canvas (800x600)
                gc.drawImage(bgImg, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
            } else {
                // Nền dự phòng nếu chưa tìm thấy file background.png
                gc.setFill(Color.rgb(22, 24, 29));
                gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
            }

            // (Tùy chọn) Phủ 1 lớp màu tối nhẹ 20% giúp đường đạn & tank nổi bật hơn
            gc.setFill(Color.rgb(0, 0, 0, 0.2));
            gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

            // 2. Render ma trận tường
            renderTerrainAndWalls();

            // 3. Render đạn, xe & hiệu ứng nổ
            renderBullets();
            renderTanks();
            renderExplosions();

            // 4. Render bụi cỏ đè lên xe
            renderBushes();

            // 5. Render bảng HUD & đồng hồ
            renderHUD();
        }
    };
    renderTimer.start();
}

private void updateClientState() {
    // Trong updateClientState():
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
        
        // Đồng bộ HP vào Map toàn cục
        if (globalPlayerStates.containsKey(tankId)) {
            globalPlayerStates.get(tankId).setHp(target.getHp());
        }
    }
}

    // 2. XỬ LÝ VA CHẠM ĐẠN VỚI TƯỜNG (Khắc phục triệt để va chạm lặp)
    if (mapMatrix != null && !bullets.isEmpty()) {
        List<BulletSnapshotDTO> bulletsToRemove = new ArrayList<>();

        for (BulletSnapshotDTO bullet : bullets) {
            // Tạo ID định danh duy nhất cho viên đạn dựa trên tọa độ/vận tốc
            String bulletKey = bullet.getX() + "_" + bullet.getY() + "_" + bullet.getVx() + "_" + bullet.getVy();

            // Nếu viên đạn này đã được xử lý va chạm trước đó rồi thì bỏ qua
            if (processedBulletIds.contains(bulletKey)) {
                continue;
            }

            int c = (int) Math.floor(bullet.getX() / tileSize);
            int r = (int) Math.floor(bullet.getY() / tileSize);

            if (r >= 0 && r < mapMatrix.length && c >= 0 && c < mapMatrix[0].length) {
                int tileType = mapMatrix[r][c];

                if (tileType == 2) { // Tường gạch
                    // Đánh dấu đạn đã xử lý
                    processedBulletIds.add(bulletKey);

                    // Trừ HP gạch CHÍNH XÁC 1 ĐƠN VỊ
                    brickHitsLeft[r][c]--;

                    double centerX = c * tileSize + tileSize / 2.0;
                    double centerY = r * tileSize + tileSize / 2.0;

                    if (brickHitsLeft[r][c] <= 0) {
                        // BẮN PHÁT THỨ 3 (HP <= 0): Ô gạch biến thành đường trống (0) và nổ lớn
                        mapMatrix[r][c] = 0;
                        explosions.add(new ExplosionEffect(centerX, centerY));
                    } else {
                        // BẮN PHÁT 1 & 2: Nổ tia lửa tại điểm va chạm
                        explosions.add(new ExplosionEffect(bullet.getX(), bullet.getY()));
                    }

                    bulletsToRemove.add(bullet);
                } else if (tileType == 1) { // Tường đá
                    processedBulletIds.add(bulletKey);
                    explosions.add(new ExplosionEffect(bullet.getX(), bullet.getY()));
                    bulletsToRemove.add(bullet);
                }
            }
        }

        if (!bulletsToRemove.isEmpty()) {
            bullets.removeAll(bulletsToRemove);
        }
    }

    // Dọn dẹp memory cho processedBulletIds nếu quá nhiều
    if (processedBulletIds.size() > 500) {
        processedBulletIds.clear();
    }

    // 3. Cập nhật hiệu ứng nổ
    for (ExplosionEffect exp : explosions) exp.update();
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

    private void renderTerrainAndWalls() {
    if (mapMatrix == null) return;

    Image stoneImg = AssetLoader.getImage("tiles/stone_wall.png");
    Image brickImg = AssetLoader.getImage("tiles/brick_wall.png");

    for (int r = 0; r < mapMatrix.length; r++) {
        for (int c = 0; c < mapMatrix[r].length; c++) {
            int tileType = mapMatrix[r][c];
            double x = c * tileSize;
            double y = r * tileSize;

            // Đất trống (0) -> BỎ QUA KHÔNG VẼ
            if (tileType == 0) continue;

            if (tileType == 1) { // Tường đá
                if (stoneImg != null) gc.drawImage(stoneImg, x, y, tileSize, tileSize);
                else { gc.setFill(Color.GRAY); gc.fillRect(x, y, tileSize, tileSize); }
            } else if (tileType == 2) { // Tường gạch
                int hp = brickHitsLeft[r][c];

                // Nếu HP <= 0: Ép về 0 và bỏ qua hoàn toàn
                if (hp <= 0) {
                    mapMatrix[r][c] = 0;
                    continue;
                }

                // 1. Vẽ khối gạch
                if (brickImg != null) gc.drawImage(brickImg, x, y, tileSize, tileSize);
                else { gc.setFill(Color.CHOCOLATE); gc.fillRect(x, y, tileSize, tileSize); }

                // 2. Vẽ nét nứt theo đúng số máu còn lại
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(2);

                if (hp == 2) {
                    // Bắn phát 1 (Còn 2 HP) -> Vẽ 1 vết nứt chéo nhẹ
                    gc.strokeLine(x + 8, y + 8, x + 24, y + 24);
                } else if (hp == 1) {
                    // Bắn phát 2 (Còn 1 HP) -> Vẽ 2 vết nứt chân chim đậm hơn
                    gc.strokeLine(x + 8, y + 8, x + 24, y + 24);
                    gc.strokeLine(x + 26, y + 8, x + 10, y + 30);
                }
            }
        }
    }
}

    private void renderBushes() {
    if (mapMatrix == null) return;
    Image bushImg = AssetLoader.getImage("tiles/grass.png");

    // Lấy vị trí xe của bản thân
    TankSnapshotDTO myTank = displayTanks.get(myTankId);

    for (int r = 0; r < mapMatrix.length; r++) {
        for (int c = 0; c < mapMatrix[r].length; c++) {
            if (mapMatrix[r][c] == 3) { // Ô bụi cỏ
                double x = c * tileSize;
                double y = r * tileSize;

                gc.save();

                // Kiểm tra xem xe của bản thân có đang nằm trong ô cỏ này hay không
                if (myTank != null && myTank.isAlive()) {
                    double myC = Math.floor(myTank.getX() / tileSize);
                    double myR = Math.floor(myTank.getY() / tileSize);

                    if ((int) myC == c && (int) myR == r) {
                        // Làm mờ bụi cỏ tại đúng ô xe mình đứng để lộ xe bên dưới
                        gc.setGlobalAlpha(0.55); 
                    }
                }

                if (bushImg != null) {
                    gc.drawImage(bushImg, x, y, tileSize, tileSize);
                } else {
                    gc.setFill(Color.rgb(34, 139, 34, 0.75));
                    gc.fillRect(x, y, tileSize, tileSize);
                }

                gc.restore();
            }
        }
    }
}

    private void renderBullets() {
        Image missileImg = AssetLoader.getImage("bullets/missile.png");

        for (BulletSnapshotDTO bullet : bullets) {
            double speed = Math.hypot(bullet.getVx(), bullet.getVy());
            boolean isMissile = speed > 450.0;

            if (isMissile) {
                gc.save();
                gc.translate(bullet.getX(), bullet.getY());
                double angle = Math.toDegrees(Math.atan2(bullet.getVy(), bullet.getVx()));
                gc.rotate(angle);

                if (missileImg != null) {
                    gc.drawImage(missileImg, -10, -5, 20, 10);
                } else {
                    gc.setFill(Color.ORANGE);
                    gc.fillPolygon(new double[]{-10, 10, -10}, new double[]{-5, 0, 5}, 3);
                    gc.setFill(Color.RED);
                    gc.fillOval(-16, -3, 6, 6);
                }
                gc.restore();
            } else {
                gc.setFill(Color.YELLOW);
                gc.fillOval(bullet.getX() - 4, bullet.getY() - 4, 8, 8);
            }
        }
    }

    private String getUsernameByTankId(int tankId) {
        for (TankPlayerDTO player : tankPlayers) {
            if (player.getTankId() == tankId) return player.getUsername();
        }
        return null;
    }

    private void renderTanks() {
    String[] tankImageAssets = {
        "tiles/green_tank.png",
        "tiles/red_tank.png",
        "tiles/purple_tank.png",
        "tiles/yellow_tank.png"
    };

    for (TankSnapshotDTO tank : displayTanks.values()) {
        if (!tank.isAlive()) continue;

        boolean isMyTank = (tank.getId() == myTankId);
        boolean inBush = isTankInBush(tank.getX(), tank.getY());

        // QUY TẮC BỤI CỎ:
        // - Xe địch trong bụi cỏ: Không vẽ (bỏ qua render)
        // - Xe mình trong bụi cỏ: Vẫn vẽ nhưng làm mờ (GlobalAlpha = 0.45)
        if (inBush && !isMyTank) {
            continue; 
        }

        // Chọn Sprite xe theo công thức mod 4 để đảm bảo xe thứ 3, 4, 5... đều có ảnh
        int imageIndex = Math.abs(tank.getId() - 1) % tankImageAssets.length;
        Image tankSprite = AssetLoader.getImage(tankImageAssets[imageIndex]);

        gc.save(); // Lưu trạng thái Canvas
        
        // Thiết lập độ mờ nếu là xe mình đang ở trong bụi cỏ
        if (inBush && isMyTank) {
            gc.setGlobalAlpha(0.45); // Độ mờ 45%
        }

        // 1. Render Thân & Nòng pháo Xe Tank
        gc.translate(tank.getX(), tank.getY());
        gc.rotate(tank.getAngle() + 90.0); // Xoay nòng pháo theo góc Server trả về

        if (tankSprite != null) {
            gc.drawImage(tankSprite, -TANK_SIZE / 2.0, -TANK_SIZE / 2.0, TANK_SIZE, TANK_SIZE);
        } else {
            // Chế độ dự phòng khi không nạp được file ảnh
            Color bodyColor = isMyTank ? Color.web("#4CAF50") : Color.web("#F44336");
            gc.setFill(Color.web("#333333"));
            gc.fillRect(-TANK_SIZE / 2.0 - 2, -TANK_SIZE / 2.0, 5, TANK_SIZE);
            gc.fillRect(TANK_SIZE / 2.0 - 3, -TANK_SIZE / 2.0, 5, TANK_SIZE);
            gc.setFill(bodyColor);
            gc.fillRect(-TANK_SIZE / 2.0 + 3, -TANK_SIZE / 2.0, TANK_SIZE - 6, TANK_SIZE);
            gc.setFill(Color.BLACK);
            gc.fillRect(-2.5, -TANK_SIZE / 2.0 - 10, 5, 12);
            gc.setFill(Color.web("#212121"));
            gc.fillOval(-7, -7, 14, 14);
        }
        gc.restore(); // Khôi phục trạng thái Canvas (trả Alpha về 1.0)

        // 2. Render Thanh Máu & Tên người chơi
        gc.save();
        if (inBush && isMyTank) {
            gc.setGlobalAlpha(0.5); // Thanh máu cũng mờ nhẹ theo xe
        }

        double barWidth = 36.0;
        double barHeight = 6.0;
        double barX = tank.getX() - barWidth / 2.0;
        double barY = tank.getY() - TANK_SIZE / 2.0 - 12.0;

        String username = getUsernameByTankId(tank.getId());
        if (username != null) {
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            double textWidth = username.length() * 6.5;
            gc.fillText(username, tank.getX() - textWidth / 2.0, barY - 6);
        }

        // Vẽ nền thanh máu (Đỏ)
        gc.setFill(Color.DARKRED);
        gc.fillRect(barX, barY, barWidth, barHeight);

        // Vẽ phần máu hiện tại (Xanh lá)
        int currentHp = (int) Math.max(0, Math.min(MAX_HP, tank.getHp()));
        double hpWidth = (barWidth / MAX_HP) * currentHp;
        gc.setFill(Color.LIME);
        gc.fillRect(barX, barY, hpWidth, barHeight);

        // Khung thanh máu
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1);
        gc.strokeRect(barX, barY, barWidth, barHeight);
        for (int i = 1; i < MAX_HP; i++) {
            double lineX = barX + (barWidth / MAX_HP) * i;
            gc.strokeLine(lineX, barY, lineX, barY + barHeight);
        }
        gc.restore();
    }
}

    private void renderExplosions() {
        for (ExplosionEffect exp : explosions) {
            exp.render(gc);
        }
    }

    private void renderHUD() {
    gc.save();

    // 1. Đồng hồ đếm ngược
    gc.setFill(Color.rgb(0, 0, 0, 0.3));
    gc.fillRoundRect(340, 10, 120, 35, 10, 10);
    gc.setStroke(Color.rgb(255, 215, 0, 0.5));
    gc.setLineWidth(1.5);
    gc.strokeRoundRect(340, 10, 120, 35, 10, 10);

    gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
    gc.setFill(Color.rgb(255, 255, 0, 0.75));
    gc.fillText(String.format("⏱ %02d:%02d", matchRemainingTime / 60, matchRemainingTime % 60), 365, 34);

    // 2. Bảng điểm mini
    gc.setFill(Color.rgb(15, 18, 24, 0.4));
    gc.fillRoundRect(10, 10, 220, 125, 8, 8);
    gc.setStroke(Color.rgb(58, 63, 77, 0.5));
    gc.strokeRoundRect(10, 10, 220, 125, 8, 8);

    gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
    gc.setFill(Color.rgb(255, 255, 255, 0.9));
    gc.fillText("BẢNG ĐIỂM TRẬN ĐẤU", 20, 28);
    gc.setStroke(Color.rgb(128, 128, 128, 0.4));
    gc.strokeLine(20, 34, 210, 34);

    gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
    int startY = 52;

    Color[] tankColors = {
        Color.rgb(76, 175, 80),  // Tank 1 - Green
        Color.rgb(244, 67, 54),  // Tank 2 - Red
        Color.rgb(156, 39, 176), // Tank 3 - Purple
        Color.rgb(255, 193, 7)   // Tank 4 - Yellow
    };

    if (tankPlayers.isEmpty()) {
        gc.setFill(Color.rgb(211, 211, 211, 0.6));
        gc.fillText("Đang chờ dữ liệu...", 20, startY);
    } else {
        // Duyệt danh sách cố định tất cả người chơi trong phòng
        for (TankPlayerDTO player : tankPlayers) {
            int tId = player.getTankId();
            String displayName = player.getUsername();

            if (tId == myTankId) {
                displayName += " (Tôi)";
            }
            if (displayName.length() > 10) displayName = displayName.substring(0, 10);

            // LẤY HP CHÍNH XÁC TỪ GLOBAL PLAYER STATES
            int currentHp = 3;
            if (globalPlayerStates.containsKey(tId)) {
                currentHp = globalPlayerStates.get(tId).getHp();
            } else if (displayTanks.containsKey(tId)) {
                currentHp = displayTanks.get(tId).getHp();
            }

            int colorIdx = Math.abs(tId - 1) % tankColors.length;
            gc.setFill(tankColors[colorIdx]);

            gc.fillText(String.format("%-10s | HP:%d", displayName, currentHp), 20, startY);
            startY += 18;
        }
    }

    gc.restore();
}
    
    private void updateMyTankIdFromSession() {
    com.tank2d.common.model.User currentUser = ClientSession.getInstance().getCurrentUser();
    
    if (currentUser != null && currentUser.getUsername() != null && !tankPlayers.isEmpty()) {
        String myUsername = currentUser.getUsername();
        
        for (TankPlayerDTO player : tankPlayers) {
            if (myUsername.equalsIgnoreCase(player.getUsername())) {
                this.myTankId = player.getTankId(); // Gán đúng Tank ID của tài khoản đang đăng nhập
                return;
            }
        }
    }
}

    private void stopAllTimers() {
        if (renderTimer != null) renderTimer.stop();
        if (matchTimer != null) matchTimer.stop();
    }

    private void handleExit() {
        stopAllTimers();
        activeKeys.clear();
        spacePressed = false;
        removeNetworkReceiver();

        try {
            if (clientSocket != null && clientSocket.isConnected()) {
                clientSocket.sendPacket(new Packet(PacketType.ROOM_LEAVE_REQ, ""));
            }
        } catch (IOException ignored) {}

        Platform.runLater(() -> {
            try {
                if (primaryStage == null) {
                    primaryStage = (Stage) canvas.getScene().getWindow();
                }
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tank2d/client/view/lobby.fxml"));
                primaryStage.setScene(new Scene(loader.load()));
                primaryStage.setTitle("Tank 2D Online - Lobby");
                primaryStage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void handlePlayAgain() {
        stopAllTimers();
        activeKeys.clear();
        spacePressed = false;
        removeNetworkReceiver();

        Platform.runLater(() -> {
            try {
                if (primaryStage == null) {
                    primaryStage = (Stage) canvas.getScene().getWindow();
                }
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tank2d/client/view/room.fxml"));
                Parent root = loader.load();
                RoomController controller = loader.getController();
                controller.setRoom(currentRoom);
                controller.resetReadyForReplay();

                primaryStage.setScene(new Scene(root));
                primaryStage.setTitle("Tank 2D Online - Room");
                primaryStage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void showGameOverPopup(GameOverDTO gameOver) {
        Stage popup = new Stage();
        if (primaryStage == null && canvas != null && canvas.getScene() != null) {
            primaryStage = (Stage) canvas.getScene().getWindow();
        }
        if (primaryStage != null) popup.initOwner(primaryStage);
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Tank 2D - Kết quả");

        Label title = new Label("🏆 KẾT THÚC VÁN ĐẤU");
        title.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 24px; -fx-font-weight: bold;");

        String winnerUsername = getUsernameByTankId(gameOver.getWinnerTankId());
        String winnerText = "🏆 Người chiến thắng: TANK " + gameOver.getWinnerTankId() 
                + (winnerUsername != null ? " - " + winnerUsername : "");
        Label winnerLabel = new Label(winnerText);
        winnerLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        GridPane table = new GridPane();
        table.setHgap(15);
        table.setVgap(10);
        table.setAlignment(Pos.CENTER);

        String[] headerTitles = {"HẠNG", "TANK", "SCORE", "KILL", "HIT"};
        for (int i = 0; i < headerTitles.length; i++) {
            Label h = new Label(headerTitles[i]);
            h.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 13px; -fx-font-weight: bold;");
            table.add(h, i, 0);
        }

        List<Integer> tankIds = new ArrayList<>(gameOver.getFinalScores().keySet());
        tankIds.sort((a, b) -> Integer.compare(gameOver.getFinalScores().getOrDefault(b, 0), gameOver.getFinalScores().getOrDefault(a, 0)));

        int rank = 1;
        for (Integer tId : tankIds) {
            String uName = getUsernameByTankId(tId);
            String displayName = "TANK " + tId + (uName != null ? " - " + uName : "");

            Label rankLbl = new Label(rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : String.valueOf(rank));
            Label nameLbl = new Label(displayName);
            Label scoreLbl = new Label(String.valueOf(gameOver.getFinalScores().getOrDefault(tId, 0)));
            Label killLbl = new Label(String.valueOf(gameOver.getFinalKills().getOrDefault(tId, 0)));
            Label hitLbl = new Label(String.valueOf(gameOver.getFinalHits().getOrDefault(tId, 0)));

            Label[] row = {rankLbl, nameLbl, scoreLbl, killLbl, hitLbl};
            for (int i = 0; i < row.length; i++) {
                row[i].setStyle("-fx-text-fill: " + (tId == gameOver.getWinnerTankId() ? "#FFD700" : "white") + "; -fx-font-size: 14px;");
                GridPane.setHalignment(row[i], i == 1 ? HPos.LEFT : HPos.CENTER);
                table.add(row[i], i, rank);
            }
            rank++;
        }

        Label reasonLabel = new Label("⏱ Kết thúc: " + gameOver.getReason());
        reasonLabel.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 13px;");

        Button playAgainBtn = new Button("🔄 CHƠI TIẾP");
        playAgainBtn.setStyle("-fx-background-color: #2ECC71; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        playAgainBtn.setOnAction(e -> { popup.close(); handlePlayAgain(); });

        Button exitBtn = new Button("🚪 THOÁT");
        exitBtn.setStyle("-fx-background-color: #E74C3C; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        exitBtn.setOnAction(e -> { popup.close(); handleExit(); });

        HBox btns = new HBox(20, playAgainBtn, exitBtn);
        btns.setAlignment(Pos.CENTER);

        VBox content = new VBox(15, title, winnerLabel, table, reasonLabel, btns);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(25));
        content.setStyle("-fx-background-color: #151A24; -fx-background-radius: 12;");

        popup.setScene(new Scene(new StackPane(content), 580, 460));
        popup.setResizable(false);
        popup.showAndWait();
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setScene(createGameScene());
        primaryStage.setTitle("Tank 2D - Client Render");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}