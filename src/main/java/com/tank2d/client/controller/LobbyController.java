package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

public class LobbyController {

    @FXML
    private ListView<RoomDTO> roomListView;

    @FXML
    private Button createRoomButton;

    @FXML
    private Button joinRoomButton;

    @FXML
    private Label statusLabel;

    private final Gson gson = new Gson();

    private final ClientSession session
            = ClientSession.getInstance();

    @FXML
    private void initialize() {
        statusLabel.setText("Đang tải danh sách phòng...");

        roomListView.setCellFactory(
                listView -> new javafx.scene.control.ListCell<RoomDTO>() {
            @Override
            protected void updateItem(RoomDTO room, boolean empty) {
                super.updateItem(room, empty);

                if (empty || room == null) {
                    setText(null);
                } else {
                    setText(
                            room.getRoomName()
                            + "   |   "
                            + room.getCurrentPlayers()
                            + "/"
                            + room.getMaxPlayers()
                            + "   |   "
                            + room.getStatus()
                    );
                }
            }
        });

        loadRooms();
    }

    private void loadRooms() {

        Thread roomThread = new Thread(() -> {

            try {
                ClientSocket clientSocket
                        = session.getClientSocket();

                Packet request = new Packet(
                        PacketType.LOBBY_GET_ROOMS_REQ,
                        ""
                );

                Packet response = clientSocket.sendAndReceivePacket(request);

                if (response == null) {
                    showError(
                            "Không nhận được phản hồi từ Server!"
                    );
                    return;
                }

                if (response.getType()
                        != PacketType.LOBBY_ROOMS_RES) {

                    showError(
                            "Server trả về phản hồi không hợp lệ!"
                    );
                    return;
                }

                Type roomListType
                        = new TypeToken<List<RoomDTO>>() {
                        }.getType();

                List<RoomDTO> rooms
                        = gson.fromJson(
                                response.getData(),
                                roomListType
                        );

                Platform.runLater(() -> {

                    roomListView.getItems().clear();
                    roomListView.getItems().addAll(rooms);

                    if (rooms.isEmpty()) {
                        statusLabel.setText(
                                "Hiện chưa có phòng nào."
                        );
                    } else {
                        statusLabel.setText(
                                "Đã tải "
                                + rooms.size()
                                + " phòng."
                        );
                    }
                });

            } catch (IOException e) {

                showError(
                        "Không thể kết nối tới Server!"
                );

                System.err.println(
                        "[Lobby] Lỗi tải phòng: "
                        + e.getMessage()
                );
            }
        });

        roomThread.setDaemon(true);
        roomThread.start();
    }

    @FXML
    private void handleCreateRoom() {

        statusLabel.setText(
                "Đang tạo phòng..."
        );

        Thread createRoomThread = new Thread(() -> {

            try {
                ClientSocket clientSocket
                        = session.getClientSocket();

                Packet request = new Packet(
                        PacketType.ROOM_CREATE_REQ,
                        ""
                );

                Packet response
                        = clientSocket.sendAndReceivePacket(request);

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

                RoomDTO room
                        = gson.fromJson(
                                response.getData(),
                                RoomDTO.class
                        );
                System.out.println(
        "[Lobby] Create response type: "
        + response.getType()
);

System.out.println(
        "[Lobby] Create response data: "
        + response.getData()
);

                openRoom(room);

            } catch (IOException e) {

                showError(
                        "Không thể kết nối tới Server!"
                );

                System.err.println(
                        "[Lobby] Lỗi tạo phòng: "
                        + e.getMessage()
                );
            }
        });

        createRoomThread.setDaemon(true);
        createRoomThread.start();
    }

    @FXML
    private void handleJoinRoom() {

        RoomDTO selectedRoom
                = roomListView
                        .getSelectionModel()
                        .getSelectedItem();

        if (selectedRoom == null) {
            statusLabel.setText(
                    "Vui lòng chọn một phòng!"
            );
            return;
        }

        int selectedRoomId
                = selectedRoom.getRoomId();

        statusLabel.setText(
                "Đang vào "
                + selectedRoom.getRoomName()
                + "..."
        );

        Thread joinRoomThread = new Thread(() -> {

            try {
                ClientSocket clientSocket
                        = session.getClientSocket();

                Packet request = new Packet(
                        PacketType.ROOM_JOIN_REQ,
                        gson.toJson(selectedRoomId)
                );

                Packet response
                        = clientSocket.sendAndReceivePacket(request);
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

                RoomDTO room
                        = gson.fromJson(
                                response.getData(),
                                RoomDTO.class
                        );

                openRoom(room);

            } catch (IOException e) {

                showError(
                        "Không thể kết nối tới Server!"
                );

                System.err.println(
                        "[Lobby] Lỗi vào phòng: "
                        + e.getMessage()
                );
            }
        });

        joinRoomThread.setDaemon(true);
        joinRoomThread.start();
    }

    private void openRoom(RoomDTO room) {

        if (room == null) {
            showError(
                    "Không thể mở phòng!"
            );
            return;
        }

        Platform.runLater(() -> {

            try {
                FXMLLoader loader
                        = new FXMLLoader(
                                getClass().getResource(
                                        "/com/tank2d/client/view/room.fxml"
                                )
                        );

                Parent root = loader.load();

                RoomController controller
                        = loader.getController();

                controller.setRoom(room);

                Stage stage
                        = (Stage) roomListView
                                .getScene()
                                .getWindow();

                stage.setScene(
                        new Scene(root)
                );

                stage.setTitle(
                        "Tank 2D Online - "
                        + room.getRoomName()
                );

                stage.show();

            } catch (IOException e) {

                System.err.println(
                        "[Lobby] Không thể mở Room: "
                        + e.getMessage()
                );

                showError(
                        "Không thể mở giao diện phòng!"
                );
            }
        });
    }

    private void showError(String message) {

        Platform.runLater(() -> {
            statusLabel.setText(message);
        });
    }
}
