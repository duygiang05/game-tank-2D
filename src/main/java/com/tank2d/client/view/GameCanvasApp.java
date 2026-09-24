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

    // --- BIẾN CẤU HÌNH ĐỌC ĐỘNG TỪ JSON ---
    private int canvasWidth = 800;
    private int canvasHeight = 800;
    private double tankSize = 36.0;
    private double lerpFactor = 0.3;
    private double maxHp = 3.0;
    private int brickWallMaxHits = 3;
    private int tileSize = 40;
    
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
        // 1. Nạp các thông số vật lý & quy tắc game từ stats.json
        loadGameStatsConfig();

        // 2. Load cấu hình Map để tính toán chính xác canvasWidth, canvasHeight & Ma trận địa hình
        loadMapConfigFromFile("map_default");

        canvas = new Canvas(canvasWidth, canvasHeight);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane();
        root.getChildren().add(canvas);

        Button exitButton = new Button("THOÁT");
        exitButton.setStyle("-fx-background-color: #E74C3C; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
        StackPane.setAlignment(exitButton, Pos.TOP_RIGHT);
        StackPane.setMargin(exitButton, new Insets(10));
        exitButton.setOnAction(e -> handleExit());
        root.getChildren().add(exitButton);

        Scene scene = new Scene(root, canvasWidth, canvasHeight);

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

        scene.windowProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                newVal.focusedProperty().addListener((obsF, oldF, isFocused) -> {
                    if (!isFocused) activeKeys.clear();
                });
            }
        });

        canvas.setFocusTraversable(true);
        Platform.runLater(() -> canvas.requestFocus());

        initNetworkReceiver();
        requestTankPlayerInfo();
        
        startMatchTimer();
        startRenderLoop();
        
        return scene;
    }

    /**
     * Nạp các thông số vật lý và gameplay từ stats.json
     */
    private void loadGameStatsConfig() {
        JsonObject statsJson = AssetLoader.loadMapConfig("stats"); // Hoặc AssetLoader tương ứng
        System.out.println("[DEBUG] statsJson = " + statsJson);
        if (statsJson == null) return;

        if (statsJson.has("physics")) {
            JsonObject physics = statsJson.getAsJsonObject("physics");
            if (physics.has("tank_size")) this.tankSize = physics.get("tank_size").getAsDouble();
            if (physics.has("tile_size")) this.tileSize = physics.get("tile_size").getAsInt();
            if (physics.has("lerp_factor")) this.lerpFactor = physics.get("lerp_factor").getAsDouble();
        }

        if (statsJson.has("damage")) {
            JsonObject damage = statsJson.getAsJsonObject("damage");
            if (damage.has("brick_wall_max_hits")) this.brickWallMaxHits = damage.get("brick_wall_max_hits").getAsInt();
            if (damage.has("max_hp")) this.maxHp = damage.get("max_hp").getAsDouble();
        }
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
        if (mapJson == null) return;

        // 1. Đọc kích thước ô vuông và ma trận từ JSON
        if (mapJson.has("tile_size")) {
            this.tileSize = mapJson.get("tile_size").getAsInt();
        }

        if (mapJson.has("width") && mapJson.has("height")) {
            int cols = mapJson.get("width").getAsInt();
            int rows = mapJson.get("height").getAsInt();
            this.canvasWidth = cols * this.tileSize;
            this.canvasHeight = rows * this.tileSize;
        }

        // 2. Đọc Ma trận địa hình
        if (mapJson.has("matrix")) {
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
                        brickHitsLeft[r][c] = this.brickWallMaxHits; // Lấy từ JSON thay vì hardcode = 3
                    }
                }
            }
        }

        // 3. Đọc Spawn Points từ JSON (Khởi tạo vị trí ban đầu cho Tank)
        /*if (mapJson.has("spawn_points")) {
            JsonArray spawns = mapJson.getAsJsonArray("spawn_points");
            for (int i = 0; i < spawns.size(); i++) {
                JsonObject spawn = spawns.get(i).getAsJsonObject();
                int tId = spawn.get("tank_id").getAsInt();
                double spawnX = spawn.get("x").getAsDouble();
                double spawnY = spawn.get("y").getAsDouble();
                double angle = spawn.get("angle").getAsDouble();

                TankSnapshotDTO defaultTank = new TankSnapshotDTO(tId, spawnX, spawnY, angle, (int) this.maxHp, true);
                displayTanks.putIfAbsent(tId, defaultTank);
                targetTanks.putIfAbsent(tId, defaultTank);
                globalPlayerStates.putIfAbsent(tId, defaultTank);
            }
        }*/
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
            Set<Integer> activeTankIds = new HashSet<>();

            for (TankSnapshotDTO incoming : snapshot.getTanks()) {
                activeTankIds.add(incoming.getId());
                targetTanks.put(incoming.getId(), incoming);
                globalPlayerStates.put(incoming.getId(), incoming);

                if (displayTanks.containsKey(incoming.getId())) {
                    updateTankHpAndStatus(displayTanks.get(incoming.getId()), incoming.getHp(), incoming.isAlive());
                } else {
                    displayTanks.put(incoming.getId(), 
                        new TankSnapshotDTO(incoming.getId(), incoming.getX(), incoming.getY(), 
                                            incoming.getAngle(), incoming.getHp(), incoming.isAlive()));
                }
            }

            // Xóa toàn bộ tank dư thừa không nằm trong danh sách Server gửi
            displayTanks.keySet().removeIf(id -> !activeTankIds.contains(id));
            targetTanks.keySet().removeIf(id -> !activeTankIds.contains(id));
            globalPlayerStates.keySet().removeIf(id -> !activeTankIds.contains(id));

            if (myTankId == -1) {
                updateMyTankIdFromSession();
            }
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
        else if (packet.getType() == PacketType.MAP_UPDATE) {
            com.tank2d.common.dto.game.MapUpdateDTO mapUpdate = 
                    gson.fromJson(packet.getData(), com.tank2d.common.dto.game.MapUpdateDTO.class);
            
            if (mapUpdate != null && mapMatrix != null) {
                int r = mapUpdate.getRow();
                int c = mapUpdate.getCol();
                int newCode = mapUpdate.getNewTileCode();

                if (r >= 0 && r < mapMatrix.length && c >= 0 && c < mapMatrix[0].length) {
                    mapMatrix[r][c] = newCode;
                    brickHitsLeft[r][c] = 0;

                    double centerX = c * tileSize + tileSize / 2.0;
                    double centerY = r * tileSize + tileSize / 2.0;
                    explosions.add(new ExplosionEffect(centerX, centerY));
                }
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

    private void startRenderLoop() {
        renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                sendInputToServer();
                updateClientState();

                // 1. Render Nền phủ kín Canvas động
                Image bgImg = AssetLoader.getImage("tiles/background.png");
                if (bgImg != null && !bgImg.isError()) {
                    gc.drawImage(bgImg, 0, 0, canvasWidth, canvasHeight);
                } else {
                    gc.setFill(Color.rgb(22, 24, 29));
                    gc.fillRect(0, 0, canvasWidth, canvasHeight);
                }

                gc.setFill(Color.rgb(0, 0, 0, 0.2));
                gc.fillRect(0, 0, canvasWidth, canvasHeight);

                // 2. Render Ma trận tường
                renderTerrainAndWalls();

                // 3. Render Đạn, Xe & Hiệu ứng nổ
                renderBullets();
                renderTanks();
                renderExplosions();

                // 4. Render Bụi cỏ đè lên xe
                renderBushes();

                // 5. Render Bảng HUD & Đồng hồ
                renderHUD();
            }
        };
        renderTimer.start();
    }

    private void updateClientState() {
        for (Map.Entry<Integer, TankSnapshotDTO> entry : displayTanks.entrySet()) {
            int tankId = entry.getKey();
            TankSnapshotDTO current = entry.getValue();
            TankSnapshotDTO target = targetTanks.get(tankId);

            if (target != null) {
                double newX = current.getX() + (target.getX() - current.getX()) * lerpFactor; // Đọc động lerpFactor
                double newY = current.getY() + (target.getY() - current.getY()) * lerpFactor;
                double diffAngle = (target.getAngle() - current.getAngle() + 540) % 360 - 180;
                double newAngle = (current.getAngle() + diffAngle * lerpFactor + 360) % 360;

                current.setX(newX);
                current.setY(newY);
                current.setAngle(newAngle);
                
                updateTankHpAndStatus(current, target.getHp(), target.isAlive());
                
                if (globalPlayerStates.containsKey(tankId)) {
                    globalPlayerStates.get(tankId).setHp(target.getHp());
                }
            }
        }

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

                if (tileType == 0) continue;

                if (tileType == 1) { // Tường đá
                    if (stoneImg != null) gc.drawImage(stoneImg, x, y, tileSize, tileSize);
                    else { gc.setFill(Color.GRAY); gc.fillRect(x, y, tileSize, tileSize); }
                } else if (tileType == 2) { // Tường gạch
                    int hp = brickHitsLeft[r][c];

                    if (hp <= 0) {
                        mapMatrix[r][c] = 0;
                        continue;
                    }

                    if (brickImg != null) gc.drawImage(brickImg, x, y, tileSize, tileSize);
                    else { gc.setFill(Color.CHOCOLATE); gc.fillRect(x, y, tileSize, tileSize); }

                    gc.setStroke(Color.BLACK);
                    gc.setLineWidth(2);

                    // Tỷ lệ nứt dựa trên max HP đọc từ JSON
                    if (hp < brickWallMaxHits && hp > 1) {
                        gc.strokeLine(x + 8, y + 8, x + 24, y + 24);
                    } else if (hp == 1) {
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

        TankSnapshotDTO myTank = displayTanks.get(myTankId);

        for (int r = 0; r < mapMatrix.length; r++) {
            for (int c = 0; c < mapMatrix[r].length; c++) {
                if (mapMatrix[r][c] == 3) { // Ô bụi cỏ
                    double x = c * tileSize;
                    double y = r * tileSize;

                    gc.save();

                    if (myTank != null && myTank.isAlive()) {
                        double myC = Math.floor(myTank.getX() / tileSize);
                        double myR = Math.floor(myTank.getY() / tileSize);

                        if ((int) myC == c && (int) myR == r) {
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

        Color[] fallbackColors = {
            Color.web("#4CAF50"), // Green
            Color.web("#F44336"), // Red
            Color.web("#9C27B0"), // Purple
            Color.web("#FFC107")  // Yellow
        };

        for (TankSnapshotDTO tank : displayTanks.values()) {
            if (!tank.isAlive()) continue;

            boolean isMyTank = (tank.getId() == myTankId);
            boolean inBush = isTankInBush(tank.getX(), tank.getY());

            if (inBush && !isMyTank) {
                continue; 
            }

            int colorIdx = Math.abs(tank.getId() - 1) % tankImageAssets.length;
            Image tankSprite = AssetLoader.getImage(tankImageAssets[colorIdx]);

            gc.save(); 
            
            if (inBush && isMyTank) {
                gc.setGlobalAlpha(0.45);
            }

            // 1. Render Thân & Nòng pháo Xe Tank theo tankSize đọc từ JSON
            gc.translate(tank.getX(), tank.getY());
            gc.rotate(tank.getAngle() + 90.0);

            if (tankSprite != null && !tankSprite.isError()) {
                gc.drawImage(tankSprite, -tankSize / 2.0, -tankSize / 2.0, tankSize, tankSize);
            } else {
                Color bodyColor = fallbackColors[colorIdx];
                
                gc.setFill(Color.web("#333333"));
                gc.fillRect(-tankSize / 2.0 - 2, -tankSize / 2.0, 5, tankSize);
                gc.fillRect(tankSize / 2.0 - 3, -tankSize / 2.0, 5, tankSize);
                
                gc.setFill(bodyColor);
                gc.fillRect(-tankSize / 2.0 + 3, -tankSize / 2.0, tankSize - 6, tankSize);
                
                gc.setFill(Color.BLACK);
                gc.fillRect(-2.5, -tankSize / 2.0 - 10, 5, 12);
                gc.setFill(Color.web("#212121"));
                gc.fillOval(-7, -7, 14, 14);
            }
            gc.restore(); 

            // 2. Render Thanh Máu & Tên người chơi
            gc.save();
            if (inBush && isMyTank) {
                gc.setGlobalAlpha(0.5);
            }

            double barWidth = this.tankSize;
            double barHeight = 5.0;
            double barX = tank.getX() - barWidth / 2.0;
            double barY = tank.getY() - tankSize / 2.0 - 8.0;

            String username = getUsernameByTankId(tank.getId());
            if (username != null) {
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                double textWidth = username.length() * 6.5;
                gc.fillText(username, tank.getX() - textWidth / 2.0, barY - 6);
            }

            gc.setFill(Color.DARKRED);
            gc.fillRect(barX, barY, barWidth, barHeight);

            int currentHp = (int) Math.max(0, Math.min(maxHp, tank.getHp()));
            double hpWidth = (barWidth / maxHp) * currentHp;
            gc.setFill(Color.LIME);
            gc.fillRect(barX, barY, hpWidth, barHeight);

            gc.setStroke(Color.BLACK);
            gc.setLineWidth(1);
            gc.strokeRect(barX, barY, barWidth, barHeight);
            for (int i = 1; i < maxHp; i++) {
                double lineX = barX + (barWidth / maxHp) * i;
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

    // 1. Đồng hồ đếm ngược căn giữa Canvas động
    double clockX = (canvasWidth / 2.0) - 50;
    gc.setFill(Color.rgb(0, 0, 0, 0.35)); // Giảm độ đậm nền
    gc.fillRoundRect(clockX, 8, 100, 30, 8, 8);
    gc.setStroke(Color.rgb(255, 215, 0, 0.5));
    gc.setLineWidth(1.2);
    gc.strokeRoundRect(clockX, 8, 100, 30, 8, 8);

    gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
    gc.setFill(Color.rgb(255, 255, 0, 0.9));
    gc.fillText(String.format("⏱ %02d:%02d", matchRemainingTime / 60, matchRemainingTime % 60), clockX + 18, 28);

    // 2. Bảng điểm mini (Thu nhỏ & Mờ hơn)
    int boardWidth = 170;  // Giảm độ rộng từ 230 -> 170
    int boardHeight = 25 + (Math.max(1, tankPlayers.size()) * 16) + 10; // Tự điều chỉnh chiều cao theo số lượng player

    gc.setFill(Color.rgb(15, 18, 24, 0.35)); // Độ mờ nền giảm xuống 0.35 (trong suốt hơn)
    gc.fillRoundRect(8, 8, boardWidth, boardHeight, 6, 6);
    gc.setStroke(Color.rgb(58, 63, 77, 0.4));
    gc.strokeRoundRect(8, 8, boardWidth, boardHeight, 6, 6);

    // Tiêu đề bảng điểm
    gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
    gc.setFill(Color.rgb(255, 255, 255, 0.75));
    gc.fillText("BẢNG ĐIỂM", 14, 21);
    gc.setStroke(Color.rgb(128, 128, 128, 0.25));
    gc.strokeLine(14, 25, boardWidth - 6, 25);

    // Danh sách người chơi
    gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5)); // Chữ nhỏ lại
    int startY = 38;

    Color[] tankColors = {
        Color.rgb(76, 175, 80, 0.9),  // Tank 1 - Green
        Color.rgb(244, 67, 54, 0.9),  // Tank 2 - Red
        Color.rgb(156, 39, 176, 0.9), // Tank 3 - Purple
        Color.rgb(255, 193, 7, 0.9)   // Tank 4 - Yellow
    };

    if (tankPlayers.isEmpty()) {
        gc.setFill(Color.rgb(211, 211, 211, 0.5));
        gc.fillText("Đang chờ...", 14, startY);
    } else {
        for (TankPlayerDTO player : tankPlayers) {
            int tId = player.getTankId();
            String displayName = player.getUsername();

            if (tId == myTankId) {
                displayName += "*"; // Đổi (Tôi) thành dấu * để đỡ tốn diện tích
            }
            if (displayName.length() > 8) displayName = displayName.substring(0, 8);

            int currentHp = (int) maxHp;
            if (globalPlayerStates.containsKey(tId)) {
                currentHp = globalPlayerStates.get(tId).getHp();
            } else if (displayTanks.containsKey(tId)) {
                currentHp = displayTanks.get(tId).getHp();
            }

            int colorIdx = Math.abs(tId - 1) % tankColors.length;
            gc.setFill(tankColors[colorIdx]);

            gc.fillText(String.format("%-8s | HP:%d", displayName, currentHp), 14, startY);
            startY += 15; // Thu hẹp khoảng cách dòng
        }
    }

    gc.restore();
}
    
    private void updateMyTankIdFromSession() {
        com.tank2d.common.model.User currentUser = ClientSession.getInstance().getCurrentUser();
        
        if (currentUser != null && currentUser.getUsername() != null && !tankPlayers.isEmpty()) {
            String myUsername = currentUser.getUsername().trim();
            
            for (TankPlayerDTO player : tankPlayers) {
                if (player.getUsername() != null && myUsername.equalsIgnoreCase(player.getUsername().trim())) {
                    this.myTankId = player.getTankId();
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