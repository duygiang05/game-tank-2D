package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.client.view.GameCanvasApp;
import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.exception.GameNetworkException;
import com.tank2d.common.config.ConfigLoader;
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

/** Room waiting screen; networking and ready logic match the original implementation. */
public class RoomController {
    private static final Logger LOGGER = Logger.getLogger(RoomController.class.getName());
    private static final Gson GSON = new Gson();

    @FXML private Label roomNameLabel;
    @FXML private Label roomStatusLabel;
    @FXML private Label p1Label;
    @FXML private Label p1StatusBadge;
    @FXML private Label p2Label;
    @FXML private Label p2StatusBadge;
    @FXML private Label p3Label;
    @FXML private Label p3StatusBadge;
    @FXML private Label p4Label;
    @FXML private Label p4StatusBadge;
    @FXML private Button readyButton;
    @FXML private Button leaveButton;
    @FXML private ComboBox<String> durationComboBox;

    private final ClientSession session = ClientSession.getInstance();
    private final Consumer<Packet> packetListener = this::handleServerPacket;
    private RoomDTO currentRoom;
    private boolean listenerRegistered = false;

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
            roomStatusLabel.setText(room.getCurrentPlayers() + "/" + room.getMaxPlayers()
                    + " người chơi  •  Đang chờ người chơi  •  Thời lượng: " + room.getDuration() + "s");
            clearSlots();
            List<String> players = room.getPlayerNames();
            String hostUsername = room.getHostUsername();
            Map<String, Boolean> playerReadyMap = room.getPlayerReadyMap();
            Map<Integer, Boolean> readyStates = room.getReadyStates();
            int hostId = room.getHostId();
            User currentUser = session.getCurrentUser();
            if (players != null) {
                for (int i = 0; i < players.size(); i++) {
                    String username = players.get(i);
                    boolean isHost = false;
                    if (hostUsername != null && !hostUsername.trim().isEmpty()) {
                        isHost = hostUsername.equalsIgnoreCase(username);
                    } else if (currentUser != null && username.equals(currentUser.getUsername())) {
                        isHost = currentUser.getId() == hostId;
                    } else if (i == 0) {
                        isHost = true;
                    }
                    boolean isReady = isHost;
                    if (!isHost) {
                        if (playerReadyMap != null && playerReadyMap.containsKey(username)) {
                            isReady = Boolean.TRUE.equals(playerReadyMap.get(username));
                        } else if (currentUser != null && username.equals(currentUser.getUsername()) && readyStates != null) {
                            isReady = Boolean.TRUE.equals(readyStates.get(currentUser.getId()));
                        }
                    }
                    renderSlot(i + 1, username, isHost, isReady);
                }
            }
            updateDurationUI(room);
            updateButton(room);
        });
    }

    private boolean isUserHost(RoomDTO room, User user) {
        if (room == null || user == null) return false;
        if (room.getHostUsername() != null && !room.getHostUsername().trim().isEmpty()) {
            return room.getHostUsername().equalsIgnoreCase(user.getUsername());
        }
        return room.getHostId() == user.getId();
    }

    private void clearSlots() {
        resetSlot(p1Label, p1StatusBadge, "Chỗ trống P1");
        resetSlot(p2Label, p2StatusBadge, "Chỗ trống P2");
        resetSlot(p3Label, p3StatusBadge, "Chỗ trống P3");
        resetSlot(p4Label, p4StatusBadge, "Chỗ trống P4");
    }

    private static void changeClass(javafx.scene.Node node, String prefix, String cssClass) {
        node.getStyleClass().removeIf(name -> name.startsWith(prefix));
        node.getStyleClass().add(cssClass);
    }

    private void resetSlot(Label nameLabel, Label badgeLabel, String placeholder) {
        if (nameLabel != null) {
            nameLabel.setText(placeholder);
            changeClass(nameLabel, "room-name-state-", "room-name-state-empty");
        }
        if (badgeLabel != null) {
            badgeLabel.setText("TRỐNG");
            changeClass(badgeLabel, "room-badge-state-", "room-badge-state-empty");
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
        changeClass(nameLabel, "room-name-state-", "room-name-state-occupied");
        if (isHost) {
            badgeLabel.setText("CHỦ PHÒNG");
            changeClass(badgeLabel, "room-badge-state-", "room-badge-state-host");
        } else if (isReady) {
            badgeLabel.setText("SẴN SÀNG");
            changeClass(badgeLabel, "room-badge-state-", "room-badge-state-ready");
        } else {
            badgeLabel.setText("ĐANG CHỜ...");
            changeClass(badgeLabel, "room-badge-state-", "room-badge-state-waiting");
        }
    }

    private void updateDurationUI(RoomDTO room) {
        if (durationComboBox == null) return;
        User currentUser = session.getCurrentUser();
        if (currentUser == null) return;
        boolean isHost = isUserHost(room, currentUser);
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
        if (currentUser == null || !isUserHost(currentRoom, currentUser)) return;
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
            clientSocket.sendPacket(new Packet(PacketType.ROOM_DURATION_REQ, String.valueOf(duration)));
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
        boolean isHost = isUserHost(room, currentUser);
        if (isHost) {
            readyButton.setText("BẮT ĐẦU TRẬN ĐẤU");
            boolean canStart = room.getCurrentPlayers() >= 2
                    && room.getCurrentPlayers() <= room.getMaxPlayers()
                    && areAllPlayersReady(room);
            readyButton.setDisable(!canStart);
            changeClass(readyButton, "room-action-state-", canStart ? "room-action-state-start" : "room-action-state-locked");
        } else {
            boolean isReady = isCurrentUserReady(room);
            readyButton.setText(isReady ? "HUỶ SẴN SÀNG" : "SẴN SÀNG");
            readyButton.setDisable(false);
            changeClass(readyButton, "room-action-state-", isReady ? "room-action-state-cancel" : "room-action-state-ready");
        }
    }

    private boolean isCurrentUserReady(RoomDTO room) {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) return false;
        if (room.getPlayerReadyMap() != null && room.getPlayerReadyMap().containsKey(currentUser.getUsername())) {
            return Boolean.TRUE.equals(room.getPlayerReadyMap().get(currentUser.getUsername()));
        }
        if (room.getReadyStates() != null) {
            return room.getReadyStates().getOrDefault(currentUser.getId(), false);
        }
        return false;
    }

    private boolean areAllPlayersReady(RoomDTO room) {
        if (room.getPlayerReadyMap() != null && !room.getPlayerReadyMap().isEmpty()) {
            String hostUsername = room.getHostUsername();
            int nonHostCount = 0;
            for (Map.Entry<String, Boolean> entry : room.getPlayerReadyMap().entrySet()) {
                if (hostUsername == null || !hostUsername.equalsIgnoreCase(entry.getKey())) {
                    nonHostCount++;
                    if (!Boolean.TRUE.equals(entry.getValue())) return false;
                }
            }
            return nonHostCount > 0;
        }
        Map<Integer, Boolean> readyMap = room.getReadyStates();
        if (readyMap == null || readyMap.isEmpty()) return false;
        int nonHostCount = 0;
        for (Map.Entry<Integer, Boolean> entry : readyMap.entrySet()) {
            if (entry.getKey() != room.getHostId()) {
                nonHostCount++;
                if (!Boolean.TRUE.equals(entry.getValue())) return false;
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
        if (isUserHost(currentRoom, currentUser)) {
            handleStartGame();
            return;
        }
        sendReady(!isCurrentUserReady(currentRoom));
    }

    private void sendReady(boolean ready) {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                roomStatusLabel.setText("Chưa kết nối Server!");
                return;
            }
            clientSocket.sendPacket(new Packet(PacketType.ROOM_READY_REQ, String.valueOf(ready)));
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
            clientSocket.sendPacket(new Packet(PacketType.ROOM_START_REQ, ""));
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
                stage.setResizable(false);
                stage.sizeToScene();
                stage.show();
                LOGGER.info("[Game] Đã chuyển sang màn hình Game!");
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
                FXMLLoader loader = new FXMLLoader(RoomController.class.getResource("/com/tank2d/client/view/lobby.fxml"));
                Scene lobbyScene = new Scene(loader.load(), ConfigLoader.getWindowWidth(), ConfigLoader.getWindowHeight());
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
