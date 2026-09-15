package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.client.view.GameCanvasApp;
import com.tank2d.common.dto.RoomDTO;
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

    private final ClientSession session
            = ClientSession.getInstance();

    private RoomDTO currentRoom;

    private volatile boolean listening = true;

    public void setRoom(RoomDTO room) {
        this.currentRoom = room;

        if (room == null) {
            return;
        }

        Platform.runLater(() -> {
            roomNameLabel.setText(room.getRoomName());

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
        });

        // Bắt đầu lắng nghe Server
        startServerListener();
    }

    /**
     * Luồng riêng lắng nghe các packet Server gửi xuống
     */
    private void startServerListener() {

        Thread listenerThread = new Thread(() -> {

            try {
                ClientSocket clientSocket = session.getClientSocket();

                if (clientSocket == null) {
                    System.err.println(
                            "[Room] ClientSocket không tồn tại!"
                    );
                    return;
                }

                System.out.println(
                        "[Room] Bắt đầu lắng nghe Server..."
                );

                while (listening && clientSocket.isConnected()) {

                    Packet packet = clientSocket.receivePacket();

                    if (packet == null) {
                        break;
                    }

                    handleServerPacket(packet);
                }

            } catch (IOException e) {

                if (listening) {
                    System.err.println(
                            "[Room] Lỗi nhận packet từ Server: "
                            + e.getMessage()
                    );
                }
            }

        });

        listenerThread.setDaemon(true);
        listenerThread.setName("Room-Server-Listener");
        listenerThread.start();
    }

    /**
     * Xử lý packet Server gửi xuống
     */
    private void handleServerPacket(Packet packet) {

        if (packet.getType() == null) {
            return;
        }

        switch (packet.getType()) {

            case ROOM_STATE_UPDATE:
                handleRoomStateUpdate(packet.getData());
                break;

            case GAME_START_NOTIFY:
                handleGameStart(packet.getData());
                break;

            default:
                System.out.println(
                        "[Room] Nhận packet: "
                        + packet.getType()
                );
                break;
        }
    }

    /**
     * Đồng bộ trạng thái phòng
     */
    private void handleRoomStateUpdate(String rawJson) {

        try {

            RoomDTO room = gson.fromJson(
                    rawJson,
                    RoomDTO.class
            );

            if (room == null) {
                return;
            }

            currentRoom = room;

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
            });

        } catch (Exception e) {

            System.err.println(
                    "[Room] Lỗi xử lý ROOM_STATE_UPDATE: "
                    + e.getMessage()
            );
        }
    }

    /**
     * Nhận tín hiệu bắt đầu trận
     */
    private void handleGameStart(String rawJson) {

        System.out.println(
                "[Room] Nhận GAME_START_NOTIFY!"
        );

        listening = false;

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

                stage.setScene(gameScene);
                stage.setTitle("Tank 2D - Game");
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

    @FXML
    private void handleReady() {

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

            Packet request = new Packet(
                    PacketType.ROOM_READY_REQ,
                    "true"
            );

            clientSocket.sendPacket(request);

            readyButton.setDisable(true);
            readyButton.setText("ĐÃ SẴN SÀNG");

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
            }

        } catch (IOException e) {

            System.err.println(
                    "[Room] Lỗi khi rời phòng: "
                    + e.getMessage()
            );
        }

        listening = false;

        Platform.runLater(() -> {

            try {
                FXMLLoader loader = new FXMLLoader(
                        RoomController.class.getResource(
                                "/com/tank2d/client/view/lobby.fxml"
                        )
                );

                Scene lobbyScene = new Scene(loader.load());

                Stage stage
                        = (Stage) leaveButton
                                .getScene()
                                .getWindow();

                stage.setScene(lobbyScene);
                stage.setTitle("Tank 2D Online - Lobby");
                stage.show();

            } catch (IOException e) {

                System.err.println(
                        "[Room] Không thể quay lại Lobby: "
                        + e.getMessage()
                );
            }
        });
    }
}
