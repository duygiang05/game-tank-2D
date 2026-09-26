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
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
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
    private Button leaderboardButton;

    @FXML
    private Button logoutButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Label usernameLabel;

    @FXML
    private ComboBox<String> durationFilterComboBox;

    private List<RoomDTO> allRooms = new ArrayList<>();
    private final Gson gson = new Gson();
    private final ClientSession session = ClientSession.getInstance();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    @FXML
    private void initialize() {
        // Đăng ký nhận packet từ Reader Thread của ClientSession
        session.addPacketListener(packetListener);

        // Cấu hình bộ lọc thời gian
        durationFilterComboBox.getItems().addAll("Tất cả", "45s", "60s", "90s");
        durationFilterComboBox.setValue("Tất cả");
        durationFilterComboBox.setOnAction(event -> applyDurationFilter());

        // Hiển thị tên người dùng
        if (session.getCurrentUser() != null) {
            usernameLabel.setText(session.getCurrentUser().getUsername());
        } else {
            usernameLabel.setText("Player");
        }

        statusLabel.setText("Đang tải danh sách phòng...");

        // Render từng dòng phòng: Để nền trong suốt để khi click vào sẽ ăn style CSS nổi bật (.list-cell:selected)
        roomListView.setCellFactory(listView -> new ListCell<RoomDTO>() {
            @Override
            protected void updateItem(RoomDTO room, boolean empty) {
                super.updateItem(room, empty);

                if (empty || room == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Cột 1: Tên phòng (260px)
                    Label nameLabel = new Label("🎮  " + room.getRoomName());
                    nameLabel.setPrefWidth(260);
                    nameLabel.setAlignment(Pos.CENTER_LEFT);
                    nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 13.5px;");

                    // Cột 2: Số người chơi (110px)
                    Label playersLabel = new Label(room.getCurrentPlayers() + " / " + room.getMaxPlayers());
                    playersLabel.setPrefWidth(110);
                    playersLabel.setAlignment(Pos.CENTER);
                    playersLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2563eb; -fx-font-size: 13px;");

                    // Cột 3: Thời lượng (120px)
                    Label durationLabel = new Label("⏱ " + room.getDuration() + "s");
                    durationLabel.setPrefWidth(120);
                    durationLabel.setAlignment(Pos.CENTER);
                    durationLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

                    // Cột 4: Trạng thái (140px)
                    String status = room.getStatus() != null ? room.getStatus() : "WAITING";
                    boolean isWaiting = status.equalsIgnoreCase("WAITING") || status.toLowerCase().contains("chờ");
                    Label statusLabelCell = new Label(isWaiting ? "● Đang chờ" : "▶ Đang chơi");
                    statusLabelCell.setPrefWidth(140);
                    statusLabelCell.setAlignment(Pos.CENTER_RIGHT);

                    if (isWaiting) {
                        statusLabelCell.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-font-size: 12.5px;");
                    } else {
                        statusLabelCell.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 12.5px;");
                    }

                    HBox row = new HBox(nameLabel, playersLabel, durationLabel, statusLabelCell);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-padding: 6px 10px; -fx-background-color: transparent;");

                    setGraphic(row);
                    setText(null);
                }
            }
        });

        // Bắn request lấy danh sách phòng hiện tại
        loadRooms();
    }

    private void loadRooms() {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                showError("Chưa kết nối tới Server!");
                return;
            }

            Packet request = new Packet(PacketType.LOBBY_GET_ROOMS_REQ, "");
            clientSocket.sendPacket(request);

        } catch (IOException e) {
            showError("Không thể kết nối tới Server!");
            System.err.println("[Lobby] Lỗi tải phòng: " + e.getMessage());
        }
    }

    @FXML
    private void handleCreateRoom() {
        statusLabel.setText("Đang tạo phòng...");

        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                showError("Chưa kết nối tới Server!");
                return;
            }

            Packet request = new Packet(PacketType.ROOM_CREATE_REQ, "");
            clientSocket.sendPacket(request);

        } catch (IOException e) {
            showError("Không thể kết nối tới Server!");
            System.err.println("[Lobby] Lỗi tạo phòng: " + e.getMessage());
        }
    }

    @FXML
    private void handleJoinRoom() {
        RoomDTO selectedRoom = roomListView.getSelectionModel().getSelectedItem();

        if (selectedRoom == null) {
            statusLabel.setText("Vui lòng chọn một phòng trong danh sách!");
            return;
        }

        int selectedRoomId = selectedRoom.getRoomId();
        statusLabel.setText("Đang vào " + selectedRoom.getRoomName() + "...");

        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                showError("Chưa kết nối tới Server!");
                return;
            }

            Packet request = new Packet(
                    PacketType.ROOM_JOIN_REQ,
                    gson.toJson(selectedRoomId)
            );
            clientSocket.sendPacket(request);

        } catch (IOException e) {
            showError("Không thể kết nối tới Server!");
            System.err.println("[Lobby] Lỗi vào phòng: " + e.getMessage());
        }
    }

    private void handleServerPacket(Packet packet) {
        if (packet == null || packet.getType() == null) {
            return;
        }

        switch (packet.getType()) {
            case LOBBY_ROOMS_RES:
            case ROOM_LIST_UPDATE:
                handleLobbyRoomsUpdate(packet.getData());
                break;

            case ROOM_STATE_UPDATE:
                handleRoomStateUpdate(packet.getData());
                break;

            default:
                break;
        }
    }

    private void handleLobbyRoomsUpdate(String rawJson) {
        try {
            Type roomListType = new TypeToken<List<RoomDTO>>() {}.getType();
            List<RoomDTO> rooms = gson.fromJson(rawJson, roomListType);

            if (rooms == null) {
                return;
            }

            Platform.runLater(() -> {
                allRooms = rooms;
                applyDurationFilter();
            });

        } catch (Exception e) {
            System.err.println("[Lobby] Lỗi cập nhật danh sách phòng: " + e.getMessage());
        }
    }

    private void applyDurationFilter() {
        String selectedFilter = durationFilterComboBox.getValue();
        if (selectedFilter == null) {
            selectedFilter = "Tất cả";
        }

        roomListView.getItems().clear();

        if (selectedFilter.equals("Tất cả")) {
            roomListView.getItems().addAll(allRooms);
        } else {
            int duration = Integer.parseInt(selectedFilter.replace("s", ""));
            for (RoomDTO room : allRooms) {
                if (room.getDuration() == duration) {
                    roomListView.getItems().add(room);
                }
            }
        }

        int count = roomListView.getItems().size();
        if (count == 0) {
            statusLabel.setText("Hiện không có phòng nào phù hợp.");
        } else {
            statusLabel.setText("Đang hiển thị " + count + " phòng chờ.");
        }
    }

    private void handleRoomStateUpdate(String rawJson) {
        try {
            RoomDTO room = gson.fromJson(rawJson, RoomDTO.class);
            if (room == null) {
                showError("Không thể xử lý thông tin phòng!");
                return;
            }

            System.out.println("[Lobby] Vào phòng thành công: " + room.getRoomName());
            openRoom(room);

        } catch (Exception e) {
            System.err.println("[Lobby] Lỗi xử lý ROOM_STATE_UPDATE: " + e.getMessage());
            showError("Không thể xử lý thông tin phòng!");
        }
    }

    private void openRoom(RoomDTO room) {
        Platform.runLater(() -> {
            try {
                // Gỡ packet listener của sảnh trước khi mở phòng
                session.removePacketListener(packetListener);

                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/tank2d/client/view/room.fxml")
                );
                Parent root = loader.load();

                RoomController controller = loader.getController();
                controller.setRoom(room);

                Stage stage = (Stage) roomListView.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.setTitle("Tank 2D Online - " + room.getRoomName());
                stage.show();

            } catch (IOException e) {
                // Nếu load thất bại thì hồi phục listener
                session.addPacketListener(packetListener);
                System.err.println("[Lobby] Không thể mở Room: " + e.getMessage());
                showError("Không thể mở giao diện phòng!");
            }
        });
    }

    @FXML
    private void handleLogout() {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket != null && clientSocket.isConnected()) {
                Packet request = new Packet(PacketType.LOGOUT_REQ, "");
                clientSocket.sendPacket(request);
            }

            session.removePacketListener(packetListener);
            session.close();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/tank2d/client/view/login.fxml")
            );
            Parent root = loader.load();

            Stage stage = (Stage) logoutButton.getScene().getWindow();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Tank 2D Online - Login");
            stage.show();

        } catch (IOException e) {
            System.err.println("[Lobby] Lỗi Logout: " + e.getMessage());
            showError("Không thể đăng xuất!");
        }
    }

    @FXML
    private void handleLeaderboard() {
        try {
            // Gỡ listener của sảnh TRƯỚC để LeaderboardController làm việc chuẩn xác
            session.removePacketListener(packetListener);

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/tank2d/client/view/leaderboard.fxml")
            );
            Parent root = loader.load();

            Stage stage = (Stage) leaderboardButton.getScene().getWindow();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Tank 2D Online - Leaderboard");
            stage.show();

        } catch (IOException e) {
            session.addPacketListener(packetListener);
            System.err.println("[Lobby] Không thể mở Leaderboard: " + e.getMessage());
            showError("Không thể mở bảng xếp hạng!");
        }
    }

    private void showError(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }
}