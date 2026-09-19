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
import com.tank2d.common.dto.game.GameOverDTO;
import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.dto.game.TankPlayerDTO;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
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
    private static final double BULLET_SIZE = 8.0;
    private static final double LERP_FACTOR = 0.3;
    private static final double MAX_HP = 3.0;

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

        // Đăng ký trực tiếp bộ xử lý vào luồng duy nhất của ClientSession
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
    // TASK 8: LOGIC CẬP NHẬT TRẠNG THÁI & RENDER RA CANVAS
    // =========================================================================
    private void startRenderLoop() {
        renderTimer = new AnimationTimer() {
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

    private void renderBullets() {
        gc.setFill(Color.YELLOW);
        for (BulletSnapshotDTO bullet : bullets) {
            gc.fillOval(bullet.getX() - BULLET_SIZE / 2.0, bullet.getY() - BULLET_SIZE / 2.0, BULLET_SIZE, BULLET_SIZE);
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
