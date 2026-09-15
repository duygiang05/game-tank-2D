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
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
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
    private ListView<String> playerListView;

    @FXML
    private Button readyButton;

    @FXML
    private Button leaveButton;

    private final Gson gson = new Gson();

    private final ClientSession session =
            ClientSession.getInstance();

    private final Consumer<Packet> packetListener =
            this::handleServerPacket;

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

        updateRoomUI(room);

        if (!listenerRegistered) {

            session.addPacketListener(packetListener);

            listenerRegistered = true;

            System.out.println(
                    "[Room] Đã đăng ký Room Packet Listener."
            );
        }
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
            );

            playerListView.getItems().clear();

            if (room.getPlayerNames() != null) {

                playerListView.getItems().addAll(
                        room.getPlayerNames()
                );
            }

            updateButton(room);
        });
    }

    // =========================
    // UPDATE BUTTON
    // =========================

    private void updateButton(RoomDTO room) {

        User currentUser =
                session.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        boolean isHost =
                room.getHostId()
                == currentUser.getId();

        if (isHost) {

            /*
             * Host không cần Ready.
             * Host dùng nút này để Start.
             */
            readyButton.setText("BẮT ĐẦU");

            /*
             * Chỉ bật Start khi:
             * 1. Đủ người
             * 2. Tất cả người chơi Ready
             */
            boolean canStart =
                    room.getCurrentPlayers()
                    == room.getMaxPlayers()
                    && areAllPlayersReady(room);

            readyButton.setDisable(!canStart);

        } else {

            /*
             * Player thường dùng nút này để Ready.
             */
            boolean isReady =
                    isCurrentUserReady(room);

            if (isReady) {

                readyButton.setText(
                        "ĐÃ SẴN SÀNG"
                );

                readyButton.setDisable(true);

            } else {

                readyButton.setText(
                        "SẴN SÀNG"
                );

                readyButton.setDisable(false);
            }
        }
    }

    // =========================
    // CHECK CURRENT USER READY
    // =========================

    private boolean isCurrentUserReady(
            RoomDTO room) {

        User currentUser =
                session.getCurrentUser();

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

        for (Map.Entry<Integer, Boolean> entry :
                room.getReadyStates().entrySet()) {

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

            RoomDTO room =
                    gson.fromJson(
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

        User currentUser =
                session.getCurrentUser();

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

            ClientSocket clientSocket =
                    session.getClientSocket();

            if (clientSocket == null
                    || !clientSocket.isConnected()) {

                roomStatusLabel.setText(
                        "Chưa kết nối Server!"
                );

                return;
            }

            Packet request =
                    new Packet(
                            PacketType.ROOM_READY_REQ,
                            "true"
                    );

            clientSocket.sendPacket(request);

            readyButton.setDisable(true);

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

            ClientSocket clientSocket =
                    session.getClientSocket();

            if (clientSocket == null
                    || !clientSocket.isConnected()) {

                roomStatusLabel.setText(
                        "Chưa kết nối Server!"
                );

                return;
            }

            Packet request =
                    new Packet(
                            PacketType.ROOM_START_REQ,
                            ""
                    );

            clientSocket.sendPacket(request);

            readyButton.setDisable(true);

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

                GameCanvasApp gameCanvasApp =
                        new GameCanvasApp();

                Scene gameScene =
                        gameCanvasApp.createGameScene();

                Stage stage =
                        (Stage) roomNameLabel
                                .getScene()
                                .getWindow();

                stage.setScene(gameScene);

                stage.setTitle(
                        "Tank 2D - Game"
                );

                stage.setResizable(false);

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

            ClientSocket clientSocket =
                    session.getClientSocket();

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

                FXMLLoader loader =
                        new FXMLLoader(
                                RoomController.class.getResource(
                                        "/com/tank2d/client/view/lobby.fxml"
                                )
                        );

                Scene lobbyScene =
                        new Scene(
                                loader.load()
                        );

                Stage stage =
                        (Stage) leaveButton
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