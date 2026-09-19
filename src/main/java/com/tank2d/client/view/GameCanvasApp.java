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
import com.tank2d.common.dto.game.GameOverDTO;
import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.dto.game.TankPlayerDTO;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameCanvasApp extends Application {

    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 600;
    private static final double TANK_SIZE = 36.0;
//    private static final double BULLET_SIZE = 8.0;
    private static final double LERP_FACTOR = 0.3;
    private static final double MAX_HP = 3.0;
    // --- DỮ LIỆU MAP ---
private int[][] mapMatrix;
private int tileSize = 40;
private int[][] brickHitsLeft;

// --- THỜI GIAN TRẬN ---
private int matchRemainingTime = 0;

    private Canvas canvas;
    private RoomDTO currentRoom;
    private GraphicsContext gc;
    private final Gson gson = new Gson();
    private ClientSocket clientSocket;
    private Stage primaryStage;
    private AnimationTimer renderTimer;

    private List<TankPlayerDTO> tankPlayers = new ArrayList<>();
    private final Set<KeyCode> activeKeys = new HashSet<>();
    private boolean spacePressed = false;

    // --- DỮ LIỆU ĐỒNG BỘ TỪ SERVER ---
    private final Map<Integer, TankSnapshotDTO> displayTanks = new ConcurrentHashMap<>();
    private final Map<Integer, TankSnapshotDTO> targetTanks = new ConcurrentHashMap<>();
    private final List<BulletSnapshotDTO> bullets = new CopyOnWriteArrayList<>();
    private final List<ExplosionEffect> explosions = new CopyOnWriteArrayList<>();

    private void showGameOverPopup(GameOverDTO gameOver) {

        Stage popup = new Stage();
        popup.initOwner(primaryStage);
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.setTitle("Tank 2D - Kết quả");

        // =========================
        // TITLE
        // =========================
        Label title = new Label("🏆  KẾT THÚC VÁN ĐẤU");
        title.setStyle(
                "-fx-text-fill: #FFD700;"
                + "-fx-font-size: 26px;"
                + "-fx-font-weight: bold;"
        );

        String winnerUsername
                = getUsernameByTankId(gameOver.getWinnerTankId());

        String winnerText
                = "🏆  Người chiến thắng: TANK "
                + gameOver.getWinnerTankId();

        if (winnerUsername != null && !winnerUsername.isEmpty()) {
            winnerText += " - " + winnerUsername;
        }

        Label winnerLabel = new Label(winnerText);

        winnerLabel.setStyle(
                "-fx-text-fill: white;"
                + "-fx-font-size: 18px;"
                + "-fx-font-weight: bold;"
        );

        // =========================
        // HEADER
        // =========================
        Label rankHeader = new Label("HẠNG");
        Label tankHeader = new Label("TANK");
        Label scoreHeader = new Label("SCORE");
        Label killHeader = new Label("KILL");
        Label hitHeader = new Label("HIT");

        Label[] headers = {
            rankHeader, tankHeader, scoreHeader, killHeader, hitHeader
        };

        for (Label label : headers) {
            label.setStyle(
                    "-fx-text-fill: #AAAAAA;"
                    + "-fx-font-size: 13px;"
                    + "-fx-font-weight: bold;"
            );
        }

        javafx.scene.layout.GridPane table
                = new javafx.scene.layout.GridPane();

        table.setHgap(20);
        table.setVgap(12);
        table.setAlignment(javafx.geometry.Pos.CENTER);

        table.add(rankHeader, 0, 0);
        table.add(tankHeader, 1, 0);
        table.add(scoreHeader, 2, 0);
        table.add(killHeader, 3, 0);
        table.add(hitHeader, 4, 0);

        table.getColumnConstraints().clear();

        javafx.scene.layout.ColumnConstraints col1
                = new javafx.scene.layout.ColumnConstraints();
        col1.setPrefWidth(60);

        javafx.scene.layout.ColumnConstraints col2
                = new javafx.scene.layout.ColumnConstraints();
        col2.setPrefWidth(180);

        javafx.scene.layout.ColumnConstraints col3
                = new javafx.scene.layout.ColumnConstraints();
        col3.setPrefWidth(80);

        javafx.scene.layout.ColumnConstraints col4
                = new javafx.scene.layout.ColumnConstraints();
        col4.setPrefWidth(70);

        javafx.scene.layout.ColumnConstraints col5
                = new javafx.scene.layout.ColumnConstraints();
        col5.setPrefWidth(70);

        table.getColumnConstraints().addAll(
                col1, col2, col3, col4, col5
        );

        // =========================
        // SORT RANKING
        // =========================
        java.util.List<Integer> tankIds
                = new java.util.ArrayList<>(gameOver.getFinalScores().keySet());

        tankIds.sort((a, b) -> {

            int scoreA = gameOver.getFinalScores().getOrDefault(a, 0);
            int scoreB = gameOver.getFinalScores().getOrDefault(b, 0);

            if (scoreA != scoreB) {
                return Integer.compare(scoreB, scoreA);
            }

            int killA = gameOver.getFinalKills().getOrDefault(a, 0);
            int killB = gameOver.getFinalKills().getOrDefault(b, 0);

            if (killA != killB) {
                return Integer.compare(killB, killA);
            }

            int hitA = gameOver.getFinalHits().getOrDefault(a, 0);
            int hitB = gameOver.getFinalHits().getOrDefault(b, 0);

            return Integer.compare(hitB, hitA);
        });

        // =========================
        // CREATE ROWS
        // =========================
        int rank = 1;

        for (Integer tankId : tankIds) {

            int score = gameOver.getFinalScores()
                    .getOrDefault(tankId, 0);

            int kills = gameOver.getFinalKills()
                    .getOrDefault(tankId, 0);

            int hits = gameOver.getFinalHits()
                    .getOrDefault(tankId, 0);

            Label rankLabel = new Label(
                    rank == 1 ? "🥇"
                            : rank == 2 ? "🥈"
                                    : rank == 3 ? "🥉"
                                            : String.valueOf(rank)
            );

            String username = getUsernameByTankId(tankId);

            String tankName = "TANK " + tankId;

            if (username != null && !username.isEmpty()) {
                tankName += " - " + username;
            }

            Label tankLabel = new Label(tankName);
            Label scoreLabel = new Label(String.valueOf(score));
            Label killLabel = new Label(String.valueOf(kills));
            Label hitLabel = new Label(String.valueOf(hits));

            Label[] row = {
                rankLabel,
                tankLabel,
                scoreLabel,
                killLabel,
                hitLabel
            };

            GridPane.setHalignment(rankLabel, javafx.geometry.HPos.CENTER);
            GridPane.setHalignment(tankLabel, javafx.geometry.HPos.LEFT);
            GridPane.setHalignment(scoreLabel, javafx.geometry.HPos.CENTER);
            GridPane.setHalignment(killLabel, javafx.geometry.HPos.CENTER);
            GridPane.setHalignment(hitLabel, javafx.geometry.HPos.CENTER);

            for (Label label : row) {
                label.setStyle(
                        "-fx-text-fill: white;"
                        + "-fx-font-size: 15px;"
                );
            }

            // Highlight người thắng
            if (tankId == gameOver.getWinnerTankId()) {

                for (Label label : row) {
                    label.setStyle(
                            "-fx-text-fill: #FFD700;"
                            + "-fx-font-size: 16px;"
                            + "-fx-font-weight: bold;"
                    );
                }
            }

            table.add(rankLabel, 0, rank);
            table.add(tankLabel, 1, rank);
            table.add(scoreLabel, 2, rank);
            table.add(killLabel, 3, rank);
            table.add(hitLabel, 4, rank);

            rank++;
        }

        // =========================
        // GAME END REASON
        // =========================
        Label reasonLabel = new Label(
                "⏱ Kết thúc: " + gameOver.getReason()
        );

        reasonLabel.setStyle(
                "-fx-text-fill: #AAAAAA;"
                + "-fx-font-size: 13px;"
        );

        // =========================
        // BUTTONS
        // =========================
        Button playAgainButton = new Button("🔄  CHƠI TIẾP");

        playAgainButton.setPrefWidth(150);
        playAgainButton.setPrefHeight(42);

        playAgainButton.setStyle(
                "-fx-background-color: #2ECC71;"
                + "-fx-text-fill: white;"
                + "-fx-font-size: 15px;"
                + "-fx-font-weight: bold;"
                + "-fx-background-radius: 8;"
        );

        Button exitButton = new Button("🚪  THOÁT");

        exitButton.setPrefWidth(150);
        exitButton.setPrefHeight(42);

        exitButton.setStyle(
                "-fx-background-color: #E74C3C;"
                + "-fx-text-fill: white;"
                + "-fx-font-size: 15px;"
                + "-fx-font-weight: bold;"
                + "-fx-background-radius: 8;"
        );

        // Hiện tại chỉ đóng popup.
        // Phần CHƠI LẠI sẽ nối với ROOM_START_REQ ở bước tiếp theo.
        playAgainButton.setOnAction(e -> {
            popup.close();
            handlePlayAgain();
        });

        exitButton.setOnAction(e -> {
            popup.close();
            handleExit();
        });

        javafx.scene.layout.HBox buttons
                = new javafx.scene.layout.HBox(20);

        buttons.setAlignment(javafx.geometry.Pos.CENTER);
        buttons.getChildren().addAll(
                playAgainButton,
                exitButton
        );

        // =========================
        // MAIN CONTAINER
        // =========================
        javafx.scene.layout.VBox content
                = new javafx.scene.layout.VBox(20);

        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPadding(new javafx.geometry.Insets(30));

        content.setStyle(
                "-fx-background-color: #151A24;"
                + "-fx-background-radius: 15;"
        );

        content.getChildren().addAll(
                title,
                winnerLabel,
                table,
                reasonLabel,
                buttons
        );

        javafx.scene.layout.StackPane root
                = new javafx.scene.layout.StackPane(content);

        root.setStyle(
                "-fx-background-color: #0B0F17;"
        );

        Scene scene = new Scene(root, 620, 500);

        popup.setScene(scene);
        popup.setResizable(false);

        popup.showAndWait();
    }

    public void setRoom(RoomDTO room) {
        this.currentRoom = room;
    }

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
            gc.fillOval(x - (radius / 2.0), y - (radius / 2.0), radius, radius);
            gc.setFill(Color.YELLOW);
            gc.fillOval(x - (radius / 4.0), y - (radius / 4.0), radius / 2.0, radius / 2.0);
        }
    }

    private void handlePlayAgain() {

        try {
            // Dừng render của ván cũ
            if (renderTimer != null) {
                renderTimer.stop();
            }

            activeKeys.clear();
            spacePressed = false;

            // Mở lại Room
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/tank2d/client/view/room.fxml"
                    )
            );

            Parent root = loader.load();

            // Lấy RoomController
            com.tank2d.client.controller.RoomController roomController
                    = loader.getController();

            // Truyền lại phòng hiện tại
            roomController.setRoom(currentRoom);
            roomController.resetReadyForReplay();

            Scene roomScene = new Scene(root);

            primaryStage.setScene(roomScene);
            primaryStage.setTitle("Tank 2D - Room");
            primaryStage.setResizable(false);
            primaryStage.show();

        } catch (Exception ex) {

            System.err.println(
                    "[Game] Không thể quay lại Room: "
                    + ex.getMessage()
            );

            ex.printStackTrace();
        }
    }

    public void setStage(Stage stage) {
        this.primaryStage = stage;
    }

    public Scene createGameScene() {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane();

        root.getChildren().add(canvas);

// Nút THOÁT
        Button exitButton = new Button("THOÁT");

        StackPane.setAlignment(exitButton, Pos.TOP_RIGHT);
        StackPane.setMargin(exitButton, new Insets(10));

        root.getChildren().add(exitButton);

        exitButton.setOnAction(e -> handleExit());

        Scene scene = new Scene(root);

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
        requestTankPlayerInfo();
        startRenderLoop();
        return scene;
    }

    private void requestTankPlayerInfo() {

        if (clientSocket == null || !clientSocket.isConnected()) {
            return;
        }

        try {
            clientSocket.sendPacket(
                    new Packet(
                            PacketType.TANK_PLAYER_INFO_REQ,
                            ""
                    )
            );

            System.out.println(
                    "[GAME] Đã yêu cầu thông tin Tank -> User"
            );

        } catch (IOException e) {
            System.err.println(
                    "[GAME] Không thể yêu cầu Tank Player Info: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================================
    // TASK 7: LOGIC NHẬN GÓI TIN TỪ SERVER
    // =========================================================================
    private void initNetworkReceiver() {
        this.clientSocket = ClientSession.getInstance().getClientSocket();
        ClientSession.getInstance().addPacketListener(this::processIncomingPacket);
    }

    private void processIncomingPacket(Packet packet) {

        if (packet.getType() == PacketType.GAME_SNAPSHOT) {

            GameSnapshotDTO snapshot
                    = gson.fromJson(
                            packet.getData(),
                            GameSnapshotDTO.class
                    );

            if (snapshot != null) {

                if (snapshot.getBullets() != null) {
                    this.bullets.clear();
                    this.bullets.addAll(
                            snapshot.getBullets()
                    );
                }

                if (snapshot.getTanks() != null) {

                    for (TankSnapshotDTO incoming
                            : snapshot.getTanks()) {

                        targetTanks.put(
                                incoming.getId(),
                                incoming
                        );

                        displayTanks.putIfAbsent(
                                incoming.getId(),
                                new TankSnapshotDTO(
                                        incoming.getId(),
                                        incoming.getX(),
                                        incoming.getY(),
                                        incoming.getAngle(),
                                        incoming.getHp(),
                                        incoming.isAlive()
                                )
                        );
                    }
                }
            }

        } else if (packet.getType()
                == PacketType.GAME_EVENT_EFFECT) {

            GameEventEffectDTO effect
                    = gson.fromJson(
                            packet.getData(),
                            GameEventEffectDTO.class
                    );

            if (effect != null
                    && "EXPLOSION".equalsIgnoreCase(
                            effect.getEventType()
                    )) {

                explosions.add(
                        new ExplosionEffect(
                                effect.getX(),
                                effect.getY()
                        )
                );
            }
        } else if (packet.getType()
                == PacketType.TANK_PLAYER_INFO) {

            TankPlayerDTO[] players
                    = gson.fromJson(
                            packet.getData(),
                            TankPlayerDTO[].class
                    );

            if (players != null) {
                tankPlayers.clear();

                for (TankPlayerDTO player : players) {
                    tankPlayers.add(player);

                    System.out.println(
                            "[Game] Tank "
                            + player.getTankId()
                            + " -> User "
                            + player.getUsername()
                    );
                }
            }
        } else if (packet.getType()
                == PacketType.GAME_OVER_NOTIFY) {

            GameOverDTO gameOver
                    = gson.fromJson(
                            packet.getData(),
                            GameOverDTO.class
                    );

            if (gameOver != null) {

                Platform.runLater(
                        () -> showGameOverPopup(gameOver)
                );
            }
        }
    }

    private void handleExit() {
        System.out.println("[GAME] Người chơi bấm THOÁT");

        // Dừng vòng lặp game ở client
        if (renderTimer != null) {
            renderTimer.stop();
        }

        // Xóa trạng thái phím
        activeKeys.clear();
        spacePressed = false;

        // Gửi yêu cầu rời phòng lên server
        try {
            if (clientSocket != null && clientSocket.isConnected()) {
                clientSocket.sendPacket(
                        new Packet(
                                PacketType.ROOM_LEAVE_REQ,
                                ""
                        )
                );

                System.out.println("[GAME] Đã gửi ROOM_LEAVE_REQ");
            }
        } catch (IOException ex) {
            System.err.println(
                    "[GAME] Không thể gửi ROOM_LEAVE_REQ: "
                    + ex.getMessage()
            );
        }

        // Quay về Lobby
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource(
                                "/com/tank2d/client/view/lobby.fxml"
                        )
                );

                Scene lobbyScene = new Scene(loader.load());

                primaryStage.setScene(lobbyScene);
                primaryStage.setTitle("Tank 2D - Lobby");
                primaryStage.setResizable(false);
                primaryStage.show();

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
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
    // VÒNG LẶP RENDER 60 FPS
    // =========================================================================
    // TASK 8: LOGIC CẬP NHẬT TRẠNG THÁI & RENDER RA CANVAS
    // =========================================================================
    private void startRenderLoop() {
        renderTimer = new AnimationTimer() {
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
    
    private void loadMapConfigFromFile(String mapName) {
    try {
        String resourcePath = "/config/maps/" + mapName + ".json";

        java.io.InputStream inputStream =
                getClass().getResourceAsStream(resourcePath);

        if (inputStream == null) {
            System.err.println(
                    "[Game] Không tìm thấy file map: " + resourcePath
            );
            return;
        }

        java.io.InputStreamReader reader =
                new java.io.InputStreamReader(
                        inputStream,
                        java.nio.charset.StandardCharsets.UTF_8
                );

        JsonObject root = gson.fromJson(reader, JsonObject.class);

        int width = root.get("width").getAsInt();
        int height = root.get("height").getAsInt();

        tileSize = 40;

        if (root.has("tile_size")) {
            tileSize = root.get("tile_size").getAsInt();
        }

        JsonArray matrixJson = root.getAsJsonArray("matrix");

        mapMatrix = new int[height][width];

        for (int r = 0; r < height; r++) {
            JsonArray row = matrixJson.get(r).getAsJsonArray();

            for (int c = 0; c < width; c++) {
                mapMatrix[r][c] = row.get(c).getAsInt();
            }
        }

        // Tường gạch mặc định chịu 3 phát
        int brickMaxHits = 3;

        brickHitsLeft = new int[height][width];

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {

                // TileType.BRICK_WALL = 2
                if (mapMatrix[r][c] == 2) {
                    brickHitsLeft[r][c] = brickMaxHits;
                }
            }
        }

        reader.close();

        System.out.println(
                "[Game] Đã load map "
                + mapName
                + " (" + width + "x" + height
                + ", tile=" + tileSize + ")"
        );

    } catch (Exception e) {
        System.err.println(
                "[Game] Lỗi load map: " + e.getMessage()
        );
        e.printStackTrace();
    }
}

// =========================================================================
    // TASK 7: RENDER MA TRẬN ĐỊA HÌNH & TƯỜNG (VẾT NỨT GẠCH 1/3, 2/3)
    // =========================================================================
    private void renderTerrainAndWalls() {
        if (mapMatrix == null) {
            return;
        }

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
        if (mapMatrix == null) {
            return;
        }
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

    private String getUsernameByTankId(int tankId) {

        for (TankPlayerDTO player : tankPlayers) {

            if (player.getTankId() == tankId) {
                return player.getUsername();
            }
        }

        return null;
    }

    private void renderTanks() {
        for (TankSnapshotDTO tank : displayTanks.values()) {
            if (!tank.isAlive()) {
                continue;
            }

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
            int currentHp = (int) Math.max(0, Math.min(MAX_HP, tank.getHp()));
            double hpWidth = (barWidth / MAX_HP) * currentHp;
            gc.setFill(Color.LIME);
            gc.fillRect(barX, barY, hpWidth, barHeight);

            // Kẻ 3 vạch chia thanh máu
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(1);
            gc.strokeRect(barX, barY, barWidth, barHeight);
            // Hiển thị tên người chơi phía trên xe
            // Hiển thị tên người chơi phía trên xe
            String username = getUsernameByTankId(tank.getId());

            if (username != null) {

                gc.setFill(Color.WHITE);
                gc.setFont(javafx.scene.text.Font.font(14));

                double textWidth = username.length() * 7;

                gc.fillText(
                        username,
                        tank.getX() - textWidth / 2.0,
                        barY - 8
                );
            }

// Chia thanh HP thành 3 vạch
            for (int i = 1; i < MAX_HP; i++) {
                double lineX = barX + (barWidth / MAX_HP) * i;
                gc.strokeLine(
                        lineX,
                        barY,
                        lineX,
                        barY + barHeight
                );
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
