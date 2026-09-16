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
import java.util.function.Consumer;

public class LobbyController {

    @FXML
    private ListView<RoomDTO> roomListView;

    @FXML
    private Button createRoomButton;

    @FXML
    private Button joinRoomButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Label usernameLabel;

    private final Gson gson = new Gson();

    private final ClientSession session =
            ClientSession.getInstance();

    /**
     * Listener nhận packet từ ClientSession.
     * ClientSession là nơi duy nhất đọc socket.
     */
    private final Consumer<Packet> packetListener =
            this::handleServerPacket;

    @FXML
    private void initialize() {

        // Đăng ký nhận packet từ Server
        session.addPacketListener(packetListener);

        // Hiển thị username
        if (session.getCurrentUser() != null) {

            usernameLabel.setText(
                    "Hello, "
                    + session.getCurrentUser().getUsername()
            );

        } else {

            usernameLabel.setText("Hello");
        }

        statusLabel.setText(
                "Đang tải danh sách phòng..."
        );

        // Cấu hình hiển thị từng phòng
        roomListView.setCellFactory(
                listView ->
                        new javafx.scene.control.ListCell<RoomDTO>() {

                    @Override
                    protected void updateItem(
                            RoomDTO room,
                            boolean empty) {

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
                }
        );

        // Yêu cầu danh sách phòng
        loadRooms();
    }

    /**
     * Yêu cầu Server gửi danh sách phòng hiện tại.
     *
     * Chỉ gửi request.
     * Không tự đọc socket.
     */
    private void loadRooms() {

        try {

            ClientSocket clientSocket =
                    session.getClientSocket();

            if (clientSocket == null
                    || !clientSocket.isConnected()) {

                showError(
                        "Chưa kết nối tới Server!"
                );
                return;
            }

            Packet request = new Packet(
                    PacketType.LOBBY_GET_ROOMS_REQ,
                    ""
            );

            clientSocket.sendPacket(request);

        } catch (IOException e) {

            showError(
                    "Không thể kết nối tới Server!"
            );

            System.err.println(
                    "[Lobby] Lỗi tải phòng: "
                    + e.getMessage()
            );
        }
    }

    /**
     * Tạo phòng.
     *
     * Chỉ gửi request.
     * ROOM_STATE_UPDATE sẽ được ClientSession
     * chuyển vào handleServerPacket().
     */
    @FXML
    private void handleCreateRoom() {

        statusLabel.setText(
                "Đang tạo phòng..."
        );

        try {

            ClientSocket clientSocket =
                    session.getClientSocket();

            if (clientSocket == null
                    || !clientSocket.isConnected()) {

                showError(
                        "Chưa kết nối tới Server!"
                );
                return;
            }

            Packet request = new Packet(
                    PacketType.ROOM_CREATE_REQ,
                    ""
            );

            clientSocket.sendPacket(request);

        } catch (IOException e) {

            showError(
                    "Không thể kết nối tới Server!"
            );

            System.err.println(
                    "[Lobby] Lỗi tạo phòng: "
                    + e.getMessage()
            );
        }
    }

    /**
     * Vào phòng được chọn.
     */
    @FXML
    private void handleJoinRoom() {

        RoomDTO selectedRoom =
                roomListView
                        .getSelectionModel()
                        .getSelectedItem();

        if (selectedRoom == null) {

            statusLabel.setText(
                    "Vui lòng chọn một phòng!"
            );
            return;
        }

        int selectedRoomId =
                selectedRoom.getRoomId();

        statusLabel.setText(
                "Đang vào "
                + selectedRoom.getRoomName()
                + "..."
        );

        try {

            ClientSocket clientSocket =
                    session.getClientSocket();

            if (clientSocket == null
                    || !clientSocket.isConnected()) {

                showError(
                        "Chưa kết nối tới Server!"
                );
                return;
            }

            Packet request = new Packet(
                    PacketType.ROOM_JOIN_REQ,
                    gson.toJson(selectedRoomId)
            );

            clientSocket.sendPacket(request);

        } catch (IOException e) {

            showError(
                    "Không thể kết nối tới Server!"
            );

            System.err.println(
                    "[Lobby] Lỗi vào phòng: "
                    + e.getMessage()
            );
        }
    }

    /**
     * ClientSession gọi hàm này khi có packet từ Server.
     */
    private void handleServerPacket(Packet packet) {

        if (packet == null
                || packet.getType() == null) {

            return;
        }

        switch (packet.getType()) {

            /*
             * Server trả danh sách phòng.
             */
            case LOBBY_ROOMS_RES:

                handleLobbyRoomsUpdate(
                        packet.getData()
                );

                break;

            /*
             * Server trả thông tin phòng
             * sau khi Create hoặc Join.
             */
            case ROOM_STATE_UPDATE:

                handleRoomStateUpdate(
                        packet.getData()
                );

                break;

            default:

                System.out.println(
                        "[Lobby] Nhận packet: "
                        + packet.getType()
                );

                break;
        }
    }

    /**
     * Cập nhật danh sách phòng trong Lobby.
     */
    private void handleLobbyRoomsUpdate(
            String rawJson) {

        try {

            Type roomListType =
                    new TypeToken<List<RoomDTO>>() {
                    }.getType();

            List<RoomDTO> rooms =
                    gson.fromJson(
                            rawJson,
                            roomListType
                    );

            if (rooms == null) {
                return;
            }

            Platform.runLater(() -> {

                roomListView.getItems().clear();

                roomListView.getItems().addAll(
                        rooms
                );

                if (rooms.isEmpty()) {

                    statusLabel.setText(
                            "Hiện chưa có phòng nào."
                    );

                } else {

                    statusLabel.setText(
                            "Đã cập nhật "
                            + rooms.size()
                            + " phòng."
                    );
                }
            });

        } catch (Exception e) {

            System.err.println(
                    "[Lobby] Lỗi cập nhật danh sách phòng: "
                    + e.getMessage()
            );
        }
    }

    /**
     * Xử lý ROOM_STATE_UPDATE.
     *
     * Được dùng cho cả:
     * - Tạo phòng
     * - Vào phòng
     */
    private void handleRoomStateUpdate(
            String rawJson) {

        try {

            RoomDTO room =
                    gson.fromJson(
                            rawJson,
                            RoomDTO.class
                    );

            if (room == null) {

                showError(
                        "Không thể xử lý thông tin phòng!"
                );
                return;
            }

            System.out.println(
                    "[Lobby] Nhận thông tin phòng: "
                    + room.getRoomName()
            );

            openRoom(room);

        } catch (Exception e) {

            System.err.println(
                    "[Lobby] Lỗi xử lý "
                    + "ROOM_STATE_UPDATE: "
                    + e.getMessage()
            );

            showError(
                    "Không thể xử lý thông tin phòng!"
            );
        }
    }

    /**
     * Mở giao diện Room.
     */
    private void openRoom(RoomDTO room) {

        if (room == null) {

            showError(
                    "Không thể mở phòng!"
            );
            return;
        }

        Platform.runLater(() -> {

            try {

                FXMLLoader loader =
                        new FXMLLoader(
                                getClass().getResource(
                                        "/com/tank2d/client/view/room.fxml"
                                )
                        );

                Parent root =
                        loader.load();

                RoomController controller =
                        loader.getController();
                
                session.removePacketListener(packetListener);

                controller.setRoom(room);

                Stage stage =
                        (Stage) roomListView
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

    /**
     * Hiển thị lỗi trên giao diện.
     */
    private void showError(String message) {

        Platform.runLater(() -> {

            statusLabel.setText(
                    message
            );
        });
    }
}