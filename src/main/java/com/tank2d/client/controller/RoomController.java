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
    private Label p2Label;

    @FXML
    private Label p3Label;

    @FXML
    private Label p4Label;

    @FXML
    private Button readyButton;

    @FXML
    private Button leaveButton;

    @FXML
    private ComboBox<String> durationComboBox;

    private final Gson gson = new Gson();

    private final ClientSession session
            = ClientSession.getInstance();

    private final Consumer<Packet> packetListener
            = this::handleServerPacket;

    private RoomDTO currentRoom;

    private boolean listenerRegistered = false;

    // =========================
    // SET ROOM
    // =========================
    public void setRoom(RoomDTO room) {

        this.currentRoom = room;

        if (room == null) {
            return;
        }

        setupDurationComboBox();

        updateRoomUI(room);

        if (!listenerRegistered) {

            session.addPacketListener(
                    packetListener
            );

            listenerRegistered = true;

            System.out.println(
                    "[Room] Đã đăng ký Room Packet Listener."
            );
        }
    }

    // =========================
    // SETUP DURATION
    // =========================
    private void setupDurationComboBox() {

        if (durationComboBox == null) {
            return;
        }

        // Không thêm lại nhiều lần
        if (durationComboBox.getItems().isEmpty()) {

            durationComboBox.getItems().addAll(
                    "45 giây",
                    "60 giây",
                    "90 giây"
            );
        }

        durationComboBox.setOnAction(
                event -> handleDurationChanged()
        );
    }

    // =========================
    // UPDATE ROOM UI
    // =========================
    private void updateRoomUI(RoomDTO room) {

        Platform.runLater(() -> {

            roomNameLabel.setText(
                    room.getRoomName()
            );

            roomStatusLabel.setText(
                    room.getCurrentPlayers()
                    + "/"
                    + room.getMaxPlayers()
                    + " người chơi - "
                    + room.getStatus()
                    + " - "
                    + room.getDuration()
                    + " giây"
            );

         

            updateSpawnSlots(room);

            updateDurationUI(room);

            updateButton(room);
        });
    }

    private void updateSpawnSlots(RoomDTO room) {

        if (room.getPlayerNames() == null) {
            p1Label.setText("Trống");
            p2Label.setText("Trống");
            p3Label.setText("Trống");
            p4Label.setText("Trống");
            return;
        }

        java.util.List<String> players
                = room.getPlayerNames();

        p1Label.setText(
                players.size() > 0
                ? players.get(0)
                : "Trống"
        );

        p2Label.setText(
                players.size() > 1
                ? players.get(1)
                : "Trống"
        );

        p3Label.setText(
                players.size() > 2
                ? players.get(2)
                : "Trống"
        );

        p4Label.setText(
                players.size() > 3
                ? players.get(3)
                : "Trống"
        );
    }

    // =========================
    // UPDATE DURATION UI
    // =========================
    private void updateDurationUI(RoomDTO room) {

        if (durationComboBox == null) {
            return;
        }

        User currentUser
                = session.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        boolean isHost
                = room.getHostId()
                == currentUser.getId();

        String durationText
                = room.getDuration()
                + " giây";

        // Tạm bỏ listener để tránh
        // tự gửi request khi server update UI
        durationComboBox.setOnAction(null);

        if (!durationComboBox.getItems()
                .contains(durationText)) {

            durationComboBox.getItems().add(
                    durationText
            );
        }

        durationComboBox.setValue(
                durationText
        );

        /*
         * Chỉ Host được chọn thời lượng.
         * Player thường chỉ được xem.
         */
        durationComboBox.setDisable(
                !isHost
        );

        durationComboBox.setOnAction(
                event -> handleDurationChanged()
        );
    }

    // =========================
    // DURATION CHANGED
    // =========================
    private void handleDurationChanged() {

        if (currentRoom == null) {
            return;
        }

        User currentUser
                = session.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        // Chỉ Host được đổi thời lượng
        if (currentRoom.getHostId()
                != currentUser.getId()) {

            return;
        }

        String selected
                = durationComboBox.getValue();

        if (selected == null) {
            return;
        }

        int duration;

        switch (selected) {

            case "45 giây":
                duration = 45;
                break;

            case "60 giây":
                duration = 60;
                break;

            case "90 giây":
                duration = 90;
                break;

            default:
                return;
        }

        sendDuration(duration);
    }

    // =========================
    // SEND DURATION
    // =========================
    private void sendDuration(int duration) {

        try {

            ClientSocket clientSocket
                    = session.getClientSocket();

            if (clientSocket == null
                    || !clientSocket.isConnected()) {

                roomStatusLabel.setText(
                        "Chưa kết nối Server!"
                );

                return;
            }

            Packet request
                    = new Packet(
                            PacketType.ROOM_DURATION_REQ,
                            String.valueOf(duration)
                    );

            clientSocket.sendPacket(
                    request
            );

            System.out.println(
                    "[Room] Host chọn thời lượng: "
                    + duration
                    + " giây"
            );

        } catch (IOException e) {

            System.err.println(
                    "[Room] Lỗi gửi duration: "
                    + e.getMessage()
            );
        }
    }

    // =========================
    // UPDATE BUTTON
    // =========================
    private void updateButton(RoomDTO room) {

        User currentUser
                = session.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        boolean isHost
                = room.getHostId()
                == currentUser.getId();

        if (isHost) {

            /*
             * Host không cần Ready.
             * Host dùng nút này để Start.
             */
            readyButton.setText(
                    "BẮT ĐẦU"
            );

            /*
             * Task 7:
             *
             * Có ít nhất 2 người
             * và không vượt quá 4 người
             * và tất cả người chơi Ready.
             */
            boolean canStart
                    = room.getCurrentPlayers() >= 2
                    && room.getCurrentPlayers()
                    <= room.getMaxPlayers()
                    && areAllPlayersReady(room);

            readyButton.setDisable(
                    !canStart
            );

        } else {

            /*
             * Player thường dùng nút này để Ready.
             */
            boolean isReady
                    = isCurrentUserReady(room);

            if (isReady) {

                readyButton.setText(
                        "ĐÃ SẴN SÀNG"
                );

                readyButton.setDisable(
                        true
                );

            } else {

                readyButton.setText(
                        "SẴN SÀNG"
                );

                readyButton.setDisable(
                        false
                );
            }
        }
    }

    // =========================
    // CHECK CURRENT USER READY
    // =========================
    private boolean isCurrentUserReady(
            RoomDTO room) {

        User currentUser
                = session.getCurrentUser();

        if (currentUser == null
                || room.getReadyStates() == null) {

            return false;
        }

        return room.getReadyStates()
                .getOrDefault(
                        currentUser.getId(),
                        false
                );
    }

    // =========================
    // CHECK ALL READY
    // =========================
    private boolean areAllPlayersReady(
            RoomDTO room) {

        if (room.getReadyStates() == null) {
            return false;
        }

        if (room.getReadyStates().isEmpty()) {
            return false;
        }

        for (Map.Entry<Integer, Boolean> entry
                : room.getReadyStates().entrySet()) {

            if (!Boolean.TRUE.equals(
                    entry.getValue())) {

                return false;
            }
        }

        return true;
    }

    // =========================
    // RECEIVE PACKET
    // =========================
    private void handleServerPacket(
            Packet packet) {

        if (packet == null
                || packet.getType() == null) {

            return;
        }

        switch (packet.getType()) {

            case ROOM_STATE_UPDATE:

                handleRoomStateUpdate(
                        packet.getData()
                );

                break;

            case GAME_START_NOTIFY:

                handleGameStart(
                        packet.getData()
                );

                break;

            default:
                break;
        }
    }

    // =========================
    // ROOM STATE UPDATE
    // =========================
    private void handleRoomStateUpdate(
            String rawJson) {

        try {

            RoomDTO room
                    = gson.fromJson(
                            rawJson,
                            RoomDTO.class
                    );

            if (room == null) {
                return;
            }

            currentRoom = room;

            updateRoomUI(room);

            System.out.println(
                    "[Room] Cập nhật Room: "
                    + room.getRoomName()
                    + " | "
                    + room.getCurrentPlayers()
                    + "/"
                    + room.getMaxPlayers()
                    + " | Duration: "
                    + room.getDuration()
                    + " giây"
            );

        } catch (Exception e) {

            System.err.println(
                    "[Room] Lỗi xử lý ROOM_STATE_UPDATE: "
                    + e.getMessage()
            );
        }
    }

    // =========================
    // BUTTON
    // =========================
    @FXML
    private void handleReady() {

        if (currentRoom == null) {
            return;
        }

        User currentUser
                = session.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        /*
         * Nếu là Host → gửi Start
         */
        if (currentRoom.getHostId()
                == currentUser.getId()) {

            handleStartGame();

            return;
        }

        /*
         * Nếu là Player thường → Ready
         */
        sendReady();
    }

    // =========================
    // SEND READY
    // =========================
    private void sendReady() {

        try {

            ClientSocket clientSocket
                    = session.getClientSocket();

            if (clientSocket == null
                    || !clientSocket.isConnected()) {

                roomStatusLabel.setText(
                        "Chưa kết nối Server!"
                );

                return;
            }

            Packet request
                    = new Packet(
                            PacketType.ROOM_READY_REQ,
                            "true"
                    );

            clientSocket.sendPacket(
                    request
            );

            readyButton.setDisable(
                    true
            );

            readyButton.setText(
                    "ĐÃ SẴN SÀNG"
            );

            System.out.println(
                    "[Room] Đã gửi READY."
            );

        } catch (IOException e) {

            roomStatusLabel.setText(
                    "Không thể gửi trạng thái Ready!"
            );

            System.err.println(
                    "[Room] Lỗi Ready: "
                    + e.getMessage()
            );
        }
    }

    // =========================
    // START GAME
    // =========================
    private void handleStartGame() {

        try {

            ClientSocket clientSocket
                    = session.getClientSocket();

            if (clientSocket == null
                    || !clientSocket.isConnected()) {

                roomStatusLabel.setText(
                        "Chưa kết nối Server!"
                );

                return;
            }

            /*
             * Kiểm tra ở Client trước khi gửi.
             *
             * Server vẫn phải kiểm tra lại
             * để đảm bảo an toàn.
             */
            if (currentRoom == null) {
                return;
            }

            if (currentRoom.getCurrentPlayers()
                    < 2) {

                roomStatusLabel.setText(
                        "Cần ít nhất 2 người để bắt đầu!"
                );

                return;
            }

            if (!areAllPlayersReady(
                    currentRoom)) {

                roomStatusLabel.setText(
                        "Tất cả người chơi phải sẵn sàng!"
                );

                return;
            }

            Packet request
                    = new Packet(
                            PacketType.ROOM_START_REQ,
                            ""
                    );

            clientSocket.sendPacket(
                    request
            );

            readyButton.setDisable(
                    true
            );

            System.out.println(
                    "[Room] Host đã gửi ROOM_START_REQ."
            );

        } catch (IOException e) {

            roomStatusLabel.setText(
                    "Không thể bắt đầu trận!"
            );

            System.err.println(
                    "[Room] Lỗi Start: "
                    + e.getMessage()
            );
        }
    }

    // =========================
    // GAME START
    // =========================
    private void handleGameStart(
            String rawJson) {

        System.out.println(
                "[Room] Nhận GAME_START_NOTIFY!"
        );

        removePacketListener();

        Platform.runLater(() -> {

            try {

                GameCanvasApp gameCanvasApp
                        = new GameCanvasApp();

                Scene gameScene
                        = gameCanvasApp.createGameScene();

                Stage stage
                        = (Stage) roomNameLabel
                                .getScene()
                                .getWindow();

                stage.setScene(
                        gameScene
                );

                stage.setTitle(
                        "Tank 2D - Game"
                );

                stage.setResizable(
                        false
                );

                stage.show();

                System.out.println(
                        "[Game] Đã chuyển sang màn hình Game!"
                );

            } catch (Exception e) {

                System.err.println(
                        "[Game] Không thể mở màn hình Game: "
                        + e.getMessage()
                );
            }
        });
    }

    // =========================
    // LEAVE ROOM
    // =========================
    @FXML
    private void handleLeave() {

        try {

            ClientSocket clientSocket
                    = session.getClientSocket();

            if (clientSocket != null
                    && clientSocket.isConnected()) {

                clientSocket.sendPacket(
                        new Packet(
                                PacketType.ROOM_LEAVE_REQ,
                                ""
                        )
                );

                System.out.println(
                        "[Room] Đã gửi yêu cầu rời phòng."
                );
            }

        } catch (IOException e) {

            System.err.println(
                    "[Room] Lỗi khi rời phòng: "
                    + e.getMessage()
            );
        }

        removePacketListener();

        Platform.runLater(() -> {

            try {

                FXMLLoader loader
                        = new FXMLLoader(
                                RoomController.class.getResource(
                                        "/com/tank2d/client/view/lobby.fxml"
                                )
                        );

                Scene lobbyScene
                        = new Scene(
                                loader.load()
                        );

                Stage stage
                        = (Stage) leaveButton
                                .getScene()
                                .getWindow();

                stage.setScene(
                        lobbyScene
                );

                stage.setTitle(
                        "Tank 2D Online - Lobby"
                );

                stage.show();

                System.out.println(
                        "[Room] Đã quay lại Lobby."
                );

            } catch (IOException e) {

                System.err.println(
                        "[Room] Không thể quay lại Lobby: "
                        + e.getMessage()
                );
            }
        });
    }

    // =========================
    // REMOVE LISTENER
    // =========================
    private void removePacketListener() {

        if (listenerRegistered) {

            session.removePacketListener(
                    packetListener
            );

            listenerRegistered = false;

            System.out.println(
                    "[Room] Đã xóa Room Packet Listener."
            );
        }
    }
}
