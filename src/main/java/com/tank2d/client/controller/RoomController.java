package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

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

            for (int i = 1; i <= room.getCurrentPlayers(); i++) {
                playerListView.getItems().add(
                        "Player " + i
                );
            }
        });
    }

    @FXML
    private void handleReady() {

        try {
            ClientSocket clientSocket
                    = session.getClientSocket();

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

        /*
         * Tạm thời đóng màn hình Room.
         * Logic gửi request rời phòng sẽ hoàn thiện
         * cùng phần xử lý Room State.
         */
        session.getClientSocket();

        try {
            ClientSocket clientSocket
                    = session.getClientSocket();

            if (clientSocket != null) {
                clientSocket.sendPacket(
                        new Packet(
                                PacketType.ROOM_READY_REQ,
                                "false"
                        )
                );
            }

        } catch (IOException e) {

            System.err.println(
                    "[Room] Lỗi khi rời phòng: "
                    + e.getMessage()
            );
        }

        Platform.runLater(() -> {
            leaveButton.setDisable(true);
            roomStatusLabel.setText(
                    "Đã rời phòng."
            );
        });
    }
}
