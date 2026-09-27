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

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameCanvasApp extends Application {

    // --- CẤU HÌNH BẢN ĐỒ VÀ THÔNG SỐ VẬT LÝ ---
    private int canvasWidth;
    private int canvasHeight;
    private double tankSize;
    private double lerpFactor = 0.3;
    private int maxHp;
    private int brickWallMaxHits;
    private int tileSize;
    private int myTankId = -1;

    // --- JAVA FX & NETWORKING ---
    private StackPane rootPane;
    private Canvas canvas;
    private GraphicsContext gc;
    private Stage primaryStage;
    private RoomDTO currentRoom;
    private ClientSocket clientSocket;
    private AnimationTimer renderTimer;
    private Timeline matchTimer;
    private Consumer<Packet> packetListener;
    private final Gson gson = new Gson();

    // --- INPUT CONTROLS ---
    private final Set<KeyCode> activeKeys = new HashSet<>();
    private boolean spacePressed = false;

    // --- DỮ LIỆU ĐỒNG BỘ TỪ SERVER ---
    private final Map<Integer, TankSnapshotDTO> displayTanks = new ConcurrentHashMap<>();
    private final Map<Integer, TankSnapshotDTO> targetTanks = new ConcurrentHashMap<>();
    private final List<BulletSnapshotDTO> bullets = new CopyOnWriteArrayList<>();
    private final List<ExplosionEffect> explosions = new CopyOnWriteArrayList<>();
    private final List<TankPlayerDTO> tankPlayers = new CopyOnWriteArrayList<>();
    private final Map<Integer, TankSnapshotDTO> globalPlayerStates = new ConcurrentHashMap<>();
    private final List<ItemSnapshotDTO> items = new CopyOnWriteArrayList<>();

    // --- ĐỊA HÌNH VÀ THỜI GIAN TRẬN ĐẤU ---
    private int[][] mapMatrix;
    private int[][] brickHitsLeft;
    private int matchRemainingTime = 60;

    // Quản lý hiệu ứng vụ nổ
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

        // Nút Thoát nhỏ gọn dạng pill button bán trong suốt để không che khuất góc xe P2
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
                    if (!isFocused) activeKeys.clear();
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
        if (matchTimer != null) matchTimer.stop();
        matchTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (matchRemainingTime > 0) matchRemainingTime--;
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
        if (clientSocket == null || !clientSocket.isConnected()) return;
        try {
            clientSocket.sendPacket(new Packet(PacketType.TANK_PLAYER_INFO_REQ, ""));
        } catch (IOException ignored) {}
    }

    private void sendShootRequestToServer() {
        if (clientSocket == null || !clientSocket.isConnected()) return;
        try {
            clientSocket.sendPacket(new Packet(PacketType.PLAYER_SHOOT_REQ, "{}"));
            SoundManager.playSound("shoot_normal");
        } catch (IOException ignored) {}
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
                            displayTanks.put(incoming.getId(), new TankSnapshotDTO(
                                incoming.getId(), incoming.getX(), incoming.getY(),
                                incoming.getAngle(), incoming.getHp(), incoming.isAlive(),
                                incoming.isGhost(), incoming.hasShield(), incoming.hasNitro()
                            ));
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
            Type listType = new TypeToken<ArrayList<TankPlayerDTO>>(){}.getType();
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
                    explosions.add(new ExplosionEffect(effect.getX(), effect.getY()));
                    if ("EXPLOSION".equalsIgnoreCase(eventType)) {
                        SoundManager.playSound("explosion"); // Tiếng nổ xe
                    } else {
                        SoundManager.playSound("wall_break"); // Tiếng vỡ tường
                    }
                } else if (eventType.contains("WALL") || eventType.contains("BRICK") || eventType.contains("TILE")) {
                    int c = (int) (effect.getX() / tileSize);
                    int r = (int) (effect.getY() / tileSize);
                    if (mapMatrix != null && r >= 0 && r < mapMatrix.length && c >= 0 && c < mapMatrix[0].length) {
                        if (mapMatrix[r][c] == 2) {
                            brickHitsLeft[r][c]--;
                            if (brickHitsLeft[r][c] <= 0) mapMatrix[r][c] = 0;
                        }
                    }
                    explosions.add(new ExplosionEffect(effect.getX(), effect.getY()));
                    SoundManager.playSound("wall_break");
                } else if (eventType.startsWith("ITEM_PICKUP")) {
                    explosions.add(new ExplosionEffect(effect.getX(), effect.getY()));
                    String itemType = eventType.replace("ITEM_PICKUP_", "");
                    SoundManager.playItemSound(itemType);
                }
            }
        } else if (packet.getType() == PacketType.GAME_OVER_NOTIFY) {
            GameOverDTO gameOver = gson.fromJson(packet.getData(), GameOverDTO.class);
            Platform.runLater(() -> {
                stopAllTimers();
                showGameOverPopup(gameOver);
                SoundManager.stopBGM(); // Tắt nhạc nền
                SoundManager.playSound("game_over"); // Tiếng kết thúc
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
                    explosions.add(new ExplosionEffect(centerX, centerY));
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

                // 1. Render Nền
                Image bgImg = AssetLoader.getImage("tiles/background.png");
                if (bgImg != null && !bgImg.isError()) {
                    gc.drawImage(bgImg, 0, 0, canvasWidth, canvasHeight);
                } else {
                    gc.setFill(Color.rgb(22, 24, 29));
                    gc.fillRect(0, 0, canvasWidth, canvasHeight);
                }

                // 2. Render Tường & Items
                renderTerrainAndWalls();
                renderItems();

                // 3. Render Đạn, Xe & Vụ nổ
                renderBullets();
                renderTanks();
                renderExplosions();

                // 4. Render Bụi cỏ
                renderBushes();

                // 5. Render HUD tinh gọn
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
                double newX = current.getX() + (target.getX() - current.getX()) * lerpFactor;
                double newY = current.getY() + (target.getY() - current.getY()) * lerpFactor;
                double diffAngle = (target.getAngle() - current.getAngle() + 540) % 360 - 180;
                double newAngle = (current.getAngle() + diffAngle * lerpFactor + 360) % 360;

                current.setX(newX);
                current.setY(newY);
                current.setAngle(newAngle);

                updateTankHpAndStatus(current, target);
                if (globalPlayerStates.containsKey(tankId)) {
                    globalPlayerStates.get(tankId).setHp(target.getHp());
                }
            }
        }

        for (ExplosionEffect exp : explosions) exp.update();
        explosions.removeIf(exp -> exp.finished);
    }

    private void updateTankHpAndStatus(TankSnapshotDTO dto, TankSnapshotDTO target) {
        dto.setHp(target.getHp());
        dto.setGhost(target.isGhost());
        dto.setShield(target.hasShield());
        dto.setNitro(target.hasNitro());
    }

    private void renderTerrainAndWalls() {
        if (mapMatrix == null) return;
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
                    Image sprite = (hp == 1 && brickDamagedImg != null) ? brickDamagedImg :
                                   (hp <= 2 && brickCrackedImg != null) ? brickCrackedImg : brickImg;

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
        if (items.isEmpty()) return;
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
                    case "HEALTH_PACK" -> gc.setFill(Color.RED);
                    case "SHIELD" -> gc.setFill(Color.DODGERBLUE);
                    case "NITRO" -> gc.setFill(Color.ORANGE);
                    case "ROCKET_AMMO" -> gc.setFill(Color.PURPLE);
                    default -> gc.setFill(Color.WHITE);
                }
                gc.fillRect(x - itemSize / 2.0, y - itemSize / 2.0, itemSize, itemSize);
            }
            gc.restore();
        }
    }

    private void renderBushes() {
        if (mapMatrix == null) return;
        Image bushImg = AssetLoader.getImage("tiles/grass.png");
        TankSnapshotDTO myTank = displayTanks.get(myTankId);

        for (int r = 0; r < mapMatrix.length; r++) {
            for (int c = 0; c < mapMatrix[r].length; c++) {
                if (mapMatrix[r][c] == 3) {
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
                    if (bushImg != null && !bushImg.isError()) {
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
            if (!tank.isAlive()) continue;

            boolean isMyTank = (tank.getId() == myTankId);
            boolean inBush = isTankInBush(tank.getX(), tank.getY());

            if (inBush && !isMyTank && myTankId != -1) continue;

            int colorIdx = Math.abs(tank.getId() - 1) % tankImageAssets.length;
            Image tankSprite = AssetLoader.getImage(tankImageAssets[colorIdx]);

            gc.save();

            if (tank.isGhost()) {
                gc.setGlobalAlpha(0.5);
            } else if (inBush && isMyTank) {
                gc.setGlobalAlpha(0.55);
            }

            // Nitro effect
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

            // Thân xe
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

            // Hiệu ứng Khiên
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

            // Thanh Máu & Tên
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
        for (ExplosionEffect exp : explosions) exp.render(gc);
    }

    private void renderHUD() {
        gc.save();

        // 1. Đồng hồ đếm ngược tinh gọn giữa màn hình
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

        // 2. Bảng điểm mini bán trong suốt
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

                if (tId == myTankId) displayName += "*";
                if (displayName.length() > 7) displayName = displayName.substring(0, 7);

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
        if (rootPane == null) return;

        // Lớp phủ tối đè lên toàn bộ màn hình Game hiện tại (In-game Overlay)
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(10, 12, 18, 0.85);");
        overlay.setPrefSize(canvasWidth, canvasHeight);

        VBox card = new VBox(12);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20, 25, 20, 25));
        card.setMaxWidth(420);
        card.setStyle("-fx-background-color: #1A202C; -fx-background-radius: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 15, 0, 0, 5); -fx-border-color: #2D3748; -fx-border-width: 1px; -fx-border-radius: 12px;");

        Label title = new Label("🏆 KẾT THÚC TRẬN ĐẤU");
        title.setStyle("-fx-text-fill: #F6E05E; -fx-font-size: 20px; -fx-font-weight: bold;");

        String winnerUsername = getUsernameByTankId(gameOver.getWinnerTankId());
        String winnerText = "Người thắng: TANK " + gameOver.getWinnerTankId() 
                + (winnerUsername != null ? " (" + winnerUsername + ")" : "");
        Label winnerLabel = new Label(winnerText);
        winnerLabel.setStyle("-fx-text-fill: #E2E8F0; -fx-font-size: 14px; -fx-font-weight: bold;");

        GridPane table = new GridPane();
        table.setHgap(12);
        table.setVgap(8);
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

            Label rankLbl = new Label(rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : String.valueOf(rank));
            Label nameLbl = new Label(displayName);
            Label scoreLbl = new Label(String.valueOf(gameOver.getFinalScores().getOrDefault(tId, 0)));
            Label killLbl = new Label(String.valueOf(gameOver.getFinalKills().getOrDefault(tId, 0)));
            Label hitLbl = new Label(String.valueOf(gameOver.getFinalHits().getOrDefault(tId, 0)));

            Label[] row = {rankLbl, nameLbl, scoreLbl, killLbl, hitLbl};
            for (int i = 0; i < row.length; i++) {
                row[i].setStyle("-fx-text-fill: " + (tId == gameOver.getWinnerTankId() ? "#ECC94B" : "#EDF2F7") + "; -fx-font-size: 12px; -fx-font-weight: bold;");
                GridPane.setHalignment(row[i], i == 1 ? HPos.LEFT : HPos.CENTER);
                table.add(row[i], i, rank);
            }
            rank++;
        }

        Label reasonLabel = new Label("Lý do: " + gameOver.getReason());
        reasonLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px;");

        Button playAgainBtn = new Button("🔄 CHƠI TIẾP");
        playAgainBtn.setStyle("-fx-background-color: #38A169; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8px 16px; -fx-cursor: hand;");
        playAgainBtn.setOnAction(e -> handlePlayAgain());

        Button exitBtn = new Button("🚪 RỜI PHÒNG");
        exitBtn.setStyle("-fx-background-color: #E53E3E; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8px 16px; -fx-cursor: hand;");
        exitBtn.setOnAction(e -> handleExit());

        HBox btns = new HBox(15, playAgainBtn, exitBtn);
        btns.setAlignment(Pos.CENTER);

        card.getChildren().addAll(title, winnerLabel, table, reasonLabel, btns);
        overlay.getChildren().add(card);

        rootPane.getChildren().add(overlay);
    }

    private void handleExit() {
        SoundManager.stopBGM();
        clearGameState();
        try {
            if (clientSocket != null && clientSocket.isConnected()) {
                clientSocket.sendPacket(new Packet(PacketType.ROOM_LEAVE_REQ, ""));
            }
        } catch (IOException ignored) {}

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
                e.printStackTrace();
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
                e.printStackTrace();
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
        explosions.clear();
        items.clear();
        globalPlayerStates.clear();
        myTankId = -1;
        removeNetworkReceiver();
    }

    private void stopAllTimers() {
        if (renderTimer != null) renderTimer.stop();
        if (matchTimer != null) matchTimer.stop();
    }

    private boolean isTankInBush(double tankX, double tankY) {
        if (mapMatrix == null) return false;
        int c = (int) (tankX / tileSize);
        int r = (int) (tankY / tileSize);
        if (r >= 0 && r < mapMatrix.length && c >= 0 && c < mapMatrix[0].length) {
            return mapMatrix[r][c] == 3;
        }
        return false;
    }

    private String getUsernameByTankId(int tankId) {
        for (TankPlayerDTO player : tankPlayers) {
            if (player.getTankId() == tankId) return player.getUsername();
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