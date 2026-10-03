package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.client.view.GameCanvasApp;
import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.exception.GameNetworkException;
import com.tank2d.common.model.User;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Điều khiển màn hình phòng chờ trận đấu (Room Waiting View).
 * <p>
 * Quản lý danh sách thành viên trong phòng, trạng thái sẵn sàng, chọn thời lượng trận đấu (đối với chủ phòng)
 * và chuyển tiếp sang màn hình chơi game (GameCanvasApp) khi trận đấu bắt đầu.
 */
public class RoomController {

    private static final Logger LOGGER = Logger.getLogger(RoomController.class.getName());
    private static final Gson GSON = new Gson();

    @FXML
    private Label roomNameLabel;

    @FXML
    private Label roomStatusLabel;

    @FXML
    private Label p1Label;
    @FXML
    private Label p1StatusBadge;

    @FXML
    private Label p2Label;
    @FXML
    private Label p2StatusBadge;

    @FXML
    private Label p3Label;
    @FXML
    private Label p3StatusBadge;

    @FXML
    private Label p4Label;
    @FXML
    private Label p4StatusBadge;

    @FXML
    private Button readyButton;

    @FXML
    private Button leaveButton;

    @FXML
    private ComboBox<String> durationComboBox;

    private final ClientSession session = ClientSession.getInstance();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    private RoomDTO currentRoom;
    private boolean listenerRegistered = false;

    /**
     * Nạp dữ liệu phòng chơi và khởi tạo giao diện phòng.
     *
     * @param room dữ liệu phòng từ máy chủ
     */
    public void setRoom(RoomDTO room) {
        this.currentRoom = room;
        if (room == null) return;

        setupDurationComboBox();
        updateRoomUI(room);

        if (!listenerRegistered) {
            session.addPacketListener(packetListener);
            listenerRegistered = true;
            LOGGER.info("[Room] Đã đăng ký Room Packet Listener.");
        }

        try {
            ClientSocket socket = session.getClientSocket();
            if (socket != null && socket.isConnected()) {
                socket.sendPacket(new Packet(PacketType.ROOM_JOIN_REQ, String.valueOf(room.getRoomId())));
            }
        } catch (Exception ignored) {}
    }

    private void setupDurationComboBox() {
        if (durationComboBox == null) return;

        if (durationComboBox.getItems().isEmpty()) {
            durationComboBox.getItems().addAll("45 giây", "60 giây", "90 giây", "180 giây");
        }

        durationComboBox.setOnAction(event -> handleDurationChanged());
    }

    private void updateRoomUI(RoomDTO room) {
        Platform.runLater(() -> {
            roomNameLabel.setText(room.getRoomName());

            String statusVi = "Đang chờ người chơi";
            roomStatusLabel.setText(
                    room.getCurrentPlayers() + "/" + room.getMaxPlayers()
                    + " người chơi  •  " + statusVi
                    + "  •  Thời lượng: " + room.getDuration() + "s"
            );

            clearSlots();

            List<String> players = room.getPlayerNames();
            Map<Integer, Boolean> readyStates = room.getReadyStates();
            int hostId = room.getHostId();

            if (players != null) {
                for (int i = 0; i < players.size(); i++) {
                    String username = players.get(i);
                    boolean isHost = false;
                    boolean isReady = false;

                    User currentUser = session.getCurrentUser();
                    if (currentUser != null && username.equals(currentUser.getUsername())) {
                        isHost = (currentUser.getId() == hostId);
                        isReady = readyStates != null && Boolean.TRUE.equals(readyStates.get(currentUser.getId()));
                    } else {
                        if (readyStates != null) {
                            for (Map.Entry<Integer, Boolean> entry : readyStates.entrySet()) {
                                if (entry.getKey() == hostId) {
                                    if (i == 0) isHost = true;
                                }
                            }
                        }
                        if (i == 0) isHost = true;
                        isReady = isHost || (readyStates != null && isGuestReady(readyStates, hostId, i));
                    }

                    renderSlot(i + 1, username, isHost, isReady);
                }
            }

            updateDurationUI(room);
            updateButton(room);
        });
    }

    private boolean isGuestReady(Map<Integer, Boolean> readyStates, int hostId, int slotIndex) {
        int currentIndex = 0;
        for (Map.Entry<Integer, Boolean> entry : readyStates.entrySet()) {
            if (entry.getKey() != hostId) {
                currentIndex++;
                if (currentIndex == slotIndex) {
                    return Boolean.TRUE.equals(entry.getValue());
                }
            }
        }
        return false;
    }

    private void clearSlots() {
        resetSlot(p1Label, p1StatusBadge, "Chỗ trống P1");
        resetSlot(p2Label, p2StatusBadge, "Chỗ trống P2");
        resetSlot(p3Label, p3StatusBadge, "Chỗ trống P3");
        resetSlot(p4Label, p4StatusBadge, "Chỗ trống P4");
    }

    private void resetSlot(Label nameLabel, Label badgeLabel, String placeholder) {
        if (nameLabel != null) {
            nameLabel.setText(placeholder);
            nameLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic;");
        }
        if (badgeLabel != null) {
            badgeLabel.setText("TRỐNG");
            badgeLabel.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 12px;");
        }
    }

    private void renderSlot(int slot, String username, boolean isHost, boolean isReady) {
        switch (slot) {
            case 1 -> applySlot(p1Label, p1StatusBadge, username, isHost, isReady);
            case 2 -> applySlot(p2Label, p2StatusBadge, username, isHost, isReady);
            case 3 -> applySlot(p3Label, p3StatusBadge, username, isHost, isReady);
            case 4 -> applySlot(p4Label, p4StatusBadge, username, isHost, isReady);
            default -> {}
        }
    }

    private void applySlot(Label nameLabel, Label badgeLabel, String username, boolean isHost, boolean isReady) {
        if (nameLabel == null || badgeLabel == null) return;

        nameLabel.setText(username);
        nameLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-weight: bold; -fx-font-style: normal;");

        if (isHost) {
            badgeLabel.setText("CHỦ PHÒNG");
            badgeLabel.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 12px;");
        } else if (isReady) {
            badgeLabel.setText("SẴN SÀNG");
            badgeLabel.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 12px;");
        } else {
            badgeLabel.setText("ĐANG CHỜ...");
            badgeLabel.setStyle("-fx-background-color: #f8fafc; -fx-text-fill: #cbd5e1; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 12px;");
        }
    }

    private void updateDurationUI(RoomDTO room) {
        if (durationComboBox == null) return;

        User currentUser = session.getCurrentUser();
        if (currentUser == null) return;

        boolean isHost = room.getHostId() == currentUser.getId();
        String durationText = room.getDuration() + " giây";

        durationComboBox.setOnAction(null);

        if (!durationComboBox.getItems().contains(durationText)) {
            durationComboBox.getItems().add(durationText);
        }

        durationComboBox.setValue(durationText);
        durationComboBox.setDisable(!isHost);

        durationComboBox.setOnAction(event -> handleDurationChanged());
    }

    private void handleDurationChanged() {
        if (currentRoom == null) return;
        User currentUser = session.getCurrentUser();
        if (currentUser == null || currentRoom.getHostId() != currentUser.getId()) return;

        String selected = durationComboBox.getValue();
        if (selected == null) return;

        int duration = switch (selected) {
            case "45 giây" -> 45;
            case "60 giây" -> 60;
            case "90 giây" -> 90;
            case "180 giây" -> 180;
            default -> 60;
        };

        sendDuration(duration);
    }

    private void sendDuration(int duration) {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                roomStatusLabel.setText("Chưa kết nối Server!");
                return;
            }

            Packet request = new Packet(PacketType.ROOM_DURATION_REQ, String.valueOf(duration));
            clientSocket.sendPacket(request);
            LOGGER.info("[Room] Host chọn thời lượng: " + duration + " giây");

        } catch (GameNetworkException e) {
            roomStatusLabel.setText(e.getMessage());
            LOGGER.log(Level.WARNING, "[Room] Lỗi nghiệp vụ khi gửi duration", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Room] Lỗi gửi duration", e);
        }
    }

    private void updateButton(RoomDTO room) {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) return;

        boolean isHost = room.getHostId() == currentUser.getId();

        if (isHost) {
            readyButton.setText("BẮT ĐẦU TRẬN ĐẤU");

            boolean canStart = room.getCurrentPlayers() >= 2
                    && room.getCurrentPlayers() <= room.getMaxPlayers()
                    && areAllPlayersReady(room);

            readyButton.setDisable(!canStart);

            if (canStart) {
                readyButton.setStyle("-fx-background-color: linear-gradient(to bottom, #22c55e, #15803d); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 8px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(22,163,74,0.4), 8, 0, 0, 3);");
            } else {
                readyButton.setStyle("-fx-background-color: #cbd5e1; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 8px;");
            }

        } else {
            boolean isReady = isCurrentUserReady(room);

            if (isReady) {
                readyButton.setText("HUỶ SẴN SÀNG");
                readyButton.setDisable(false);
                readyButton.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 14px; -fx-border-color: #fca5a5; -fx-border-width: 1.5px; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-cursor: hand;");
            } else {
                readyButton.setText("SẴN SÀNG");
                readyButton.setDisable(false);
                readyButton.setStyle("-fx-background-color: linear-gradient(to bottom, #3b82f6, #1d4ed8); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 8px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(37,99,235,0.35), 8, 0, 0, 3);");
            }
        }
    }

    private boolean isCurrentUserReady(RoomDTO room) {
        User currentUser = session.getCurrentUser();
        if (currentUser == null || room.getReadyStates() == null) return false;
        return room.getReadyStates().getOrDefault(currentUser.getId(), false);
    }

    private boolean areAllPlayersReady(RoomDTO room) {
        Map<Integer, Boolean> readyMap = room.getReadyStates();
        if (readyMap == null || readyMap.isEmpty()) return false;

        int nonHostCount = 0;
        for (Map.Entry<Integer, Boolean> entry : readyMap.entrySet()) {
            if (entry.getKey() != room.getHostId()) {
                nonHostCount++;
                if (!Boolean.TRUE.equals(entry.getValue())) {
                    return false;
                }
            }
        }

        return nonHostCount > 0;
    }

    private void handleServerPacket(Packet packet) {
        if (packet == null || packet.getType() == null) return;

        switch (packet.getType()) {
            case ROOM_STATE_UPDATE -> handleRoomStateUpdate(packet.getData());
            case GAME_START_NOTIFY -> handleGameStart(packet.getData());
            default -> {}
        }
    }

    private void handleRoomStateUpdate(String rawJson) {
        try {
            RoomDTO room = GSON.fromJson(rawJson, RoomDTO.class);
            if (room == null) return;
            currentRoom = room;
            updateRoomUI(room);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Room] Lỗi xử lý ROOM_STATE_UPDATE", e);
        }
    }

    @FXML
    private void handleReady() {
        if (currentRoom == null) return;
        User currentUser = session.getCurrentUser();
        if (currentUser == null) return;

        if (currentRoom.getHostId() == currentUser.getId()) {
            handleStartGame();
            return;
        }

        boolean currentReady = isCurrentUserReady(currentRoom);
        sendReady(!currentReady);
    }

    private void sendReady(boolean ready) {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                roomStatusLabel.setText("Chưa kết nối Server!");
                return;
            }

            Packet request = new Packet(PacketType.ROOM_READY_REQ, String.valueOf(ready));
            clientSocket.sendPacket(request);
            LOGGER.info("[Room] Đã gửi READY: " + ready);

        } catch (GameNetworkException e) {
            roomStatusLabel.setText(e.getMessage());
            LOGGER.log(Level.WARNING, "[Room] Lỗi nghiệp vụ Ready: " + e.getErrorCode(), e);
        } catch (IOException e) {
            roomStatusLabel.setText("Không thể gửi trạng thái Ready!");
            LOGGER.log(Level.SEVERE, "[Room] Lỗi Ready", e);
        }
    }

    public void resetReadyForReplay() {
        sendReady(false);
    }

    private void handleStartGame() {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                roomStatusLabel.setText("Chưa kết nối Server!");
                return;
            }

            if (currentRoom == null) return;

            if (currentRoom.getCurrentPlayers() < 2) {
                roomStatusLabel.setText("Cần ít nhất 2 người chơi để bắt đầu!");
                return;
            }

            if (!areAllPlayersReady(currentRoom)) {
                roomStatusLabel.setText("Tất cả người chơi phải bấm Sẵn sàng!");
                return;
            }

            Packet request = new Packet(PacketType.ROOM_START_REQ, "");
            clientSocket.sendPacket(request);
            readyButton.setDisable(true);
            LOGGER.info("[Room] Host đã gửi ROOM_START_REQ.");

        } catch (GameNetworkException e) {
            roomStatusLabel.setText(e.getMessage());
            LOGGER.log(Level.WARNING, "[Room] Lỗi nghiệp vụ Start: " + e.getErrorCode(), e);
        } catch (IOException e) {
            roomStatusLabel.setText("Không thể bắt đầu trận!");
            LOGGER.log(Level.SEVERE, "[Room] Lỗi Start", e);
        }
    }

    private void handleGameStart(String rawJson) {
        LOGGER.info("[Room] Nhận GAME_START_NOTIFY!");
        removePacketListener();

        Platform.runLater(() -> {
            try {
                Stage stage = (Stage) roomNameLabel.getScene().getWindow();
                stage.setMinWidth(0);
                stage.setMinHeight(0);
                stage.setMaxWidth(Double.MAX_VALUE);
                stage.setMaxHeight(Double.MAX_VALUE);
                stage.setResizable(true);

                GameCanvasApp gameCanvasApp = new GameCanvasApp();
                gameCanvasApp.setStage(stage);
                gameCanvasApp.setRoom(currentRoom);

                Scene gameScene = gameCanvasApp.createGameScene();
                stage.setScene(gameScene);
                stage.setTitle("Tank 2D - Game");
                
                stage.sizeToScene();
                stage.setResizable(false);
                stage.centerOnScreen();
                stage.show();
                LOGGER.info("[Game] Đã chuyển sang màn hình Game chuẩn 600x600!");

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[Game] Không thể mở màn hình Game", e);
            }
        });
    }

    @FXML
    private void handleLeave() {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket != null && clientSocket.isConnected()) {
                clientSocket.sendPacket(new Packet(PacketType.ROOM_LEAVE_REQ, ""));
                LOGGER.info("[Room] Đã gửi yêu cầu rời phòng.");
            }
        } catch (GameNetworkException e) {
            LOGGER.log(Level.WARNING, "[Room] Lỗi nghiệp vụ khi rời phòng: " + e.getErrorCode(), e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Room] Lỗi khi rời phòng", e);
        }

        removePacketListener();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        RoomController.class.getResource("/com/tank2d/client/view/lobby.fxml")
                );
                Scene lobbyScene = new Scene(loader.load(), 800, 600);

                Stage stage = (Stage) leaveButton.getScene().getWindow();
                stage.setScene(lobbyScene);
                stage.setTitle("Tank 2D Online - Lobby");
                stage.show();
                LOGGER.info("[Room] Đã quay lại Lobby.");

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "[Room] Không thể quay lại Lobby", e);
            }
        });
    }

    private void removePacketListener() {
        if (listenerRegistered) {
            session.removePacketListener(packetListener);
            listenerRegistered = false;
            LOGGER.info("[Room] Đã xóa Room Packet Listener.");
        }
    }
}