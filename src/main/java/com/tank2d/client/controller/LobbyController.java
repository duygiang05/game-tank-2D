package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import java.lang.reflect.Type;
import java.util.List;

public class LobbyController {

    @FXML
    private ListView<String> roomListView;

    @FXML
    private Button createRoomButton;

    @FXML
    private Button joinRoomButton;

    @FXML
    private Label statusLabel;

    private final Gson gson = new Gson();

    @FXML
    private void initialize() {
        statusLabel.setText("Đang tải danh sách phòng...");
        loadRooms();
    }

    private void loadRooms() {

        Thread roomThread = new Thread(() -> {

            ClientSocket clientSocket = new ClientSocket();

            try {
                clientSocket.connect();

                // Gửi yêu cầu lấy danh sách phòng
                Packet request = new Packet(
                        PacketType.LOBBY_GET_ROOMS_REQ,
                        ""
                );

                clientSocket.sendPacket(request);

                // Nhận phản hồi từ Server
                Packet response = clientSocket.receivePacket();

                if (response == null) {
                    showError("Không nhận được phản hồi từ Server!");
                    return;
                }

                if (response.getType() != PacketType.LOBBY_ROOMS_RES) {
                    showError("Server trả về phản hồi không hợp lệ!");
                    return;
                }

                // Chuyển JSON thành danh sách RoomDTO
                Type roomListType =
                        new TypeToken<List<RoomDTO>>() {}.getType();

                List<RoomDTO> rooms =
                        gson.fromJson(response.getData(), roomListType);

                Platform.runLater(() -> {

                    roomListView.getItems().clear();

                    for (RoomDTO room : rooms) {
                        String roomInfo =
                                room.getRoomName()
                                + "   |   "
                                + room.getCurrentPlayers()
                                + "/"
                                + room.getMaxPlayers()
                                + "   |   "
                                + room.getStatus();

                        roomListView.getItems().add(roomInfo);
                    }

                    if (rooms.isEmpty()) {
                        statusLabel.setText(
                                "Hiện chưa có phòng nào."
                        );
                    } else {
                        statusLabel.setText(
                                "Đã tải " + rooms.size() + " phòng."
                        );
                    }
                });

            } catch (IOException e) {

                showError(
                        "Không thể kết nối tới Server!\n"
                        + "Vui lòng kiểm tra Server đang chạy."
                );

                System.err.println(
                        "[Lobby] Lỗi kết nối: "
                        + e.getMessage()
                );

            } finally {
                clientSocket.close();
            }

        });

        roomThread.setDaemon(true);
        roomThread.start();
    }

  @FXML
private void handleCreateRoom() {

    statusLabel.setText("Đang tạo phòng...");

    Thread createRoomThread = new Thread(() -> {

        ClientSocket clientSocket = new ClientSocket();

        try {
            clientSocket.connect();

            Packet request = new Packet(
                    PacketType.ROOM_CREATE_REQ,
                    ""
            );

            clientSocket.sendPacket(request);

            Packet response = clientSocket.receivePacket();

            if (response == null) {
                showError("Không nhận được phản hồi từ Server!");
                return;
            }

            if (response.getType() != PacketType.LOBBY_ROOMS_RES) {
                showError("Server trả về phản hồi không hợp lệ!");
                return;
            }

            Type roomListType =
                    new TypeToken<List<RoomDTO>>() {}.getType();

            List<RoomDTO> rooms =
                    gson.fromJson(
                            response.getData(),
                            roomListType
                    );

            Platform.runLater(() -> {

                roomListView.getItems().clear();

                for (RoomDTO room : rooms) {

                    String roomInfo =
                            room.getRoomName()
                            + "   |   "
                            + room.getCurrentPlayers()
                            + "/"
                            + room.getMaxPlayers()
                            + "   |   "
                            + room.getStatus();

                    roomListView.getItems().add(roomInfo);
                }

                statusLabel.setText(
                        "Tạo phòng thành công!"
                );
            });

        } catch (IOException e) {

            showError(
                    "Không thể kết nối tới Server!"
            );

            System.err.println(
                    "[Lobby] Lỗi tạo phòng: "
                    + e.getMessage()
            );

        } finally {
            clientSocket.close();
        }

    });

    createRoomThread.setDaemon(true);
    createRoomThread.start();
}
    @FXML
private void handleJoinRoom() {

    String selectedRoom =
            roomListView.getSelectionModel().getSelectedItem();

    if (selectedRoom == null) {
        statusLabel.setText(
                "Vui lòng chọn một phòng!"
        );
        return;
    }

    // Lấy Room ID từ chuỗi hiển thị
    // Ví dụ: "Room 01   |   1/8   |   Waiting"
    String roomName = selectedRoom.split("\\|")[0].trim();

    int roomId;

    try {
        roomId = Integer.parseInt(
                roomName.replace("Room", "").trim()
        );
    } catch (NumberFormatException e) {
        statusLabel.setText(
                "Không xác định được ID phòng!"
        );
        return;
    }

    final int selectedRoomId = roomId;

    statusLabel.setText(
            "Đang vào " + selectedRoom + "..."
    );

    Thread joinRoomThread = new Thread(() -> {

        ClientSocket clientSocket = new ClientSocket();

        try {
            clientSocket.connect();

            // Gửi yêu cầu vào phòng
            Packet request = new Packet(
                    PacketType.ROOM_JOIN_REQ,
                    gson.toJson(selectedRoomId)
            );

            clientSocket.sendPacket(request);

            // Nhận trạng thái phòng từ Server
            Packet response =
                    clientSocket.receivePacket();

            if (response == null) {
                showError(
                        "Không nhận được phản hồi từ Server!"
                );
                return;
            }

            if (response.getType()
                    != PacketType.ROOM_STATE_UPDATE) {

                showError(
                        "Server trả về phản hồi không hợp lệ!"
                );
                return;
            }

            RoomDTO room =
                    gson.fromJson(
                            response.getData(),
                            RoomDTO.class
                    );

            Platform.runLater(() -> {

                if (room != null) {

                    statusLabel.setText(
                            "Vào phòng thành công! "
                            + room.getRoomName()
                            + " - "
                            + room.getCurrentPlayers()
                            + "/"
                            + room.getMaxPlayers()
                    );

                    // Cập nhật lại danh sách phòng
                    loadRooms();
                } else {

                    statusLabel.setText(
                            "Không thể vào phòng!"
                    );
                }
            });

        } catch (IOException e) {

            showError(
                    "Không thể kết nối tới Server!"
            );

            System.err.println(
                    "[Lobby] Lỗi vào phòng: "
                    + e.getMessage()
            );

        } finally {
            clientSocket.close();
        }

    });

    joinRoomThread.setDaemon(true);
    joinRoomThread.start();
}
private void showError(String message) {
    Platform.runLater(() -> {
        statusLabel.setText(message);
    });
}

}