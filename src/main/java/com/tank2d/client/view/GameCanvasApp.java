package com.tank2d.client.view;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tank2d.client.ClientSession;
import com.tank2d.client.controller.RoomController;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.client.util.AssetLoader;
import com.tank2d.client.util.SoundManager;
import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.dto.game.*;
import com.tank2d.common.model.User;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;
import com.tank2d.client.util.BushClusterUtil;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.stage.Stage;
import javafx.util.Duration;

import com.tank2d.client.controller.InterpolationEngine;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.effect.DropShadow;

/**
 * Ứng dụng vẽ và điều khiển chiến trường trực tiếp thời gian thực bằng JavaFX Canvas (Client Engine).
 * Chịu trách nhiệm kết xuất đồ họa (render loop 60 FPS), nội suy vị trí (interpolation),
 * bắt sự kiện bàn phím gửi lên server, phát âm thanh và hiển thị bảng kết quả ván đấu.
 */
public class GameCanvasApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(GameCanvasApp.class.getName());

    private int canvasWidth;
    private int canvasHeight;
    private double tankSize;
    private double lerpFactor = 0.3;
    private int maxHp;
    private int brickWallMaxHits;
    private int tileSize;
    private int myTankId = -1;

    private StackPane rootPane;
    private Canvas canvas;
    private GraphicsContext gc;
    private Canvas fireworkCanvas;
    private GraphicsContext gcFirework;
    private Stage primaryStage;
    private RoomDTO currentRoom;
    private ClientSocket clientSocket;
    private AnimationTimer renderTimer;
    private AnimationTimer fireworkTimer;
    private Timeline matchTimer;
    private Consumer<Packet> packetListener;
    private final Gson gson = new Gson();

    private final Set<KeyCode> activeKeys = new HashSet<>();
    private boolean spacePressed = false;

    private final Map<Integer, TankSnapshotDTO> displayTanks = new ConcurrentHashMap<>();
    private final Map<Integer, TankSnapshotDTO> targetTanks = new ConcurrentHashMap<>();
    private final List<BulletSnapshotDTO> bullets = new CopyOnWriteArrayList<>();
    private final List<TankPlayerDTO> tankPlayers = new CopyOnWriteArrayList<>();
    private final Map<Integer, TankSnapshotDTO> globalPlayerStates = new ConcurrentHashMap<>();
    private final List<ItemSnapshotDTO> items = new CopyOnWriteArrayList<>();

    private int[][] mapMatrix;
    private int[][] brickHitsLeft;
    private int[][] bushClusterIds;
    private int matchRemainingTime = 60;

    private final List<ExplosionEffect> activeExplosions = new CopyOnWriteArrayList<>();
    private final List<ItemPickupEffect> itemPickupEffects = new CopyOnWriteArrayList<>();
    private final ExplosionPool explosionPool = new ExplosionPool(30);

    /**
     * Lớp đại diện cho một hoạt cảnh vụ nổ đồ họa trên Canvas.
     */
    private static class ExplosionEffect {

        double x, y;
        int radius = 6;
        int maxRadius = 24;
        boolean finished = false;

        void init(double x, double y) {
            this.x = x;
            this.y = y;
            this.radius = 6;
            this.finished = false;
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
            gc.save();
            gc.setFill(Color.ORANGE);
            gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            gc.setFill(Color.RED);
            gc.fillOval(x - (radius / 2.0), y - (radius / 2.0), radius, radius);
            gc.setFill(Color.YELLOW);
            gc.fillOval(x - (radius / 4.0), y - (radius / 4.0), radius / 2.0, radius / 2.0);
            gc.restore();
        }
    }

    /**
     * Lớp đại diện cho hiệu ứng lan tỏa và chữ nổi bay lên khi nhặt vật phẩm.
     */
    private static class ItemPickupEffect {

        double x, y;
        double radius = 10;
        double maxRadius = 36;
        double alpha = 1.0;
        Color color;
        String label;
        boolean finished = false;

        ItemPickupEffect(double x, double y, String itemType) {
            this.x = x;
            this.y = y;
            String type = itemType != null ? itemType.toUpperCase() : "";
            switch (type) {
                case "HEALTH_PACK" -> {
                    this.color = Color.rgb(34, 197, 94);
                    this.label = "+50 HP";
                }
                case "SHIELD" -> {
                    this.color = Color.rgb(6, 182, 212);
                    this.label = "SHIELD ON!";
                }
                case "NITRO" -> {
                    this.color = Color.rgb(249, 115, 22);
                    this.label = "NITRO BOOST!";
                }
                case "ROCKET_AMMO" -> {
                    this.color = Color.rgb(239, 68, 68);
                    this.label = "ROCKET READY!";
                }
                default -> {
                    this.color = Color.rgb(250, 204, 21);
                    this.label = "BUFF ACQUIRED!";
                }
            }
        }

        boolean update() {
            radius += 1.8;
            y -= 0.9;
            alpha -= 0.025;
            if (alpha <= 0 || radius > maxRadius) {
                finished = true;
            }
            return !finished;
        }

        void render(GraphicsContext gc) {
            if (finished || alpha <= 0) return;
            gc.save();
            gc.setGlobalAlpha(Math.max(0, Math.min(1.0, alpha)));

            gc.setStroke(color);
            gc.setLineWidth(2.5);
            gc.strokeOval(x - radius, y - radius + 15, radius * 2, radius * 2);

            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            gc.setFill(Color.BLACK);
            gc.fillText(label, x - (label.length() * 4.0) + 1, y - 10 + 1);
            gc.setFill(color);
            gc.fillText(label, x - (label.length() * 4.0), y - 10);

            gc.restore();
        }
    }

    /**
     * Bể chứa đối tượng vụ nổ (Object Pool) giúp tái sử dụng vùng nhớ, tránh GC giật lag.
     */
    private static class ExplosionPool {

        private final Queue<ExplosionEffect> pool = new ArrayDeque<>();

        public ExplosionPool(int initialCapacity) {
            for (int i = 0; i < initialCapacity; i++) {
                pool.add(new ExplosionEffect());
            }
        }

        public ExplosionEffect obtain(double x, double y) {
            ExplosionEffect effect = pool.poll();
            if (effect == null) {
                effect = new ExplosionEffect();
            }
            effect.init(x, y);
            return effect;
        }

        public void recycle(ExplosionEffect effect) {
            if (pool.size() < 50) {
                pool.offer(effect);
            }
        }
    }

    private void addExplosion(double x, double y) {
        ExplosionEffect exp = explosionPool.obtain(x, y);
        activeExplosions.add(exp);
    }

    private void addItemPickupEffect(double x, double y, String itemType) {
        itemPickupEffects.add(new ItemPickupEffect(x, y, itemType));
    }

    /**
     * Hạt pháo hoa chúc mừng Quán quân khi kết thúc trận đấu.
     */
    private static class FireworkParticle {

        double x, y, vx, vy, alpha, size;
        Color color;

        FireworkParticle(double x, double y, Color color) {
            this.x = x;
            this.y = y;
            double angle = Math.random() * Math.PI * 2;
            double speed = 2 + Math.random() * 5;
            this.vx = Math.cos(angle) * speed;
            this.vy = Math.sin(angle) * speed;
            this.alpha = 1.0;
            this.size = 3 + Math.random() * 3;
            this.color = color;
        }

        boolean update() {
            x += vx;
            y += vy;
            vy += 0.08;
            alpha -= 0.015;
            return alpha > 0;
        }

        void render(GraphicsContext gc) {
            gc.save();
            gc.setGlobalAlpha(Math.max(0, alpha));
            gc.setFill(color);
            gc.fillOval(x - size / 2, y - size / 2, size, size);
            gc.restore();
        }
    }

    private final List<FireworkParticle> fireworkParticles = new ArrayList<>();

    public void setStage(Stage stage) {
        this.primaryStage = stage;
        if (this.primaryStage != null) {
            this.primaryStage.setOnCloseRequest(event -> {
                try {
                    if (ClientSession.getInstance().getClientSocket() != null) {
                        ClientSession.getInstance().getClientSocket().close();
                    }
                } catch (Exception ignored) {}
                Platform.exit();
                System.exit(0);
            });
        }
    }

    public void setRoom(RoomDTO room) {
        this.currentRoom = room;
        if (room != null && room.getDuration() > 0) {
            this.matchRemainingTime = room.getDuration();
        }
    }

    public void setRemainingTime(int seconds) {
        if (seconds > 0) {
            this.matchRemainingTime = seconds;
        }
    }

    public void setMyTankId(int tankId) {
        this.myTankId = tankId;
    }

    public Scene createGameScene() {
        initGameConfigurations();
        clearGameState();
        SoundManager.init();
        SoundManager.playBGM();

        canvas = new Canvas(canvasWidth, canvasHeight);
        gc = canvas.getGraphicsContext2D();

        rootPane = new StackPane();
        rootPane.setStyle("-fx-background-color: #16181D;");
        rootPane.getChildren().add(canvas);
        StackPane.setAlignment(canvas, Pos.CENTER);

        Button exitButton = new Button("✕ Thoát");
        exitButton.setStyle("-fx-background-color: rgba(231, 76, 60, 0.75); -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 15px; -fx-padding: 3px 10px; -fx-cursor: hand;");
        exitButton.setOnMouseEntered(e -> exitButton.setStyle("-fx-background-color: rgba(231, 76, 60, 1.0); -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 15px; -fx-padding: 3px 10px; -fx-cursor: hand;"));
        exitButton.setOnMouseExited(e -> exitButton.setStyle("-fx-background-color: rgba(231, 76, 60, 0.75); -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 15px; -fx-padding: 3px 10px; -fx-cursor: hand;"));
        StackPane.setAlignment(exitButton, Pos.TOP_RIGHT);
        StackPane.setMargin(exitButton, new Insets(6, 6, 0, 0));
        exitButton.setOnAction(e -> handleExit());
        rootPane.getChildren().add(exitButton);

        Scene scene = new Scene(rootPane, canvasWidth, canvasHeight);

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
                    if (!isFocused) {
                        activeKeys.clear();
                    }
                });
            }
        });

        canvas.setFocusTraversable(true);
        Platform.runLater(() -> {
            canvas.requestFocus();
            if (primaryStage != null) {
                primaryStage.sizeToScene();
                primaryStage.centerOnScreen();
            }
        });

        initNetworkReceiver();
        requestTankPlayerInfo();
        startMatchTimer();
        startRenderLoop();

        return scene;
    }

    private void initGameConfigurations() {
        this.tileSize = ConfigLoader.getTileSize();
        this.tankSize = ConfigLoader.getTankSize();
        this.maxHp = ConfigLoader.getMaxHp();
        this.brickWallMaxHits = ConfigLoader.getBrickWallMaxHits();

        int cols = ConfigLoader.getMapCols();
        int rows = ConfigLoader.getMapRows();
        this.canvasWidth = cols * this.tileSize;
        this.canvasHeight = rows * this.tileSize;

        this.mapMatrix = ConfigLoader.getMapMatrix();
        this.bushClusterIds = BushClusterUtil.computeClusterIds(this.mapMatrix);
        int h = mapMatrix.length;
        int w = h > 0 ? mapMatrix[0].length : 0;

        this.brickHitsLeft = new int[h][w];
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                if (mapMatrix[r][c] == 2) {
                    brickHitsLeft[r][c] = this.brickWallMaxHits;
                }
            }
        }
    }

    private void startMatchTimer() {
        if (matchTimer != null) {
            matchTimer.stop();
        }
        matchTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (matchRemainingTime > 0) {
                matchRemainingTime--;
            }
        }));
        matchTimer.setCycleCount(Timeline.INDEFINITE);
        matchTimer.play();
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

    private void requestTankPlayerInfo() {
        if (clientSocket == null || !clientSocket.isConnected()) {
            return;
        }
        try {
            clientSocket.sendPacket(new Packet(PacketType.TANK_PLAYER_INFO_REQ, ""));
        } catch (IOException ignored) {
        }
    }

    private void sendShootRequestToServer() {
        if (clientSocket == null || !clientSocket.isConnected()) {
            return;
        }
        try {
            clientSocket.sendPacket(new Packet(PacketType.PLAYER_SHOOT_REQ, "{}"));
            SoundManager.playSound("shoot_normal");
        } catch (IOException ignored) {
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

    private void processIncomingPacket(Packet packet) {
        if (packet.getType() == PacketType.GAME_SNAPSHOT) {
            GameSnapshotDTO snapshot = gson.fromJson(packet.getData(), GameSnapshotDTO.class);
            if (snapshot != null) {
                if (snapshot.getItems() != null) {
                    this.items.clear();
                    this.items.addAll(snapshot.getItems());
                }
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
                            updateTankHpAndStatus(displayTanks.get(incoming.getId()), incoming);
                        } else {
                            TankSnapshotDTO newTank = new TankSnapshotDTO(
                                    incoming.getId(), incoming.getX(), incoming.getY(),
                                    incoming.getAngle(), incoming.getHp(), incoming.isAlive(),
                                    incoming.isGhost(), incoming.hasShield(), incoming.hasNitro()
                            );
                            newTank.setDisconnected(incoming.isDisconnected());
                            displayTanks.put(incoming.getId(), newTank);
                        }
                    }
                    displayTanks.keySet().removeIf(id -> !activeTankIds.contains(id));
                    targetTanks.keySet().removeIf(id -> !activeTankIds.contains(id));
                    globalPlayerStates.keySet().removeIf(id -> !activeTankIds.contains(id));

                    if (myTankId == -1) {
                        updateMyTankIdFromSession();
                    }
                }
            }
        } else if (packet.getType() == PacketType.TANK_PLAYER_INFO) {
            Type listType = new TypeToken<ArrayList<TankPlayerDTO>>() {
            }.getType();
            List<TankPlayerDTO> players = gson.fromJson(packet.getData(), listType);
            if (players != null) {
                tankPlayers.clear();
                tankPlayers.addAll(players);
                updateMyTankIdFromSession();
            }
        } else if (packet.getType() == PacketType.GAME_EVENT_EFFECT) {
            GameEventEffectDTO effect = gson.fromJson(packet.getData(), GameEventEffectDTO.class);
            if (effect != null) {
                String eventType = effect.getEventType() != null ? effect.getEventType().toUpperCase() : "";
                if ("EXPLOSION".equalsIgnoreCase(eventType) || "WALL_BREAK".equalsIgnoreCase(eventType)) {
                    addExplosion(effect.getX(), effect.getY());
                    if ("EXPLOSION".equalsIgnoreCase(eventType)) {
                        SoundManager.playSound("explosion");
                    } else {
                        SoundManager.playSound("wall_break");
                    }
                } else if (eventType.contains("WALL") || eventType.contains("BRICK") || eventType.contains("TILE")) {
                    int c = (int) (effect.getX() / tileSize);
                    int r = (int) (effect.getY() / tileSize);
                    if (mapMatrix != null && r >= 0 && r < mapMatrix.length && c >= 0 && c < mapMatrix[0].length) {
                        if (mapMatrix[r][c] == 2) {
                            brickHitsLeft[r][c]--;
                            if (brickHitsLeft[r][c] <= 0) {
                                mapMatrix[r][c] = 0;
                            }
                        }
                    }
                    addExplosion(effect.getX(), effect.getY());
                    SoundManager.playSound("wall_break");
                } else if (eventType.startsWith("ITEM_PICKUP")) {
                    String itemType = eventType.replace("ITEM_PICKUP_", "");
                    addItemPickupEffect(effect.getX(), effect.getY(), itemType);
                    addExplosion(effect.getX(), effect.getY());
                    SoundManager.playItemSound(itemType);
                }
            }
        } else if (packet.getType() == PacketType.GAME_OVER_NOTIFY) {
            GameOverDTO gameOver = gson.fromJson(packet.getData(), GameOverDTO.class);
            Platform.runLater(() -> {
                stopAllTimers();
                SoundManager.stopBGM();
                SoundManager.playSound("game_over");
                showGameOverPopup(gameOver);
            });
        } else if (packet.getType() == PacketType.MAP_UPDATE) {
            MapUpdateDTO mapUpdate = gson.fromJson(packet.getData(), MapUpdateDTO.class);
            if (mapUpdate != null && mapMatrix != null) {
                int r = mapUpdate.getRow();
                int c = mapUpdate.getCol();
                if (r >= 0 && r < mapMatrix.length && c >= 0 && c < mapMatrix[0].length) {
                    mapMatrix[r][c] = mapUpdate.getNewTileCode();
                    brickHitsLeft[r][c] = 0;
                    double centerX = c * tileSize + tileSize / 2.0;
                    double centerY = r * tileSize + tileSize / 2.0;
                    addExplosion(centerX, centerY);
                }
            }
        }
    }

    private void startRenderLoop() {
        renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                sendInputToServer();
                updateClientState();

                Image bgImg = AssetLoader.getImage("tiles/background.png");
                if (bgImg != null && !bgImg.isError()) {
                    gc.drawImage(bgImg, 0, 0, canvasWidth, canvasHeight);
                } else {
                    gc.setFill(Color.rgb(22, 24, 29));
                    gc.fillRect(0, 0, canvasWidth, canvasHeight);
                }
                renderTerrainAndWalls();
                renderItems();
                renderBullets();
                renderTanks();
                renderExplosions();
                renderItemPickupEffects();
                renderBushes();
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
                double newX = InterpolationEngine.lerp(current.getX(), target.getX(), lerpFactor);
                double newY = InterpolationEngine.lerp(current.getY(), target.getY(), lerpFactor);
                double newAngle = (InterpolationEngine.lerpAngle(current.getAngle(), target.getAngle(), lerpFactor) + 360.0) % 360.0;

                current.setX(newX);
                current.setY(newY);
                current.setAngle(newAngle);

                updateTankHpAndStatus(current, target);
                if (globalPlayerStates.containsKey(tankId)) {
                    globalPlayerStates.get(tankId).setHp(target.getHp());
                }
            }
        }

        for (int i = activeExplosions.size() - 1; i >= 0; i--) {
            ExplosionEffect exp = activeExplosions.get(i);
            exp.update();
            if (exp.finished) {
                activeExplosions.remove(i);
                explosionPool.recycle(exp);
            }
        }

        for (int i = itemPickupEffects.size() - 1; i >= 0; i--) {
            if (!itemPickupEffects.get(i).update()) {
                itemPickupEffects.remove(i);
            }
        }
    }

    private void updateTankHpAndStatus(TankSnapshotDTO dto, TankSnapshotDTO target) {
        dto.setHp(target.getHp());
        dto.setAlive(target.isAlive());
        dto.setGhost(target.isGhost());
        dto.setShield(target.hasShield());
        dto.setNitro(target.hasNitro());
        dto.setDisconnected(target.isDisconnected());
    }

    private void renderTerrainAndWalls() {
        if (mapMatrix == null) {
            return;
        }
        Image stoneImg = AssetLoader.getImage("tiles/stone_wall.png");
        Image brickImg = AssetLoader.getImage("tiles/brick_wall.png");
        Image brickCrackedImg = AssetLoader.getImage("tiles/brick_wall_cracked.png");
        Image brickDamagedImg = AssetLoader.getImage("tiles/brick_wall_damaged.png");

        for (int r = 0; r < mapMatrix.length; r++) {
            for (int c = 0; c < mapMatrix[r].length; c++) {
                int tileType = mapMatrix[r][c];
                double x = c * tileSize;
                double y = r * tileSize;

                if (tileType == 1) {
                    if (stoneImg != null && !stoneImg.isError()) {
                        gc.drawImage(stoneImg, x, y, tileSize, tileSize);
                    } else {
                        gc.setFill(Color.GRAY);
                        gc.fillRect(x, y, tileSize, tileSize);
                    }
                } else if (tileType == 2) {
                    int hp = brickHitsLeft[r][c];
                    if (hp <= 0) {
                        mapMatrix[r][c] = 0;
                        continue;
                    }
                    Image sprite = (hp == 1 && brickDamagedImg != null) ? brickDamagedImg
                            : (hp <= 2 && brickCrackedImg != null) ? brickCrackedImg : brickImg;

                    if (sprite != null && !sprite.isError()) {
                        gc.drawImage(sprite, x, y, tileSize, tileSize);
                    } else {
                        gc.setFill(Color.CHOCOLATE);
                        gc.fillRect(x, y, tileSize, tileSize);
                    }
                }
            }
        }
    }

    private void renderItems() {
        if (items.isEmpty()) {
            return;
        }
        double itemSize = 22.0;

        for (ItemSnapshotDTO item : items) {
            double x = item.getX();
            double y = item.getY();
            String normalizedType = (item.getType() != null) ? item.getType().toUpperCase() : "";
            Image itemImg = AssetLoader.getPowerUpImage(normalizedType);

            gc.save();
            gc.setFill(Color.rgb(255, 255, 255, 0.25));
            gc.fillOval(x - itemSize / 1.5, y - itemSize / 1.5, itemSize * 1.33, itemSize * 1.33);

            if (itemImg != null && !itemImg.isError()) {
                gc.drawImage(itemImg, x - itemSize / 2.0, y - itemSize / 2.0, itemSize, itemSize);
            } else {
                switch (normalizedType) {
                    case "HEALTH_PACK" ->
                        gc.setFill(Color.RED);
                    case "SHIELD" ->
                        gc.setFill(Color.DODGERBLUE);
                    case "NITRO" ->
                        gc.setFill(Color.ORANGE);
                    case "ROCKET_AMMO" ->
                        gc.setFill(Color.PURPLE);
                    default ->
                        gc.setFill(Color.WHITE);
                }
                gc.fillRect(x - itemSize / 2.0, y - itemSize / 2.0, itemSize, itemSize);
            }
            gc.restore();
        }
    }

    private void renderBushes() {
        if (mapMatrix == null) {
            return;
        }
        Image bushImg = AssetLoader.getImage("tiles/grass.png");

        Set<Integer> occupiedClusters = BushClusterUtil.findClustersWithTanks(
                bushClusterIds, displayTanks.values(), tileSize, 1);

        for (int r = 0; r < mapMatrix.length; r++) {
            for (int c = 0; c < mapMatrix[r].length; c++) {
                if (mapMatrix[r][c] == 3) {
                    double x = c * tileSize;
                    double y = r * tileSize;

                    gc.save();

                    int clusterId = (bushClusterIds != null && r < bushClusterIds.length && c < bushClusterIds[r].length)
                            ? bushClusterIds[r][c] : BushClusterUtil.NO_CLUSTER;

                    if (occupiedClusters.contains(clusterId)) {
                        gc.setGlobalAlpha(0.35);
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
        Image missileImg = AssetLoader.getImage("bullets/rocket.png");

        for (BulletSnapshotDTO bullet : bullets) {
            boolean isMissile = "ROCKET".equalsIgnoreCase(bullet.getType());

            if (isMissile) {
                gc.save();
                gc.translate(bullet.getX(), bullet.getY());
                double angle = Math.toDegrees(Math.atan2(bullet.getVy(), bullet.getVx()));
                gc.rotate(angle + 90.0);

                if (missileImg != null && !missileImg.isError()) {
                    double targetHeight = 22.0;
                    double targetWidth = targetHeight * (missileImg.getWidth() / missileImg.getHeight());
                    gc.drawImage(missileImg, -targetWidth / 2.0, -targetHeight / 2.0, targetWidth, targetHeight);
                } else {
                    gc.setFill(Color.ORANGE);
                    gc.fillPolygon(new double[]{-5, 5, 0}, new double[]{10, 10, -10}, 3);
                }
                gc.restore();
            } else {
                gc.setFill(Color.YELLOW);
                gc.fillOval(bullet.getX() - 4, bullet.getY() - 4, 8, 8);
            }
        }
    }

    private void renderTanks() {
        String[] tankImageAssets = {
            "tiles/green_tank.png", "tiles/red_tank.png",
            "tiles/purple_tank.png", "tiles/yellow_tank.png"
        };
        Color[] fallbackColors = {
            Color.web("#4CAF50"), Color.web("#F44336"),
            Color.web("#9C27B0"), Color.web("#FFC107")
        };

        for (TankSnapshotDTO tank : displayTanks.values()) {
            if (!tank.isAlive() || tank.getHp() <= 0) {
                continue;
            }

            boolean isMyTank = (tank.getId() == myTankId);
            boolean inBush = isTankInBush(tank.getX(), tank.getY());

            int colorIdx = Math.abs(tank.getId() - 1) % tankImageAssets.length;
            Image tankSprite = AssetLoader.getImage(tankImageAssets[colorIdx]);

            gc.save();

            if (tank.isGhost()) {
                gc.setGlobalAlpha(0.5);
            } else if (inBush && isMyTank) {
                gc.setGlobalAlpha(0.55);
            }

            if (tank.hasNitro()) {
                gc.save();
                gc.translate(tank.getX(), tank.getY());
                gc.rotate(tank.getAngle() + 90.0);
                double flame = 12 + Math.random() * 8;
                gc.setFill(Color.ORANGERED);
                gc.fillPolygon(new double[]{-6, 0, 6}, new double[]{tankSize / 2.0, tankSize / 2.0 + flame, tankSize / 2.0}, 3);
                gc.setFill(Color.YELLOW);
                gc.fillPolygon(new double[]{-3, 0, 3}, new double[]{tankSize / 2.0, tankSize / 2.0 + flame * 0.6, tankSize / 2.0}, 3);
                gc.restore();
            }

            gc.translate(tank.getX(), tank.getY());
            gc.rotate(tank.getAngle() + 90.0);

            if (tankSprite != null && !tankSprite.isError()) {
                gc.drawImage(tankSprite, -tankSize / 2.0, -tankSize / 2.0, tankSize, tankSize);
            } else {
                gc.setFill(fallbackColors[colorIdx]);
                gc.fillRect(-tankSize / 2.0 + 3, -tankSize / 2.0, tankSize - 6, tankSize);
                gc.setFill(Color.BLACK);
                gc.fillRect(-2.5, -tankSize / 2.0 - 8, 5, 10);
            }
            gc.restore();

            if (tank.hasShield()) {
                gc.save();
                double radius = tankSize * 1.5;
                gc.setStroke(Color.CYAN);
                gc.setLineWidth(2.0);
                gc.strokeOval(tank.getX() - radius / 2.0, tank.getY() - radius / 2.0, radius, radius);
                gc.setFill(Color.rgb(0, 255, 255, 0.2));
                gc.fillOval(tank.getX() - radius / 2.0, tank.getY() - radius / 2.0, radius, radius);
                gc.restore();
            }

            gc.save();
            double barWidth = this.tankSize;
            double barHeight = 4.0;
            double barX = tank.getX() - barWidth / 2.0;
            double barY = tank.getY() - tankSize / 2.0 - 7.0;

            String username = getUsernameByTankId(tank.getId());
            if (username != null) {
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
                gc.fillText(username, tank.getX() - (username.length() * 5.5) / 2.0, barY - 4);
            }

            if (tank.isDisconnected()) {
                gc.save();
                double iconX = tank.getX();
                double iconY = (username != null) ? (barY - 17.0) : (barY - 10.0);
                double badgeRadius = 6.0;

                gc.setFill(Color.web("#F59E0B"));
                gc.fillOval(iconX - badgeRadius, iconY - badgeRadius, badgeRadius * 2, badgeRadius * 2);
                gc.setStroke(Color.web("#92400E"));
                gc.setLineWidth(1.0);
                gc.strokeOval(iconX - badgeRadius, iconY - badgeRadius, badgeRadius * 2, badgeRadius * 2);

                gc.setFill(Color.web("#0F172A"));
                gc.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 9.0));
                gc.fillText("!", iconX - 2.0, iconY + 3.0);

                gc.restore();
            }

            gc.setFill(Color.DARKRED);
            gc.fillRect(barX, barY, barWidth, barHeight);

            int currentHp = Math.max(0, Math.min(maxHp, tank.getHp()));
            gc.setFill(Color.LIME);
            gc.fillRect(barX, barY, (barWidth / maxHp) * currentHp, barHeight);

            gc.setStroke(Color.BLACK);
            gc.setLineWidth(0.8);
            gc.strokeRect(barX, barY, barWidth, barHeight);
            gc.restore();
        }
    }

    private void renderExplosions() {
        for (ExplosionEffect exp : activeExplosions) {
            exp.render(gc);
        }
    }

    private void renderItemPickupEffects() {
        for (ItemPickupEffect effect : itemPickupEffects) {
            effect.render(gc);
        }
    }

    private void renderHUD() {
        gc.save();

        double clockWidth = 76;
        double clockHeight = 22;
        double clockX = (canvasWidth / 2.0) - (clockWidth / 2.0);
        gc.setFill(Color.rgb(0, 0, 0, 0.45));
        gc.fillRoundRect(clockX, 6, clockWidth, clockHeight, 6, 6);
        gc.setStroke(Color.rgb(255, 215, 0, 0.4));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(clockX, 6, clockWidth, clockHeight, 6, 6);

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        gc.setFill(Color.rgb(255, 235, 59, 0.95));
        gc.fillText(String.format("⏱ %02d:%02d", matchRemainingTime / 60, matchRemainingTime % 60), clockX + 11, 21);

        int boardWidth = 145;
        int rowHeight = 14;
        int boardHeight = 20 + (Math.max(1, tankPlayers.size()) * rowHeight) + 4;

        gc.setFill(Color.rgb(15, 18, 24, 0.45));
        gc.fillRoundRect(6, 6, boardWidth, boardHeight, 6, 6);
        gc.setStroke(Color.rgb(255, 255, 255, 0.15));
        gc.strokeRoundRect(6, 6, boardWidth, boardHeight, 6, 6);

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        gc.setFill(Color.rgb(200, 200, 200, 0.85));
        gc.fillText("DANH SÁCH CHIẾN ĐẤU", 10, 18);
        gc.setStroke(Color.rgb(255, 255, 255, 0.1));
        gc.strokeLine(10, 21, boardWidth + 2, 21);

        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10));
        int startY = 33;
        Color[] tankColors = {
            Color.rgb(76, 175, 80, 0.95), Color.rgb(244, 67, 54, 0.95),
            Color.rgb(156, 39, 176, 0.95), Color.rgb(255, 193, 7, 0.95)
        };

        if (tankPlayers.isEmpty()) {
            gc.setFill(Color.rgb(200, 200, 200, 0.5));
            gc.fillText("Đang nạp...", 10, startY);
        } else {
            for (TankPlayerDTO player : tankPlayers) {
                int tId = player.getTankId();
                String displayName = player.getUsername();

                if (tId == myTankId) {
                    displayName += "*";
                }
                if (displayName.length() > 7) {
                    displayName = displayName.substring(0, 7);
                }

                int currentHp = (int) maxHp;
                if (globalPlayerStates.containsKey(tId)) {
                    currentHp = globalPlayerStates.get(tId).getHp();
                } else if (displayTanks.containsKey(tId)) {
                    currentHp = displayTanks.get(tId).getHp();
                }

                int colorIdx = Math.abs(tId - 1) % tankColors.length;
                gc.setFill(tankColors[colorIdx]);
                gc.fillText(String.format("%-7s | HP:%d", displayName, currentHp), 10, startY);
                startY += rowHeight;
            }
        }

        gc.restore();
    }

    private void showGameOverPopup(GameOverDTO gameOver) {
        if (rootPane == null) {
            return;
        }

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(10, 12, 18, 0.88);");
        overlay.setPrefSize(canvasWidth, canvasHeight);

        fireworkCanvas = new Canvas(canvasWidth, canvasHeight);
        gcFirework = fireworkCanvas.getGraphicsContext2D();
        overlay.getChildren().add(fireworkCanvas);

        startFireworkAnimation();

        VBox card = new VBox(14);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(24, 30, 24, 30));
        card.setMaxWidth(460);
        card.setStyle("-fx-background-color: linear-gradient(to bottom, #1E2638, #111622); "
                + "-fx-background-radius: 16px; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 25, 0, 0, 8); "
                + "-fx-border-color: linear-gradient(to bottom, #F6E05E, #D69E2E); "
                + "-fx-border-width: 2px; "
                + "-fx-border-radius: 16px;");

        Label trophyIcon = new Label("🏆");
        trophyIcon.setStyle("-fx-font-size: 52px;");

        Label title = new Label("KẾT THÚC TRẬN ĐẤU");
        title.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 22));
        title.setStyle("-fx-text-fill: #F6E05E;");

        DropShadow textGlow = new DropShadow();
        textGlow.setColor(Color.web("#ECC94B"));
        textGlow.setRadius(12);
        textGlow.setSpread(0.4);
        title.setEffect(textGlow);

        int winnerId = gameOver.getWinnerTankId();
        String winnerText;
        if (winnerId < 0) {
            winnerText = "🤝 KHÔNG CÓ NGƯỜI CHIẾN THẮNG";
        } else {
            String winnerUsername = getUsernameByTankId(winnerId);
            winnerText = "👑 QUÁN QUÂN: TANK " + winnerId
                    + (winnerUsername != null ? " (" + winnerUsername + ")" : "");
        }

        Label winnerLabel = new Label(winnerText);
        winnerLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 15px; -fx-font-weight: bold; "
                + "-fx-background-color: rgba(236, 201, 75, 0.2); -fx-padding: 6px 16px; -fx-background-radius: 20px;");

        GridPane table = new GridPane();
        table.setHgap(14);
        table.setVgap(10);
        table.setAlignment(Pos.CENTER);

        String[] headerTitles = {"HẠNG", "NGƯỜI CHƠI", "ĐIỂM", "HẠ GỤC", "BẮN TRÚNG"};
        for (int i = 0; i < headerTitles.length; i++) {
            Label h = new Label(headerTitles[i]);
            h.setStyle("-fx-text-fill: #A0AEC0; -fx-font-size: 11px; -fx-font-weight: bold;");
            table.add(h, i, 0);
        }

        List<Integer> tankIds = new ArrayList<>(gameOver.getFinalScores().keySet());
        tankIds.sort((a, b) -> Integer.compare(gameOver.getFinalScores().getOrDefault(b, 0), gameOver.getFinalScores().getOrDefault(a, 0)));

        int rank = 1;
        for (Integer tId : tankIds) {
            String uName = getUsernameByTankId(tId);
            String displayName = "P" + tId + (uName != null ? " - " + uName : "");

            Label rankLbl = new Label(rank == 1 ? "🥇 Top 1" : rank == 2 ? "🥈 Top 2" : rank == 3 ? "🥉 Top 3" : "  " + rank);
            Label nameLbl = new Label(displayName);
            Label scoreLbl = new Label(String.valueOf(gameOver.getFinalScores().getOrDefault(tId, 0)));
            Label killLbl = new Label(String.valueOf(gameOver.getFinalKills().getOrDefault(tId, 0)));
            Label hitLbl = new Label(String.valueOf(gameOver.getFinalHits().getOrDefault(tId, 0)));

            boolean isWinner = (tId == gameOver.getWinnerTankId());
            String textColor = isWinner ? "#F6E05E" : "#EDF2F7";

            Label[] row = {rankLbl, nameLbl, scoreLbl, killLbl, hitLbl};
            for (int i = 0; i < row.length; i++) {
                row[i].setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 12px; -fx-font-weight: " + (isWinner ? "bold" : "normal") + ";");
                GridPane.setHalignment(row[i], i == 1 ? HPos.LEFT : HPos.CENTER);
                table.add(row[i], i, rank);
            }
            rank++;
        }

        Label reasonLabel = new Label("Lý do: " + gameOver.getReason());
        reasonLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-font-style: italic;");

        Button playAgainBtn = new Button("🔄 CHƠI TIẾP");
        playAgainBtn.setStyle("-fx-background-color: #38A169; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 18px; -fx-cursor: hand;");
        playAgainBtn.setOnAction(e -> handlePlayAgain());

        Button exitBtn = new Button("🚪 RỜI PHÒNG");
        exitBtn.setStyle("-fx-background-color: #E53E3E; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 18px; -fx-cursor: hand;");
        exitBtn.setOnAction(e -> handleExit());

        HBox btns = new HBox(16, playAgainBtn, exitBtn);
        btns.setAlignment(Pos.CENTER);

        card.getChildren().addAll(trophyIcon, title, winnerLabel, table, reasonLabel, btns);
        overlay.getChildren().add(card);

        rootPane.getChildren().add(overlay);
    }

    private void startFireworkAnimation() {
        Color[] fireworkColors = {Color.GOLD, Color.RED, Color.CYAN, Color.LIME, Color.MAGENTA, Color.ORANGE};

        fireworkTimer = new AnimationTimer() {
            private long lastSpawn = 0;

            @Override
            public void handle(long now) {
                if (gcFirework == null) {
                    return;
                }
                gcFirework.clearRect(0, 0, canvasWidth, canvasHeight);

                if (now - lastSpawn > 400_000_000L) {
                    double spawnX = Math.random() * canvasWidth;
                    double spawnY = Math.random() * (canvasHeight * 0.6);
                    Color color = fireworkColors[(int) (Math.random() * fireworkColors.length)];

                    for (int i = 0; i < 35; i++) {
                        fireworkParticles.add(new FireworkParticle(spawnX, spawnY, color));
                    }
                    lastSpawn = now;
                }

                for (int i = fireworkParticles.size() - 1; i >= 0; i--) {
                    FireworkParticle p = fireworkParticles.get(i);
                    if (!p.update()) {
                        fireworkParticles.remove(i);
                    } else {
                        p.render(gcFirework);
                    }
                }
            }
        };
        fireworkTimer.start();
    }

    private void handleExit() {
        SoundManager.stopBGM();
        clearGameState();
        removeNetworkReceiver();
        try {
            if (clientSocket != null && clientSocket.isConnected()) {
                clientSocket.sendPacket(new Packet(PacketType.ROOM_LEAVE_REQ, ""));
            }
        } catch (IOException ignored) {
        }

        this.currentRoom = null;

        Platform.runLater(() -> {
            try {
                if (primaryStage == null && canvas != null && canvas.getScene() != null) {
                    primaryStage = (Stage) canvas.getScene().getWindow();
                }
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tank2d/client/view/lobby.fxml"));
                primaryStage.setScene(new Scene(loader.load(), 800, 600));
                primaryStage.setTitle("Tank 2D Online - Lobby");
                primaryStage.centerOnScreen();
                primaryStage.show();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "[GameCanvasApp] Không thể quay lại Lobby", e);
            }
        });
    }

    private void handlePlayAgain() {
        clearGameState();
        Platform.runLater(() -> {
            try {
                if (primaryStage == null && canvas != null && canvas.getScene() != null) {
                    primaryStage = (Stage) canvas.getScene().getWindow();
                }
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tank2d/client/view/room.fxml"));
                Parent root = loader.load();
                RoomController controller = loader.getController();

                if (currentRoom != null) {
                    currentRoom.setStatus("Waiting");
                    controller.setRoom(currentRoom);
                    controller.resetReadyForReplay();
                }

                primaryStage.setScene(new Scene(root, 800, 600));
                primaryStage.setTitle("Tank 2D Online - Room");
                primaryStage.centerOnScreen();
                primaryStage.show();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "[GameCanvasApp] Không thể quay lại Room", e);
            }
        });
    }

    private void clearGameState() {
        SoundManager.stopBGM();
        stopAllTimers();
        activeKeys.clear();
        spacePressed = false;
        displayTanks.clear();
        targetTanks.clear();
        bullets.clear();
        activeExplosions.clear();
        itemPickupEffects.clear();
        items.clear();
        globalPlayerStates.clear();
        myTankId = -1;
        removeNetworkReceiver();
    }

    private void stopAllTimers() {
        if (renderTimer != null) {
            renderTimer.stop();
        }
        if (matchTimer != null) {
            matchTimer.stop();
        }
        if (fireworkTimer != null) {
            fireworkTimer.stop();
        }
    }

    private boolean isTankInBush(double tankX, double tankY) {
        if (mapMatrix == null) {
            return false;
        }
        int c = (int) (tankX / tileSize);
        int r = (int) (tankY / tileSize);
        if (r >= 0 && r < mapMatrix.length && c >= 0 && c < mapMatrix[0].length) {
            return mapMatrix[r][c] == 3;
        }
        return false;
    }

    private String getUsernameByTankId(int tankId) {
        for (TankPlayerDTO player : tankPlayers) {
            if (player.getTankId() == tankId) {
                return player.getUsername();
            }
        }
        return null;
    }

    private void updateMyTankIdFromSession() {
        User currentUser = ClientSession.getInstance().getCurrentUser();
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
