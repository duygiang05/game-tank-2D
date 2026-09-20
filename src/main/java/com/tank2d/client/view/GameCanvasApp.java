package com.tank2d.client.view;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

        canvas.setFocusTraversable(true);
        Platform.runLater(() -> canvas.requestFocus());

        loadMapConfigFromFile("map_default");
        initNetworkReceiver();
        requestTankPlayerInfo();
        startRenderLoop();

        return scene;
    }

    private void requestTankPlayerInfo() {
        if (clientSocket == null || !clientSocket.isConnected()) return;
        try {
            clientSocket.sendPacket(new Packet(PacketType.TANK_PLAYER_INFO_REQ, ""));
        } catch (IOException ignored) {}
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
                        displayTanks.putIfAbsent(incoming.getId(), 
                            new TankSnapshotDTO(incoming.getId(), incoming.getX(), incoming.getY(), 
                                                incoming.getAngle(), incoming.getHp(), incoming.isAlive()));
                    }

                    // FIX KẸT BÓNG XE: Xóa sạch các xe đã bị khuất khỏi tầm nhìn (vào bụi cỏ)
                    displayTanks.keySet().removeIf(id -> !currentVisibleTankIds.contains(id));
                    targetTanks.keySet().removeIf(id -> !currentVisibleTankIds.contains(id));
                }
            }
        } else if (packet.getType() == PacketType.GAME_EVENT_EFFECT) {
            GameEventEffectDTO effect = gson.fromJson(packet.getData(), GameEventEffectDTO.class);
            if (effect != null && "EXPLOSION".equalsIgnoreCase(effect.getEventType())) {
                explosions.add(new ExplosionEffect(effect.getX(), effect.getY()));
            }
        } else if (packet.getType() == PacketType.TANK_PLAYER_INFO) {
            TankPlayerDTO[] players = gson.fromJson(packet.getData(), TankPlayerDTO[].class);
            if (players != null) {
                tankPlayers.clear();
                tankPlayers.addAll(Arrays.asList(players));
            }
        } else if (packet.getType() == PacketType.GAME_OVER_NOTIFY) {
            GameOverDTO gameOver = gson.fromJson(packet.getData(), GameOverDTO.class);
            if (gameOver != null) {
                Platform.runLater(() -> showGameOverPopup(gameOver));
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

                // 1. Clear nền tối
                gc.setFill(Color.rgb(22, 24, 29));
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

    private void renderTerrainAndWalls() {
        if (mapMatrix == null) return;

        Image stoneImg = AssetLoader.getImage("tiles/stone_wall.png");
        Image brickImg = AssetLoader.getImage("tiles/brick_wall.png");

        for (int r = 0; r < mapMatrix.length; r++) {
            for (int c = 0; c < mapMatrix[r].length; c++) {
                int tileType = mapMatrix[r][c];
                double x = c * tileSize;
                double y = r * tileSize;

                if (tileType == 1) {
                    if (stoneImg != null) {
                        gc.drawImage(stoneImg, x, y, tileSize, tileSize);
                    } else {
                        gc.setFill(Color.GRAY);
                        gc.fillRect(x, y, tileSize, tileSize);
                    }
                } else if (tileType == 2) {
                    if (brickImg != null) {
                        gc.drawImage(brickImg, x, y, tileSize, tileSize);
                    } else {
                        gc.setFill(Color.CHOCOLATE);
                        gc.fillRect(x, y, tileSize, tileSize);
                    }

                    int hitsLeft = brickHitsLeft[r][c];
                    if (hitsLeft < 3) {
                        gc.setStroke(Color.BLACK);
                        gc.setLineWidth(2);
                        gc.strokeLine(x + 8, y + 8, x + 18, y + 22);
                        if (hitsLeft <= 1) {
                            gc.strokeLine(x + 24, y + 10, x + 32, y + 32);
                            gc.strokeLine(x + 10, y + 26, x + 28, y + 34);
                        }
                    }
                }
            }
        }
    }

    private void renderBushes() {
        if (mapMatrix == null) return;
        Image bushImg = AssetLoader.getImage("tiles/grass.png");

        for (int r = 0; r < mapMatrix.length; r++) {
            for (int c = 0; c < mapMatrix[r].length; c++) {
                if (mapMatrix[r][c] == 3) {
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
        for (TankSnapshotDTO tank : displayTanks.values()) {
            if (!tank.isAlive()) continue;

            // 1. Thân & nòng xe
            gc.save();
            gc.translate(tank.getX(), tank.getY());
            gc.rotate(tank.getAngle() + 90.0);

            Color bodyColor = (tank.getId() == 1) ? Color.web("#4CAF50") : Color.web("#F44336");

            // Xích xe
            gc.setFill(Color.web("#333333"));
            gc.fillRect(-TANK_SIZE / 2.0 - 2, -TANK_SIZE / 2.0, 5, TANK_SIZE);
            gc.fillRect(TANK_SIZE / 2.0 - 3, -TANK_SIZE / 2.0, 5, TANK_SIZE);

            // Thân xe
            gc.setFill(bodyColor);
            gc.fillRect(-TANK_SIZE / 2.0 + 3, -TANK_SIZE / 2.0, TANK_SIZE - 6, TANK_SIZE);

            // Nòng pháo
            gc.setFill(Color.BLACK);
            gc.fillRect(-2.5, -TANK_SIZE / 2.0 - 10, 5, 12);

            // Tháp pháo
            gc.setFill(Color.web("#212121"));
            gc.fillOval(-7, -7, 14, 14);
            gc.restore();

            // 2. Thanh máu & tên người chơi
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

            gc.setFill(Color.DARKRED);
            gc.fillRect(barX, barY, barWidth, barHeight);

            int currentHp = (int) Math.max(0, Math.min(MAX_HP, tank.getHp()));
            double hpWidth = (barWidth / MAX_HP) * currentHp;
            gc.setFill(Color.LIME);
            gc.fillRect(barX, barY, hpWidth, barHeight);

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

    private void renderHUD() {
        gc.save();

        // Đồng hồ đếm ngược
        gc.setFill(Color.rgb(0, 0, 0, 0.3));
        gc.fillRoundRect(340, 10, 120, 35, 10, 10);
        gc.setStroke(Color.rgb(255, 215, 0, 0.5));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(340, 10, 120, 35, 10, 10);

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        gc.setFill(Color.rgb(255, 255, 0, 0.75));
        String timeStr = String.format("%02d:%02d", matchRemainingTime / 60, matchRemainingTime % 60);
        gc.fillText("⏱ " + timeStr, 365, 34);

        // Bảng xếp hạng mini
        gc.setFill(Color.rgb(15, 18, 24, 0.25));
        gc.fillRoundRect(10, 10, 210, 115, 8, 8);
        gc.setStroke(Color.rgb(58, 63, 77, 0.4));
        gc.strokeRoundRect(10, 10, 210, 115, 8, 8);

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        gc.setFill(Color.rgb(255, 255, 255, 0.7));
        gc.fillText("BẢNG ĐIỂM TRẬN ĐẤU", 20, 28);
        gc.setStroke(Color.rgb(128, 128, 128, 0.4));
        gc.strokeLine(20, 34, 200, 34);

        gc.setFont(Font.font("Consolas", FontWeight.NORMAL, 12));
        int startY = 52;
        if (displayTanks.isEmpty()) {
            gc.setFill(Color.rgb(211, 211, 211, 0.6));
            gc.fillText("Đang chờ người chơi...", 20, startY);
        } else {
            for (TankSnapshotDTO tank : displayTanks.values()) {
                String uName = getUsernameByTankId(tank.getId());
                String displayName = (uName != null) ? uName : ("Tank #" + tank.getId());
                if (displayName.length() > 8) displayName = displayName.substring(0, 8);

                Color textColor = (tank.getId() == 1) ? Color.rgb(76, 175, 80, 0.75) : Color.rgb(244, 67, 54, 0.75);
                gc.setFill(textColor);
                gc.fillText(String.format("%-8s | HP:%d", displayName, tank.getHp()), 20, startY);
                startY += 18;
            }
        }

        gc.restore();
    }

    private void handleExit() {
        if (renderTimer != null) renderTimer.stop();
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
        if (renderTimer != null) renderTimer.stop();
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