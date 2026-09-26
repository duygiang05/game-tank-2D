package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.client.view.GameCanvasApp;
import com.tank2d.common.dto.RoomDTO;
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

public class RoomController {

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

    private final Gson gson = new Gson();
    private final ClientSession session = ClientSession.getInstance();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    private RoomDTO currentRoom;
    private boolean listenerRegistered = false;

    // ==========================================
    // SET ROOM
    // ==========================================
    public void setRoom(RoomDTO room) {
        this.currentRoom = room;
        if (room == null) return;

        setupDurationComboBox();
        updateRoomUI(room);

        if (!listenerRegistered) {
            session.addPacketListener(packetListener);
            listenerRegistered = true;
            System.out.println("[Room] Đã đăng ký Room Packet Listener.");
        }
    }

    // ==========================================
    // SETUP DURATION
    // ==========================================
    private void setupDurationComboBox() {
        if (durationComboBox == null) return;

        if (durationComboBox.getItems().isEmpty()) {
            durationComboBox.getItems().addAll("45 giây", "60 giây", "90 giây");
        }

        durationComboBox.setOnAction(event -> handleDurationChanged());
    }

    // ==========================================
    // UPDATE ROOM UI
    // ==========================================
    private void updateRoomUI(RoomDTO room) {
        Platform.runLater(() -> {
            roomNameLabel.setText(room.getRoomName());

            String statusVi = "Playing".equalsIgnoreCase(room.getStatus()) ? "Đang chiến đấu" : "Đang chờ người chơi";
            roomStatusLabel.setText(
                    room.getCurrentPlayers() + "/" + room.getMaxPlayers()
                    + " người chơi  •  " + statusVi
                    + "  •  Thời lượng: " + room.getDuration() + "s"
            );

            updateSpawnSlots(room);
            updateDurationUI(room);
            updateButton(room);
        });
    }

    private void updateSpawnSlots(RoomDTO room) {
        List<String> players = room.getPlayerNames();
        Map<Integer, Boolean> readyMap = room.getReadyStates();

        updateSingleSlot(p1Label, p1StatusBadge, players, readyMap, room, 0);
        updateSingleSlot(p2Label, p2StatusBadge, players, readyMap, room, 1);
        updateSingleSlot(p3Label, p3StatusBadge, players, readyMap, room, 2);
        updateSingleSlot(p4Label, p4StatusBadge, players, readyMap, room, 3);
    }

    private void updateSingleSlot(Label nameLabel, Label badgeLabel, List<String> players, 
                                  Map<Integer, Boolean> readyMap, RoomDTO room, int index) {
        if (nameLabel == null || badgeLabel == null) return;

        if (players != null && index < players.size()) {
            String playerName = players.get(index);
            nameLabel.setText(playerName);
            nameLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 14px; -fx-font-weight: bold;");

            // 1. Slot 0 luôn là Chủ phòng (Host)
            if (index == 0) {
                badgeLabel.setText("👑 CHỦ PHÒNG");
                badgeLabel.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 12px;");
            } else {
                // 2. Slot người chơi thường (P2, P3, P4):
                User currentUser = session.getCurrentUser();
                boolean isReady = false;

                // Nếu slot này thuộc về chính client hiện tại đang xem
                if (currentUser != null && playerName.equalsIgnoreCase(currentUser.getUsername())) {
                    isReady = isCurrentUserReady(room);
                } else {
                    // Nếu là người chơi khác trên màn hình Host/Client:
                    // Server Room lưu Host không ở trong readyStates, chỉ những ai là khách mới có trong map
                    if (readyMap != null && !readyMap.isEmpty()) {
                        for (Map.Entry<Integer, Boolean> entry : readyMap.entrySet()) {
                            if (entry.getKey() != room.getHostId() && Boolean.TRUE.equals(entry.getValue())) {
                                isReady = true;
                                break;
                            }
                        }
                    }
                }

                if (isReady) {
                    badgeLabel.setText("✅ ĐÃ SẴN SÀNG");
                    badgeLabel.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 12px;");
                } else {
                    badgeLabel.setText("⏳ CHƯA SẴN SÀNG");
                    badgeLabel.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 12px;");
                }
            }
        } else {
            // Slot trống
            nameLabel.setText("Trống");
            nameLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-weight: bold;");
            badgeLabel.setText("ĐANG CHỜ...");
            badgeLabel.setStyle("-fx-background-color: #f8fafc; -fx-text-fill: #cbd5e1; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 12px;");
        }
    }

    // ==========================================
    // UPDATE DURATION UI
    // ==========================================
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
            System.out.println("[Room] Host chọn thời lượng: " + duration + " giây");

        } catch (IOException e) {
            System.err.println("[Room] Lỗi gửi duration: " + e.getMessage());
        }
    }

    // ==========================================
    // UPDATE BUTTON
    // ==========================================
    private void updateButton(RoomDTO room) {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) return;

        boolean isHost = room.getHostId() == currentUser.getId();

        if ("Playing".equalsIgnoreCase(room.getStatus())) {
            readyButton.setText("ĐANG CHIẾN ĐẤU");
            readyButton.setDisable(true);
            readyButton.setStyle("-fx-background-color: #94a3b8; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px;");
            return;
        }

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
        if (room.getReadyStates() == null || room.getReadyStates().isEmpty()) return false;
        for (Map.Entry<Integer, Boolean> entry : room.getReadyStates().entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) return false;
        }
        return true;
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
            RoomDTO room = gson.fromJson(rawJson, RoomDTO.class);
            if (room == null) return;
            currentRoom = room;
            updateRoomUI(room);
        } catch (Exception e) {
            System.err.println("[Room] Lỗi xử lý ROOM_STATE_UPDATE: " + e.getMessage());
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
            System.out.println("[Room] Đã gửi READY: " + ready);

        } catch (IOException e) {
            roomStatusLabel.setText("Không thể gửi trạng thái Ready!");
            System.err.println("[Room] Lỗi Ready: " + e.getMessage());
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
            System.out.println("[Room] Host đã gửi ROOM_START_REQ.");

        } catch (IOException e) {
            roomStatusLabel.setText("Không thể bắt đầu trận!");
            System.err.println("[Room] Lỗi Start: " + e.getMessage());
        }
    }

    private void handleGameStart(String rawJson) {
        System.out.println("[Room] Nhận GAME_START_NOTIFY!");
        removePacketListener();

        Platform.runLater(() -> {
            try {
                Stage stage = (Stage) roomNameLabel.getScene().getWindow();
                GameCanvasApp gameCanvasApp = new GameCanvasApp();
                gameCanvasApp.setStage(stage);
                gameCanvasApp.setRoom(currentRoom);

                Scene gameScene = gameCanvasApp.createGameScene();
                stage.setScene(gameScene);
                stage.setTitle("Tank 2D - Game");
                stage.setResizable(false);
                stage.show();
                System.out.println("[Game] Đã chuyển sang màn hình Game!");

            } catch (Exception e) {
                System.err.println("[Game] Không thể mở màn hình Game: " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleLeave() {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket != null && clientSocket.isConnected()) {
                clientSocket.sendPacket(new Packet(PacketType.ROOM_LEAVE_REQ, ""));
                System.out.println("[Room] Đã gửi yêu cầu rời phòng.");
            }
        } catch (IOException e) {
            System.err.println("[Room] Lỗi khi rời phòng: " + e.getMessage());
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
                System.out.println("[Room] Đã quay lại Lobby.");

            } catch (IOException e) {
                System.err.println("[Room] Không thể quay lại Lobby: " + e.getMessage());
            }
        });
    }

    private void removePacketListener() {
        if (listenerRegistered) {
            session.removePacketListener(packetListener);
            listenerRegistered = false;
            System.out.println("[Room] Đã xóa Room Packet Listener.");
        }
    }
}