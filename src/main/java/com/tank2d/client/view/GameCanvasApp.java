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
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
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
import com.tank2d.common.dto.UserProfileDTO;
import javafx.scene.Cursor;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;

/**
 * Ứng dụng vẽ và điều khiển chiến trường trực tiếp thời gian thực bằng JavaFX
 * Canvas (Client Engine).
 * Chịu trách nhiệm kết xuất đồ họa (render loop 60 FPS), nội suy vị trí
 * (interpolation),
 * bắt sự kiện bàn phím gửi lên server, phát âm thanh và hiển thị bảng kết quả
 * ván đấu.
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
    private String currentTheme = "default";

    private int windowWidth;
    private int windowHeight;

    private StackPane rootPane;
    private Canvas canvas;
    private GraphicsContext gc;
    private Stage primaryStage;
    private RoomDTO currentRoom;
    private ClientSocket clientSocket;
    private AnimationTimer renderTimer;
    private Timeline matchTimer;
    private Label hudTimerLabel;
    private VBox hudScoreBox;
    private final Map<Integer, Label> hudPlayerHpLabels = new ConcurrentHashMap<>();
    private final Map<Integer, HBox> hudPlayerRows = new ConcurrentHashMap<>();
    private volatile boolean hudRowsInitialized = false;
    private long lastHudUpdate = 0;
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
    private final Set<Integer> knownBulletIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private int[][] mapMatrix;
    private int[][] brickHitsLeft;
    private int[][] bushClusterIds;
    private int matchRemainingTime = 60;

    private final List<ExplosionEffect> activeExplosions = new CopyOnWriteArrayList<>();
    private final List<ItemPickupEffect> itemPickupEffects = new CopyOnWriteArrayList<>();
    private final List<TankHitEffect> activeHitEffects = new CopyOnWriteArrayList<>();
    private final ExplosionPool explosionPool = new ExplosionPool(30);

    private StackPane activeModalOverlay = null;
    private boolean isProfileModalOpen = false;
    private VBox profileStatsCardContent = null;
    private UserProfileDTO latestUserProfile = null;
    private boolean showFpsOverlay = false;
    private long lastFpsTime = 0;
    private int frameCount = 0;
    private int currentFps = 60;

    /**
     * Lớp đại diện cho một hoạt cảnh vụ nổ đồ họa trên Canvas.
     */
    private static class ExplosionEffect {

        double x, y;
        int frame = 0;
        int frameTick = 0;
        int ticksPerFrame = 2;
        boolean finished = false;

        void init(double x, double y) {
            this.x = x;
            this.y = y;
            this.frame = 0;
            this.frameTick = 0;
            this.finished = false;
        }

        void update() {
            frameTick++;
            if (frameTick >= ticksPerFrame) {
                frameTick = 0;
                frame++;
                if (frame >= 5) {
                    finished = true;
                }
            }
        }

        void render(GraphicsContext gc) {
            if (finished) {
                return;
            }
            Image frameImg = AssetLoader.getExplosionFrame(frame);
            if (frameImg != null && !frameImg.isError()) {
                double size = 52.0;
                gc.drawImage(frameImg, x - size / 2.0, y - size / 2.0, size, size);
            } else {
                int radius = (frame + 1) * 6;
                gc.save();
                gc.setFill(Color.ORANGE);
                gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
                gc.setFill(Color.RED);
                gc.fillOval(x - (radius / 2.0), y - (radius / 2.0), radius, radius);
                gc.restore();
            }
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
            if (finished || alpha <= 0)
                return;
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
     * Bể chứa đối tượng vụ nổ (Object Pool) giúp tái sử dụng vùng nhớ, tránh GC
     * giật lag.
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
     * Lớp đại diện cho hiệu ứng tia lửa tóe ra, sóng xung kích và số sát thương khi
     * đạn bắn trúng xe tăng.
     */
    private static class TankHitEffect {
        double x, y;
        boolean isShield;
        int age = 0;
        int maxAge = 20;
        boolean finished = false;
        double ringRadius = 5.0;
        double ringMaxRadius = 26.0;
        List<SparkParticle> sparks = new ArrayList<>();
        double textYOffset = 0.0;

        static class SparkParticle {
            double px, py;
            double vx, vy;
            Color color;
            double size;
        }

        TankHitEffect(double x, double y, boolean isShield) {
            this.x = x;
            this.y = y;
            this.isShield = isShield;
            Random rnd = new Random();
            int count = isShield ? 10 : 14;
            Color[] colors = isShield
                    ? new Color[] { Color.web("#38bdf8"), Color.web("#06b6d4"), Color.web("#e0f2fe"), Color.WHITE }
                    : new Color[] { Color.web("#fef08a"), Color.web("#f59e0b"), Color.web("#ef4444"),
                            Color.web("#f97316"), Color.WHITE };

            for (int i = 0; i < count; i++) {
                SparkParticle sp = new SparkParticle();
                sp.px = x;
                sp.py = y;
                double angle = rnd.nextDouble() * 2 * Math.PI;
                double speed = 1.8 + rnd.nextDouble() * 3.6;
                sp.vx = Math.cos(angle) * speed;
                sp.vy = Math.sin(angle) * speed;
                sp.color = colors[rnd.nextInt(colors.length)];
                sp.size = 2.0 + rnd.nextDouble() * 2.5;
                sparks.add(sp);
            }
        }

        boolean update() {
            age++;
            if (age >= maxAge) {
                finished = true;
                return false;
            }
            double progress = (double) age / maxAge;
            ringRadius = 5.0 + (ringMaxRadius - 5.0) * progress;
            textYOffset -= 1.1;

            for (SparkParticle sp : sparks) {
                sp.px += sp.vx;
                sp.py += sp.vy;
                sp.vx *= 0.91;
                sp.vy *= 0.91;
            }
            return true;
        }

        void render(GraphicsContext gc) {
            if (finished)
                return;
            double progress = (double) age / maxAge;
            double alpha = Math.max(0.0, 1.0 - progress);

            gc.save();

            // 1. Vòng sóng va chạm kim loại / khiên năng lượng
            gc.setGlobalAlpha(alpha * 0.85);
            gc.setLineWidth(isShield ? 2.5 : 2.0);
            gc.setStroke(isShield ? Color.web("#38bdf8") : Color.web("#fbbf24"));
            gc.strokeOval(x - ringRadius, y - ringRadius, ringRadius * 2, ringRadius * 2);

            // 2. Các hạt tia lửa tóe ra
            for (SparkParticle sp : sparks) {
                gc.setGlobalAlpha(alpha);
                gc.setFill(sp.color);
                gc.fillOval(sp.px - sp.size / 2.0, sp.py - sp.size / 2.0, sp.size, sp.size);
            }

            // 3. Số chữ nổi thông báo trúng đạn / đỡ đạn
            gc.setGlobalAlpha(alpha);
            gc.setFont(Font.font("Kenney Future", FontWeight.BOLD, 11));
            if (isShield) {
                gc.setFill(Color.web("#38bdf8"));
                gc.fillText("DEFLECT!", x - 26, y - 16 + textYOffset);
            } else {
                gc.setFill(Color.web("#ef4444"));
                gc.fillText("-HP", x - 12, y - 14 + textYOffset);
            }

            gc.restore();
        }
    }

    private void addTankHitEffect(double x, double y, boolean isShield) {
        activeHitEffects.add(new TankHitEffect(x, y, isShield));
    }

    public void setStage(Stage stage) {
        this.primaryStage = stage;
        if (this.primaryStage != null) {
            this.primaryStage.setOnCloseRequest(event -> {
                try {
                    if (ClientSession.getInstance().getClientSocket() != null) {
                        ClientSession.getInstance().getClientSocket().close();
                    }
                } catch (Exception ignored) {
                }
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

        HBox mainLayout = new HBox();
        mainLayout.setPrefSize(windowWidth, windowHeight);
        mainLayout.setMinSize(windowWidth, windowHeight);
        mainLayout.setMaxSize(windowWidth, windowHeight);

        StackPane canvasWrapper = new StackPane();
        canvasWrapper.setPrefSize(canvasWidth, canvasHeight);
        canvasWrapper.setMinSize(canvasWidth, canvasHeight);
        canvasWrapper.setMaxSize(canvasWidth, canvasHeight);
        canvasWrapper.setStyle("-fx-background-color: #16181D;");
        canvasWrapper.getChildren().add(canvas);

        int hudWidth = Math.max(0, windowWidth - canvasWidth);
        VBox rightPanel = createRightHUDPanel(hudWidth, canvasHeight);

        mainLayout.getChildren().addAll(canvasWrapper, rightPanel);

        rootPane = new StackPane();
        rootPane.setPrefSize(windowWidth, windowHeight);
        rootPane.setMinSize(windowWidth, windowHeight);
        rootPane.setMaxSize(windowWidth, windowHeight);
        rootPane.setStyle("-fx-background-color: #0f172a;");
        rootPane.getChildren().add(mainLayout);

        Scene scene = new Scene(rootPane, windowWidth, windowHeight);

        scene.setOnKeyPressed(e -> {
            activeKeys.add(e.getCode());
            if (e.getCode() == KeyCode.SPACE && !spacePressed) {
                spacePressed = true;
                sendShootRequestToServer();
            }
            if (e.getCode() == KeyCode.ESCAPE) {
                if (activeModalOverlay != null) {
                    closeActiveModal();
                } else {
                    showSettingsModal();
                }
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
        });

        initNetworkReceiver();
        requestTankPlayerInfo();
        startMatchTimer();
        startRenderLoop();

        return scene;
    }

    private VBox createRightHUDPanel(int hudWidth, int hudHeight) {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16, 12, 16, 12));
        panel.setPrefWidth(hudWidth);
        panel.setMinWidth(hudWidth);
        panel.setMaxWidth(hudWidth);
        panel.setPrefHeight(hudHeight);
        panel.setMinHeight(hudHeight);
        panel.setMaxHeight(hudHeight);
        panel.setStyle(
                "-fx-background-color: #0b1120; -fx-border-color: rgba(56, 189, 248, 0.35); -fx-border-width: 0 0 0 2;");

        HBox topHudBar = new HBox(8);
        topHudBar.setAlignment(Pos.CENTER);
        Label hudTitle = new Label("BATTLEFIELD HUD");
        hudTitle.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 12px; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, rgba(56, 189, 248, 0.4), 8, 0.3, 0, 0);");
        HBox.setHgrow(hudTitle, Priority.ALWAYS);

        Button settingsHeaderBtn = new Button();
        settingsHeaderBtn.setCursor(Cursor.HAND);
        Image settingIconImg = AssetLoader.getSettingsIcon();
        if (settingIconImg != null && !settingIconImg.isError()) {
            ImageView setView = new ImageView(settingIconImg);
            setView.setFitWidth(18);
            setView.setFitHeight(18);
            settingsHeaderBtn.setGraphic(setView);
        } else {
            settingsHeaderBtn.setText("⚙");
        }
        settingsHeaderBtn.setStyle(
                "-fx-background-color: rgba(56, 189, 248, 0.15); -fx-border-color: rgba(56, 189, 248, 0.4); -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 2px 5px; -fx-cursor: hand;");
        settingsHeaderBtn.setOnAction(e -> showSettingsModal());
        Tooltip.install(settingsHeaderBtn, new Tooltip("Battle Settings (ESC)"));

        topHudBar.getChildren().addAll(hudTitle, settingsHeaderBtn);

        VBox timerCard = new VBox(4);
        timerCard.setAlignment(Pos.CENTER);
        timerCard.setPadding(new Insets(8, 10, 8, 10));
        timerCard.setStyle(
                "-fx-background-color: rgba(15, 23, 42, 0.95); -fx-background-radius: 10px; -fx-border-color: rgba(56, 189, 248, 0.3); -fx-border-radius: 10px; -fx-border-width: 1px;");

        Label timerCaption = new Label("TIME REMAINING");
        timerCaption.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 9px; -fx-font-weight: bold;");

        hudTimerLabel = new Label("⏱ 01:00");
        hudTimerLabel.setStyle(
                "-fx-font-family: 'Kenney Future', monospace; -fx-text-fill: #fbbf24; -fx-font-size: 17px; -fx-font-weight: bold;");

        timerCard.getChildren().addAll(timerCaption, hudTimerLabel);

        VBox scoreCard = new VBox(6);
        scoreCard.setPadding(new Insets(10, 8, 10, 8));
        scoreCard.setStyle(
                "-fx-background-color: rgba(15, 23, 42, 0.95); -fx-background-radius: 10px; -fx-border-color: rgba(56, 189, 248, 0.3); -fx-border-radius: 10px; -fx-border-width: 1px;");
        VBox.setVgrow(scoreCard, javafx.scene.layout.Priority.ALWAYS);

        HBox scoreHeaderBox = new HBox(4);
        scoreHeaderBox.setAlignment(Pos.CENTER_LEFT);
        Label scoreTitle = new Label("OPERATOR SQUAD");
        scoreTitle.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 9.5px; -fx-font-weight: bold;");
        Label hintLbl = new Label(" [ℹ VIEW STATS]");
        hintLbl.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 8px; -fx-font-weight: bold;");
        scoreHeaderBox.getChildren().addAll(scoreTitle, hintLbl);
        HBox.setHgrow(scoreTitle, Priority.ALWAYS);

        hudScoreBox = new VBox(6);
        hudScoreBox.setAlignment(Pos.TOP_LEFT);

        scoreCard.getChildren().addAll(scoreHeaderBox, hudScoreBox);
        rebuildHUDPlayerList();

        VBox guideCard = new VBox(4);
        guideCard.setPadding(new Insets(8, 10, 8, 10));
        guideCard.setStyle(
                "-fx-background-color: rgba(15, 23, 42, 0.85); -fx-background-radius: 10px; -fx-border-color: rgba(148, 163, 184, 0.2); -fx-border-radius: 10px; -fx-border-width: 1px;");
        Label guideTitle = new Label("CONTROLS");
        guideTitle.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 9px; -fx-font-weight: bold;");
        Label guideKeys = new Label("• MOVE: W A S D / ARROWS\n• SHOOT: SPACEBAR\n• QUIT: ESC KEY");
        guideKeys.setStyle(
                "-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 8.5px;");
        guideCard.getChildren().addAll(guideTitle, guideKeys);

        Button exitBtn = new Button("✕ LEAVE MATCH");
        exitBtn.setMaxWidth(Double.MAX_VALUE);
        exitBtn.setPrefHeight(38);
        exitBtn.setStyle(
                "-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_red_normal.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: white; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand;");
        exitBtn.setOnAction(e -> handleExit());

        panel.getChildren().addAll(topHudBar, timerCard, scoreCard, guideCard, exitBtn);
        return panel;
    }

    private void initGameConfigurations() {
        String mapId = (currentRoom != null && currentRoom.getMapId() != null) ? currentRoom.getMapId() : "map_default";
        ConfigLoader.loadMapConfig("config/maps/" + mapId + ".json");
        this.currentTheme = ConfigLoader.getMapTheme();

        this.tileSize = ConfigLoader.getTileSize();
        this.tankSize = ConfigLoader.getTankSize();
        this.maxHp = ConfigLoader.getMaxHp();
        this.brickWallMaxHits = ConfigLoader.getBrickWallMaxHits();

        int cols = ConfigLoader.getMapCols();
        int rows = ConfigLoader.getMapRows();
        this.canvasWidth = cols * this.tileSize;
        this.canvasHeight = rows * this.tileSize;
        this.windowWidth = ConfigLoader.getWindowWidth();
        this.windowHeight = ConfigLoader.getWindowHeight();

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
            if (hudTimerLabel != null) {
                hudTimerLabel.setText(String.format("⏱ %02d:%02d", matchRemainingTime / 60, matchRemainingTime % 60));
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
                    Set<Integer> currentBulletIds = new HashSet<>();
                    for (BulletSnapshotDTO b : snapshot.getBullets()) {
                        currentBulletIds.add(b.getId());
                        if (!knownBulletIds.contains(b.getId())) {
                            if ("ROCKET".equalsIgnoreCase(b.getType())) {
                                SoundManager.playSound("shoot_rocket");
                            } else {
                                SoundManager.playSound("shoot_normal");
                            }
                        }
                    }
                    knownBulletIds.retainAll(currentBulletIds);
                    knownBulletIds.addAll(currentBulletIds);

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
                                    incoming.isGhost(), incoming.hasShield(), incoming.hasNitro());
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
                rebuildHUDPlayerList();
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
                } else if ("TANK_HIT".equalsIgnoreCase(eventType)) {
                    addTankHitEffect(effect.getX(), effect.getY(), false);
                    SoundManager.playSound("tank_hit");
                } else if ("SHIELD_DEFLECT".equalsIgnoreCase(eventType)) {
                    addTankHitEffect(effect.getX(), effect.getY(), true);
                    SoundManager.playSound("tank_hit");
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
        } else if (packet.getType() == PacketType.USER_PROFILE_RES) {
            UserProfileDTO profile = gson.fromJson(packet.getData(), UserProfileDTO.class);
            if (profile != null) {
                this.latestUserProfile = profile;
                Platform.runLater(() -> {
                    if (activeModalOverlay != null && isProfileModalOpen && profileStatsCardContent != null) {
                        populateProfileStatsGrid(profileStatsCardContent, profile);
                    }
                });
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

                Image bgImg = AssetLoader.getBattlefieldBackground(currentTheme);
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
                renderHitEffects();
                renderItemPickupEffects();
                renderBushes();
                renderFpsOverlay(now);
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
                double newAngle = (InterpolationEngine.lerpAngle(current.getAngle(), target.getAngle(), lerpFactor)
                        + 360.0) % 360.0;

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

        for (int i = activeHitEffects.size() - 1; i >= 0; i--) {
            if (!activeHitEffects.get(i).update()) {
                activeHitEffects.remove(i);
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
        Image stoneImg = AssetLoader.getStoneWallImage(currentTheme);

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
                    Image sprite = AssetLoader.getBrickWallImage(hp, brickWallMaxHits, currentTheme);

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
        double itemSize = 28.0;

        for (ItemSnapshotDTO item : items) {
            double x = item.getX();
            double y = item.getY();
            String normalizedType = (item.getType() != null) ? item.getType().toUpperCase() : "";
            Image itemImg = AssetLoader.getPowerUpImage(normalizedType);

            gc.save();
            gc.setFill(Color.rgb(255, 255, 255, 0.28));
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
        Image bushImg = AssetLoader.getBushImage(currentTheme);

        Set<Integer> occupiedClusters = BushClusterUtil.findClustersWithTanks(
                bushClusterIds, displayTanks.values(), tileSize, 1);

        for (int r = 0; r < mapMatrix.length; r++) {
            for (int c = 0; c < mapMatrix[r].length; c++) {
                if (mapMatrix[r][c] == 3) {
                    double x = c * tileSize;
                    double y = r * tileSize;

                    gc.save();

                    int clusterId = (bushClusterIds != null && r < bushClusterIds.length
                            && c < bushClusterIds[r].length)
                                    ? bushClusterIds[r][c]
                                    : BushClusterUtil.NO_CLUSTER;

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
        Image normalBulletImg = AssetLoader.getImage("bullets/bullet_normal.png");

        for (BulletSnapshotDTO bullet : bullets) {
            boolean isMissile = "ROCKET".equalsIgnoreCase(bullet.getType());

            gc.save();
            gc.translate(bullet.getX(), bullet.getY());
            double angle = Math.toDegrees(Math.atan2(bullet.getVy(), bullet.getVx()));
            gc.rotate(angle + 90.0);

            if (isMissile) {
                if (missileImg != null && !missileImg.isError()) {
                    double targetHeight = 24.0;
                    double targetWidth = targetHeight * (missileImg.getWidth() / missileImg.getHeight());
                    gc.drawImage(missileImg, -targetWidth / 2.0, -targetHeight / 2.0, targetWidth, targetHeight);
                } else {
                    gc.setFill(Color.ORANGE);
                    gc.fillPolygon(new double[] { -5, 5, 0 }, new double[] { 10, 10, -10 }, 3);
                }
            } else {
                if (normalBulletImg != null && !normalBulletImg.isError()) {
                    double targetHeight = 16.0;
                    double targetWidth = targetHeight * (normalBulletImg.getWidth() / normalBulletImg.getHeight());
                    gc.drawImage(normalBulletImg, -targetWidth / 2.0, -targetHeight / 2.0, targetWidth, targetHeight);
                } else {
                    gc.setFill(Color.YELLOW);
                    gc.fillOval(-4, -4, 8, 8);
                }
            }
            gc.restore();
        }
    }

    private void renderTanks() {
        Color[] fallbackColors = {
                Color.web("#4CAF50"), Color.web("#F44336"),
                Color.web("#2196F3"), Color.web("#FFC107")
        };

        for (TankSnapshotDTO tank : displayTanks.values()) {
            if (!tank.isAlive() || tank.getHp() <= 0) {
                continue;
            }

            boolean isMyTank = (tank.getId() == myTankId);
            boolean inBush = isTankInBush(tank.getX(), tank.getY());

            int colorIdx = Math.abs(tank.getId() - 1);
            Image tankSprite = AssetLoader.getKenneyTankImage(colorIdx);

            gc.save();

            if (tank.isGhost()) {
                gc.setGlobalAlpha(0.5);
            } else if (inBush && isMyTank) {
                gc.setGlobalAlpha(0.55);
            }

            if (tank.hasNitro()) {
                Image nitroImg = AssetLoader.getItemEffectImage("NITRO");
                gc.save();
                gc.translate(tank.getX(), tank.getY());
                gc.rotate(tank.getAngle() - 90.0);
                if (nitroImg != null && !nitroImg.isError()) {
                    double nWidth = tankSize * 0.70;
                    double nHeight = nWidth * (nitroImg.getHeight() / nitroImg.getWidth());
                    gc.drawImage(nitroImg, -nWidth / 2.0, -tankSize / 2.0 - nHeight + 6, nWidth, nHeight);
                } else {
                    double flame = 12 + Math.random() * 8;
                    gc.setFill(Color.ORANGERED);
                    gc.fillPolygon(new double[] { -6, 0, 6 },
                            new double[] { -tankSize / 2.0, -tankSize / 2.0 - flame, -tankSize / 2.0 }, 3);
                    gc.setFill(Color.YELLOW);
                    gc.fillPolygon(new double[] { -3, 0, 3 },
                            new double[] { -tankSize / 2.0, -tankSize / 2.0 - flame * 0.6, -tankSize / 2.0 }, 3);
                }
                gc.restore();
            }

            gc.translate(tank.getX(), tank.getY());
            gc.rotate(tank.getAngle() - 90.0);

            if (tankSprite != null && !tankSprite.isError()) {
                gc.drawImage(tankSprite, -tankSize / 2.0, -tankSize / 2.0, tankSize, tankSize);
            } else {
                gc.setFill(fallbackColors[colorIdx]);
                gc.fillRect(-tankSize / 2.0 + 3, -tankSize / 2.0, tankSize - 6, tankSize);
                gc.setFill(Color.BLACK);
                gc.fillRect(-2.5, tankSize / 2.0, 5, 10);
            }
            gc.restore();

            if (tank.hasShield()) {
                Image shieldImg = AssetLoader.getItemEffectImage("SHIELD");
                gc.save();
                double radius = tankSize * 1.5;
                if (shieldImg != null && !shieldImg.isError()) {
                    gc.drawImage(shieldImg, tank.getX() - radius / 2.0, tank.getY() - radius / 2.0, radius, radius);
                } else {
                    gc.setStroke(Color.CYAN);
                    gc.setLineWidth(2.0);
                    gc.strokeOval(tank.getX() - radius / 2.0, tank.getY() - radius / 2.0, radius, radius);
                    gc.setFill(Color.rgb(0, 255, 255, 0.2));
                    gc.fillOval(tank.getX() - radius / 2.0, tank.getY() - radius / 2.0, radius, radius);
                }
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

    private void renderHitEffects() {
        for (TankHitEffect eff : activeHitEffects) {
            eff.render(gc);
        }
    }

    private void renderFpsOverlay(long now) {
        frameCount++;
        if (now - lastFpsTime >= 1_000_000_000L) {
            currentFps = frameCount;
            frameCount = 0;
            lastFpsTime = now;
        }

        if (showFpsOverlay) {
            gc.save();
            gc.setFill(Color.rgb(15, 23, 42, 0.75));
            gc.fillRoundRect(8, 8, 76, 22, 6, 6);
            gc.setStroke(Color.rgb(34, 197, 94, 0.6));
            gc.setLineWidth(1);
            gc.strokeRoundRect(8, 8, 76, 22, 6, 6);
            gc.setFont(Font.font("Kenney Future Narrow", FontWeight.BOLD, 10));
            gc.setFill(Color.web("#4ade80"));
            gc.fillText("FPS: " + currentFps, 16, 23);
            gc.restore();
        }
    }

    private void rebuildHUDPlayerList() {
        if (hudScoreBox == null) {
            return;
        }

        Platform.runLater(() -> {
            hudScoreBox.getChildren().clear();
            hudPlayerHpLabels.clear();
            hudPlayerRows.clear();

            String[] tankBadgeColors = { "#22c55e", "#ef4444", "#a855f7", "#eab308" };
            String[] tankBgColors = { "rgba(34,197,94,0.15)", "rgba(239,68,68,0.15)", "rgba(168,85,247,0.15)",
                    "rgba(234,179,8,0.15)" };

            List<TankPlayerDTO> list = new ArrayList<>(tankPlayers);
            // Nếu danh sách tankPlayers chưa có từ server nhưng người chơi đã vào trận, tạo sẵn chính mình
            if (list.isEmpty()) {
                String myName = (ClientSession.getInstance().getCurrentUser() != null)
                        ? ClientSession.getInstance().getCurrentUser().getUsername()
                        : "YOU";
                int myId = (myTankId > 0) ? myTankId : 1;
                list.add(new TankPlayerDTO(myId, myName));
            }

            for (TankPlayerDTO player : list) {
                int tId = player.getTankId();
                String displayName = player.getUsername();
                boolean isMe = (tId == myTankId || list.size() == 1);

                int colorIdx = Math.abs(tId - 1) % tankBadgeColors.length;

                HBox row = new HBox(6);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(5, 7, 5, 7));

                Label pBadge = new Label("P" + tId);
                pBadge.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: "
                        + tankBadgeColors[colorIdx] + "; -fx-font-weight: bold; -fx-font-size: 10px;");

                String nameText = displayName + (isMe ? " (YOU)" : "");
                if (nameText.length() > 10) {
                    nameText = nameText.substring(0, 10) + "..";
                }
                Label nameLbl = new Label(nameText);
                nameLbl.setStyle(
                        "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: "
                                + (isMe ? "#38bdf8" : "#f1f5f9") + "; -fx-font-weight: "
                                + (isMe ? "bold" : "normal") + "; -fx-font-size: 10px;");
                HBox.setHgrow(nameLbl, Priority.ALWAYS);

                int initHp = (int) maxHp;
                if (globalPlayerStates.containsKey(tId)) {
                    initHp = globalPlayerStates.get(tId).getHp();
                } else if (displayTanks.containsKey(tId)) {
                    initHp = displayTanks.get(tId).getHp();
                }

                Label hpLbl = new Label(initHp + " HP");
                hpLbl.setStyle("-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: "
                        + (initHp > 0 ? "#4ade80" : "#94a3b8") + "; -fx-font-weight: bold; -fx-font-size: 10px;");
                hudPlayerHpLabels.put(tId, hpLbl);

                if (isMe) {
                    row.setStyle(
                            "-fx-background-color: rgba(56,189,248,0.25); -fx-background-radius: 8px; -fx-border-color: #38bdf8; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;");
                    row.setOnMouseEntered(e -> row.setStyle(
                            "-fx-background-color: rgba(56,189,248,0.45); -fx-background-radius: 8px; -fx-border-color: #38bdf8; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;"));
                    row.setOnMouseExited(e -> row.setStyle(
                            "-fx-background-color: rgba(56,189,248,0.25); -fx-background-radius: 8px; -fx-border-color: #38bdf8; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;"));
                    row.setOnMouseClicked(e -> showPlayerProfileStatsModal());
                    Tooltip.install(row, new Tooltip("Click to view full combat career profile"));

                    Button rowSettingsBtn = new Button();
                    rowSettingsBtn.setCursor(Cursor.HAND);
                    Image setImg = AssetLoader.getSettingsIcon();
                    if (setImg != null && !setImg.isError()) {
                        ImageView setIcon = new ImageView(setImg);
                        setIcon.setFitWidth(16);
                        setIcon.setFitHeight(16);
                        rowSettingsBtn.setGraphic(setIcon);
                    } else {
                        rowSettingsBtn.setText("⚙");
                    }
                    rowSettingsBtn.setStyle(
                            "-fx-background-color: rgba(56, 189, 248, 0.3); -fx-border-color: rgba(56, 189, 248, 0.7); -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 2px 5px; -fx-cursor: hand;");
                    rowSettingsBtn.setOnAction(e -> {
                        e.consume();
                        showSettingsModal();
                    });
                    Tooltip.install(rowSettingsBtn, new Tooltip("Battle Settings (ESC)"));

                    row.getChildren().addAll(pBadge, nameLbl, hpLbl, rowSettingsBtn);
                } else {
                    row.setStyle("-fx-background-color: " + tankBgColors[colorIdx]
                            + "; -fx-background-radius: 8px; -fx-border-color: transparent; -fx-border-radius: 8px; -fx-border-width: 1px;");
                    row.getChildren().addAll(pBadge, nameLbl, hpLbl);
                }

                hudPlayerRows.put(tId, row);
                hudScoreBox.getChildren().add(row);
            }
            hudRowsInitialized = true;
        });
    }

    private void renderHUD() {
        if (hudScoreBox == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastHudUpdate < 150) {
            return;
        }
        lastHudUpdate = now;

        if (!hudRowsInitialized || hudPlayerRows.isEmpty()) {
            rebuildHUDPlayerList();
            return;
        }

        Platform.runLater(() -> {
            for (Map.Entry<Integer, Label> entry : hudPlayerHpLabels.entrySet()) {
                int tId = entry.getKey();
                Label hpLbl = entry.getValue();
                if (hpLbl == null) continue;

                int currentHp = (int) maxHp;
                if (globalPlayerStates.containsKey(tId)) {
                    currentHp = globalPlayerStates.get(tId).getHp();
                } else if (displayTanks.containsKey(tId)) {
                    currentHp = displayTanks.get(tId).getHp();
                }

                hpLbl.setText(currentHp + " HP");
                hpLbl.setStyle("-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: "
                        + (currentHp > 0 ? "#4ade80" : "#94a3b8") + "; -fx-font-weight: bold; -fx-font-size: 10px;");
            }
        });
    }

    private void closeActiveModal() {
        if (activeModalOverlay != null && rootPane != null) {
            rootPane.getChildren().remove(activeModalOverlay);
            activeModalOverlay = null;
            isProfileModalOpen = false;
        }
    }

    private void requestUserProfile() {
        if (clientSocket == null || !clientSocket.isConnected())
            return;
        try {
            int userId = (ClientSession.getInstance().getCurrentUser() != null)
                    ? ClientSession.getInstance().getCurrentUser().getId()
                    : -1;
            clientSocket.sendPacket(new Packet(PacketType.USER_PROFILE_REQ, String.valueOf(userId)));
        } catch (IOException ignored) {
        }
    }

    private void showPlayerProfileStatsModal() {
        closeActiveModal();
        if (rootPane == null)
            return;

        requestUserProfile();
        isProfileModalOpen = true;

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(6, 10, 20, 0.78);");
        overlay.setPrefSize(windowWidth, windowHeight);
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                closeActiveModal();
            }
        });

        VBox card = new VBox(14);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(22, 28, 22, 28));
        card.setMaxWidth(530);
        card.setStyle("-fx-background-color: rgba(13, 20, 36, 0.98); "
                + "-fx-background-radius: 18px; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.85), 32, 0.45, 0, 8); "
                + "-fx-border-color: rgba(56, 189, 248, 0.8); "
                + "-fx-border-width: 2px; "
                + "-fx-border-radius: 18px;");

        // Header: Avatar, Username, Rank Badge, Close button
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);

        int colorIdx = Math.abs(myTankId - 1);
        Image tankImg = AssetLoader.getKenneyTankImage(colorIdx);
        ImageView tankAvatar = new ImageView();
        tankAvatar.setFitWidth(44);
        tankAvatar.setFitHeight(44);
        tankAvatar.setPreserveRatio(true);
        if (tankImg != null) {
            tankAvatar.setImage(tankImg);
        }

        VBox userTitleBox = new VBox(3);
        String username = (ClientSession.getInstance().getCurrentUser() != null)
                ? ClientSession.getInstance().getCurrentUser().getUsername()
                : "PLAYER " + myTankId;
        Label nameLbl = new Label(username.toUpperCase());
        nameLbl.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 17px; -fx-font-weight: bold;");

        Label badgeLbl = new Label("OPERATOR TANK P" + (myTankId > 0 ? myTankId : "1") + " • COMBATANT PROFILE");
        badgeLbl.setStyle(
                "-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 10.5px; -fx-font-weight: bold;");
        userTitleBox.getChildren().addAll(nameLbl, badgeLbl);
        HBox.setHgrow(userTitleBox, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;"));
        closeBtn.setOnAction(e -> closeActiveModal());

        header.getChildren().addAll(tankAvatar, userTitleBox, closeBtn);

        // Live In-match Section
        VBox liveBox = new VBox(8);
        liveBox.setPadding(new Insets(10, 14, 10, 14));
        liveBox.setStyle(
                "-fx-background-color: rgba(30, 41, 59, 0.65); -fx-background-radius: 10px; -fx-border-color: rgba(56, 189, 248, 0.3); -fx-border-radius: 10px; -fx-border-width: 1px;");

        int currentHp = (int) maxHp;
        if (globalPlayerStates.containsKey(myTankId)) {
            currentHp = globalPlayerStates.get(myTankId).getHp();
        } else if (displayTanks.containsKey(myTankId)) {
            currentHp = displayTanks.get(myTankId).getHp();
        }

        HBox liveTitleRow = new HBox(6);
        liveTitleRow.setAlignment(Pos.CENTER_LEFT);
        Label liveDot = new Label("●");
        liveDot.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 12px;");
        Label liveTitle = new Label("LIVE ARENA COMBAT STATUS");
        liveTitle.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 10.5px; -fx-font-weight: bold;");
        liveTitleRow.getChildren().addAll(liveDot, liveTitle);

        HBox hpRow = new HBox(8);
        hpRow.setAlignment(Pos.CENTER_LEFT);
        Label hpCaption = new Label("ARMOR INTEGRITY (HP): " + currentHp + " / " + (int) maxHp);
        hpCaption.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #f1f5f9; -fx-font-size: 12px; -fx-font-weight: bold;");
        liveBox.getChildren().addAll(liveTitleRow, hpCaption);

        // Career stats grid container (dynamic)
        profileStatsCardContent = new VBox(10);
        populateProfileStatsGrid(profileStatsCardContent, latestUserProfile);

        // Resume / Close Button using asset
        Button resumeBtn = new Button("✕ CLOSE & RESUME BATTLE");
        resumeBtn.setMaxWidth(Double.MAX_VALUE);
        resumeBtn.setPrefHeight(42);
        resumeBtn.setStyle(
                "-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_blue_normal.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: white; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 12px; -fx-cursor: hand;");
        resumeBtn.setOnAction(e -> closeActiveModal());

        card.getChildren().addAll(header, liveBox, profileStatsCardContent, resumeBtn);
        overlay.getChildren().add(card);

        this.activeModalOverlay = overlay;
        rootPane.getChildren().add(overlay);
    }

    private void populateProfileStatsGrid(VBox container, UserProfileDTO profile) {
        container.getChildren().clear();

        Label careerTitle = new Label("🏆 CAREER COMBAT PERFORMANCE");
        careerTitle.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #fbbf24; -fx-font-size: 11px; -fx-font-weight: bold;");
        container.getChildren().add(careerTitle);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        String rankTier = (profile != null && profile.getRankTier() != null) ? profile.getRankTier().toUpperCase() : "BRONZE";
        int rankPos = (profile != null) ? profile.getRankPosition() : 1;
        int points = (profile != null) ? profile.getTotalPoints() : 0;
        int matches = (profile != null) ? profile.getTotalMatches() : 0;
        int wins = (profile != null) ? profile.getTotalWins() : 0;
        int kills = (profile != null) ? profile.getTotalKills() : 0;
        int hits = (profile != null) ? profile.getTotalHits() : 0;
        double winRate = (profile != null) ? profile.getWinRate() : 0.0;
        int losses = Math.max(0, matches - wins);

        VBox boxRank = createStatItem("RANK & LEADERBOARD", rankTier + " (TOP #" + rankPos + ")", "#fbbf24");
        VBox boxPoints = createStatItem("COMBAT MMR POINTS", points + " PTS", "#38bdf8");
        VBox boxMatches = createStatItem("TOTAL MATCHES", matches + " BATTLES", "#f1f5f9");
        VBox boxWinRate = createStatItem("VICTORY RATE", String.format("%.1f%%", winRate), "#4ade80");
        VBox boxWins = createStatItem("VICTORIES / DEFEATS", wins + " W - " + losses + " L", "#c084fc");
        VBox boxKills = createStatItem("TOTAL KILLS / HITS", kills + " K - " + hits + " HITS", "#f87171");

        grid.add(boxRank, 0, 0);
        grid.add(boxPoints, 1, 0);
        grid.add(boxMatches, 0, 1);
        grid.add(boxWinRate, 1, 1);
        grid.add(boxWins, 0, 2);
        grid.add(boxKills, 1, 2);

        container.getChildren().add(grid);
    }

    private VBox createStatItem(String caption, String value, String colorHex) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8, 14, 8, 14));
        box.setPrefWidth(228);
        box.setStyle(
                "-fx-background-color: rgba(30, 41, 59, 0.75); -fx-background-radius: 10px; -fx-border-color: rgba(148, 163, 184, 0.25); -fx-border-radius: 10px; -fx-border-width: 1.2px;");

        Label capLbl = new Label(caption);
        capLbl.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 8.5px; -fx-font-weight: bold;");

        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: " + colorHex
                + "; -fx-font-size: 14px; -fx-font-weight: bold;");

        box.getChildren().addAll(capLbl, valLbl);
        return box;
    }

    private void showSettingsModal() {
        closeActiveModal();
        if (rootPane == null)
            return;

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(6, 10, 20, 0.78);");
        overlay.setPrefSize(windowWidth, windowHeight);
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                closeActiveModal();
            }
        });

        VBox card = new VBox(14);
        card.setPadding(new Insets(20, 26, 20, 26));
        card.setMaxWidth(510);
        card.setStyle("-fx-background-color: rgba(15, 23, 42, 0.98); "
                + "-fx-background-radius: 18px; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.85), 32, 0.45, 0, 8); "
                + "-fx-border-color: rgba(245, 158, 11, 0.8); "
                + "-fx-border-width: 2px; "
                + "-fx-border-radius: 18px;");

        // Header: Settings Icon + Title + Close Button
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Image setIconImg = AssetLoader.getSettingsIcon();
        ImageView iconView = new ImageView();
        iconView.setFitWidth(28);
        iconView.setFitHeight(28);
        iconView.setPreserveRatio(true);
        if (setIconImg != null) {
            iconView.setImage(setIconImg);
        }

        Label titleLbl = new Label("GAMEPLAY SETTINGS");
        titleLbl.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #fbbf24; -fx-font-size: 15px; -fx-font-weight: bold;");
        HBox.setHgrow(titleLbl, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;"));
        closeBtn.setOnAction(e -> closeActiveModal());

        header.getChildren().addAll(iconView, titleLbl, closeBtn);

        // Group 1: Audio Controls
        VBox audioBox = new VBox(10);
        audioBox.setPadding(new Insets(12, 14, 12, 14));
        audioBox.setStyle(
                "-fx-background-color: rgba(30, 41, 59, 0.65); -fx-background-radius: 10px; -fx-border-color: rgba(56, 189, 248, 0.3); -fx-border-radius: 10px; -fx-border-width: 1px;");

        Label audioTitle = new Label("🔊 AUDIO & VOLUME PREFERENCES");
        audioTitle.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 10.5px; -fx-font-weight: bold;");

        // SFX Slider
        HBox sfxRow = new HBox(10);
        sfxRow.setAlignment(Pos.CENTER_LEFT);
        Label sfxLbl = new Label("SFX:");
        sfxLbl.setPrefWidth(50);
        sfxLbl.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 10px;");
        Slider sfxSlider = new Slider(0, 100, SoundManager.getSFXVolume() * 100);
        HBox.setHgrow(sfxSlider, Priority.ALWAYS);
        Label sfxVal = new Label((int) (SoundManager.getSFXVolume() * 100) + "%");
        sfxVal.setPrefWidth(45);
        sfxVal.setStyle(
                "-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #4ade80; -fx-font-size: 11px; -fx-font-weight: bold;");
        sfxSlider.valueProperty().addListener((obs, oldV, newV) -> {
            sfxVal.setText(newV.intValue() + "%");
            SoundManager.setSFXVolume((float) (newV.doubleValue() / 100.0));
        });
        sfxSlider.setOnMouseReleased(e -> SoundManager.playSound("tank_hit"));
        sfxRow.getChildren().addAll(sfxLbl, sfxSlider, sfxVal);

        // BGM Slider
        HBox bgmRow = new HBox(10);
        bgmRow.setAlignment(Pos.CENTER_LEFT);
        Label bgmLbl = new Label("BGM:");
        bgmLbl.setPrefWidth(50);
        bgmLbl.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 10px;");
        Slider bgmSlider = new Slider(0, 100, SoundManager.getBGMVolume() * 100);
        HBox.setHgrow(bgmSlider, Priority.ALWAYS);
        Label bgmVal = new Label((int) (SoundManager.getBGMVolume() * 100) + "%");
        bgmVal.setPrefWidth(45);
        bgmVal.setStyle(
                "-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #4ade80; -fx-font-size: 11px; -fx-font-weight: bold;");
        bgmSlider.valueProperty().addListener((obs, oldV, newV) -> {
            bgmVal.setText(newV.intValue() + "%");
            SoundManager.setBGMVolume((float) (newV.doubleValue() / 100.0));
        });
        bgmRow.getChildren().addAll(bgmLbl, bgmSlider, bgmVal);

        // Mute checkbox
        CheckBox muteCheck = new CheckBox("MUTE ALL SOUNDS (MASTER MUTE)");
        muteCheck.setSelected(SoundManager.isMuted());
        muteCheck.setStyle(
                "-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #f87171; -fx-font-size: 10.5px; -fx-cursor: hand;");
        muteCheck.setOnAction(e -> SoundManager.setMuted(muteCheck.isSelected()));

        audioBox.getChildren().addAll(audioTitle, sfxRow, bgmRow, muteCheck);

        // Group 2: Display & Overlay
        VBox displayBox = new VBox(8);
        displayBox.setPadding(new Insets(10, 14, 10, 14));
        displayBox.setStyle(
                "-fx-background-color: rgba(30, 41, 59, 0.65); -fx-background-radius: 10px; -fx-border-color: rgba(148, 163, 184, 0.25); -fx-border-radius: 10px; -fx-border-width: 1px;");
        Label displayTitle = new Label("🖥 DISPLAY & PERFORMANCE");
        displayTitle.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 10.5px; -fx-font-weight: bold;");

        CheckBox fpsCheck = new CheckBox("SHOW REAL-TIME FPS COUNTER ON ARENA");
        fpsCheck.setSelected(showFpsOverlay);
        fpsCheck.setStyle(
                "-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #e2e8f0; -fx-font-size: 10.5px; -fx-cursor: hand;");
        fpsCheck.setOnAction(e -> showFpsOverlay = fpsCheck.isSelected());
        displayBox.getChildren().addAll(displayTitle, fpsCheck);

        // Group 3: Keybinds guide
        VBox guideBox = new VBox(4);
        guideBox.setPadding(new Insets(10, 14, 10, 14));
        guideBox.setStyle("-fx-background-color: rgba(30, 41, 59, 0.5); -fx-background-radius: 10px;");
        Label guideTitle = new Label("⌨ COMBAT CONTROLS REFERENCE");
        guideTitle.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 9.5px; -fx-font-weight: bold;");
        Label key1 = new Label("• MOVE: W, A, S, D OR ARROW KEYS");
        key1.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 11px;");
        Label key2 = new Label("• FIRE CANNON: SPACEBAR OR LEFT MOUSE CLICK");
        key2.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 11px;");
        Label key3 = new Label("• QUICK SETTINGS: ESC KEY OR GEAR ICON");
        key3.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 11px;");
        guideBox.getChildren().addAll(guideTitle, key1, key2, key3);

        // Group 4: Action buttons using Asset buttons
        HBox actions = new HBox(12);
        Button surrenderBtn = new Button("🏳 SURRENDER / RETREAT");
        surrenderBtn.setPrefHeight(40);
        surrenderBtn.setStyle(
                "-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_red_normal.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: white; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand;");
        surrenderBtn.setOnAction(e -> {
            closeActiveModal();
            handleExit();
        });
        HBox.setHgrow(surrenderBtn, Priority.ALWAYS);
        surrenderBtn.setMaxWidth(Double.MAX_VALUE);

        Button resumeBtn = new Button("▶ RESUME COMBAT (ESC)");
        resumeBtn.setPrefHeight(40);
        resumeBtn.setStyle(
                "-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_green_normal.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: white; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand;");
        resumeBtn.setOnAction(e -> closeActiveModal());
        HBox.setHgrow(resumeBtn, Priority.ALWAYS);
        resumeBtn.setMaxWidth(Double.MAX_VALUE);

        actions.getChildren().addAll(surrenderBtn, resumeBtn);

        card.getChildren().addAll(header, audioBox, displayBox, guideBox, actions);
        overlay.getChildren().add(card);

        this.activeModalOverlay = overlay;
        rootPane.getChildren().add(overlay);
    }

    private void showGameOverPopup(GameOverDTO gameOver) {
        if (rootPane == null) {
            return;
        }

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(6, 10, 20, 0.52);");
        overlay.setPrefSize(windowWidth, windowHeight);

        VBox card = new VBox(12);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20, 28, 20, 28));
        card.setMaxWidth(520);
        card.setStyle("-fx-background-color: rgba(13, 20, 36, 0.55); "
                + "-fx-background-radius: 16px; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.85), 30, 0.4, 0, 8); "
                + "-fx-border-color: rgba(251, 191, 36, 0.75); "
                + "-fx-border-width: 2px; "
                + "-fx-border-radius: 16px;");

        int winnerId = gameOver.getWinnerTankId();

        // Authentic Graphic Logo (Victory / Defeat / Draw)
        ImageView bannerView = new ImageView();
        bannerView.setFitWidth(330);
        bannerView.setPreserveRatio(true);
        bannerView.setSmooth(true);
        if (winnerId < 0) {
            bannerView.setImage(AssetLoader.getDrawLogo());
        } else if (winnerId == myTankId) {
            bannerView.setImage(AssetLoader.getVictoryLogo());
        } else {
            bannerView.setImage(AssetLoader.getDefeatLogo());
        }

        // Winner description with Tank Preview Icon
        HBox winnerBox = new HBox(10);
        winnerBox.setAlignment(Pos.CENTER);
        winnerBox.setStyle(
                "-fx-background-color: rgba(234, 179, 8, 0.15); -fx-padding: 6px 16px; -fx-background-radius: 20px; -fx-border-color: rgba(234, 179, 8, 0.4); -fx-border-radius: 20px; -fx-border-width: 1px;");

        if (winnerId > 0) {
            int winnerColorIdx = Math.abs(winnerId - 1);
            Image winnerTankImg = AssetLoader.getKenneyTankImage(winnerColorIdx);
            if (winnerTankImg != null) {
                ImageView tankIcon = new ImageView(winnerTankImg);
                tankIcon.setFitWidth(28);
                tankIcon.setFitHeight(28);
                tankIcon.setPreserveRatio(true);
                winnerBox.getChildren().add(tankIcon);
            }
        }

        String winnerText;
        if (winnerId < 0) {
            winnerText = "DRAW - NO VICTOR";
        } else {
            String winnerUsername = getUsernameByTankId(winnerId);
            winnerText = "VICTOR: TANK " + winnerId + (winnerUsername != null ? " (" + winnerUsername + ")" : "");
        }

        Label winnerLabel = new Label(winnerText);
        winnerLabel.setStyle(
                "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #fbbf24; -fx-font-size: 12px; -fx-font-weight: bold;");
        winnerBox.getChildren().add(winnerLabel);

        GridPane table = new GridPane();
        table.setHgap(14);
        table.setVgap(8);
        table.setAlignment(Pos.CENTER);

        String[] headerTitles = { "RANK", "OPERATOR", "POINTS", "KILLS", "HITS" };
        for (int i = 0; i < headerTitles.length; i++) {
            Label h = new Label(headerTitles[i]);
            h.setStyle(
                    "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 10px; -fx-font-weight: bold;");
            table.add(h, i, 0);
        }

        List<Integer> tankIds = new ArrayList<>(gameOver.getFinalScores().keySet());
        tankIds.sort((a, b) -> Integer.compare(gameOver.getFinalScores().getOrDefault(b, 0),
                gameOver.getFinalScores().getOrDefault(a, 0)));

        int rank = 1;
        for (Integer tId : tankIds) {
            String uName = getUsernameByTankId(tId);
            String displayName = "P" + tId + (uName != null ? " - " + uName : "");

            HBox rankBox = new HBox(4);
            rankBox.setAlignment(Pos.CENTER_LEFT);
            Image medal = AssetLoader.getMedalImage(rank);
            if (medal != null) {
                ImageView medalView = new ImageView(medal);
                medalView.setFitWidth(20);
                medalView.setFitHeight(20);
                medalView.setPreserveRatio(true);
                rankBox.getChildren().add(medalView);
            }
            Label rankNum = new Label("#" + rank);
            boolean isWinner = (tId == gameOver.getWinnerTankId());
            String textColor = isWinner ? "#fbbf24" : "#f1f5f9";
            rankNum.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: " + textColor
                    + "; -fx-font-size: 11px; -fx-font-weight: bold;");
            rankBox.getChildren().add(rankNum);

            Label nameLbl = new Label(displayName);
            Label scoreLbl = new Label(String.valueOf(gameOver.getFinalScores().getOrDefault(tId, 0)));
            Label killLbl = new Label(String.valueOf(gameOver.getFinalKills().getOrDefault(tId, 0)));
            Label hitLbl = new Label(String.valueOf(gameOver.getFinalHits().getOrDefault(tId, 0)));

            Label[] rowLabels = { nameLbl, scoreLbl, killLbl, hitLbl };
            GridPane.setHalignment(rankBox, HPos.CENTER);
            table.add(rankBox, 0, rank);

            for (int i = 0; i < rowLabels.length; i++) {
                rowLabels[i].setStyle(
                        "-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: " + textColor
                                + "; -fx-font-size: 11px; -fx-font-weight: " + (isWinner ? "bold" : "normal") + ";");
                GridPane.setHalignment(rowLabels[i], i == 0 ? HPos.LEFT : HPos.CENTER);
                table.add(rowLabels[i], i + 1, rank);
            }
            rank++;
        }

        Label reasonLabel = new Label("REASON: " + gameOver.getReason());
        reasonLabel.setStyle(
                "-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 10px;");

        Button playAgainBtn = new Button("🔄 PLAY AGAIN");
        playAgainBtn.setStyle(
                "-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_green_normal.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: white; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 10px 22px; -fx-cursor: hand;");
        playAgainBtn.setOnAction(e -> handlePlayAgain());

        Button exitBtn = new Button("🚪 LEAVE ROOM");
        exitBtn.setStyle(
                "-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_red_normal.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: white; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 10px 22px; -fx-cursor: hand;");
        exitBtn.setOnAction(e -> handleExit());

        HBox btns = new HBox(16, playAgainBtn, exitBtn);
        btns.setAlignment(Pos.CENTER);

        card.getChildren().addAll(bannerView, winnerBox, table, reasonLabel, btns);
        overlay.getChildren().add(card);

        rootPane.getChildren().add(overlay);
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
                Parent root = loader.load();
                primaryStage.setMinWidth(0);
                primaryStage.setMinHeight(0);
                primaryStage.setMaxWidth(Double.MAX_VALUE);
                primaryStage.setMaxHeight(Double.MAX_VALUE);
                primaryStage.setScene(new Scene(root, windowWidth, windowHeight));
                primaryStage.setTitle("Tank 2D Online - Lobby");
                primaryStage.setResizable(false);
                primaryStage.sizeToScene();
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

                primaryStage.setMinWidth(0);
                primaryStage.setMinHeight(0);
                primaryStage.setMaxWidth(Double.MAX_VALUE);
                primaryStage.setMaxHeight(Double.MAX_VALUE);
                primaryStage.setScene(new Scene(root, windowWidth, windowHeight));
                primaryStage.setTitle("Tank 2D Online - Room");
                primaryStage.setResizable(false);
                primaryStage.sizeToScene();
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
        activeHitEffects.clear();
        closeActiveModal();
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
        try {
            primaryStage.getIcons()
                    .add(new Image(getClass().getResourceAsStream("/com/tank2d/client/view/tank-logo_1.png")));
        } catch (Exception ignored) {
        }
        primaryStage.setScene(createGameScene());
        primaryStage.setTitle("Tank 2D Online - Battlefield");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
