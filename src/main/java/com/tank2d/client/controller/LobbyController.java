package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.client.view.GameCanvasApp;
import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.dto.game.ReconnectPromptDTO;
import com.tank2d.common.dto.game.ReconnectResponseDTO;
import com.tank2d.common.model.User;
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
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import com.tank2d.common.exception.GameNetworkException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LobbyController {

    private static final Logger LOGGER = Logger.getLogger(LobbyController.class.getName());

    @FXML
    private ListView<RoomDTO> roomListView;

    @FXML
    private Button createRoomButton;

    @FXML
    private Button joinRoomButton;

    @FXML
    private Button leaderboardButton;

    @FXML
    private Button matchHistoryButton;

    @FXML
    private Button logoutButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Label usernameLabel;

    @FXML
    private ComboBox<String> durationFilterComboBox;

    @FXML
    private BorderPane mainContentPane;

    @FXML
    private StackPane penaltyOverlay;

    @FXML
    private StackPane reconnectOverlay;

    @FXML
    private Label reconnectRoomLabel;

    @FXML
    private Label reconnectTimeLabel;

    @FXML
    private Button skipReconnectButton;

    @FXML
    private Button confirmReconnectButton;

    private List<RoomDTO> allRooms = new ArrayList<>();
    private final Gson gson = new Gson();
    private final ClientSession session = ClientSession.getInstance();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    private ReconnectPromptDTO pendingReconnectMatch = null;
    private boolean isPenalized = false;
    private int penalizedRoomId = -1;

    @FXML
    private void initialize() {
        // Đăng ký nhận packet từ Reader Thread của ClientSession
        session.addPacketListener(packetListener);

        // Cấu hình bộ lọc thời gian
        durationFilterComboBox.getItems().addAll("Tất cả", "45s", "60s", "90s", "180s");
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

        } catch (GameNetworkException e) {
            showError(e.getMessage());
            LOGGER.log(Level.WARNING, "[Lobby] Lỗi mạng khi tải phòng: " + e.getErrorCode(), e);
        } catch (IOException e) {
            showError("Không thể kết nối tới Server!");
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi tải phòng", e);
        }
    }

    @FXML
    private void handleCreateRoom() {
        if (isPenalized) {
            penaltyOverlay.setVisible(true);
            mainContentPane.setEffect(new GaussianBlur(12));
            return;
        }

        if (pendingReconnectMatch != null) {
            showReconnectModal(pendingReconnectMatch);
            return;
        }

        statusLabel.setText("Đang tạo phòng...");

        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                showError("Chưa kết nối tới Server!");
                return;
            }

            Packet request = new Packet(PacketType.ROOM_CREATE_REQ, "");
            clientSocket.sendPacket(request);

        } catch (GameNetworkException e) {
            showError(e.getMessage());
            LOGGER.log(Level.WARNING, "[Lobby] Lỗi mạng khi tạo phòng: " + e.getErrorCode(), e);
        } catch (IOException e) {
            showError("Không thể kết nối tới Server!");
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi tạo phòng", e);
        }
    }

    @FXML
    private void handleJoinRoom() {
        if (isPenalized) {
            penaltyOverlay.setVisible(true);
            mainContentPane.setEffect(new GaussianBlur(12));
            return;
        }

        if (pendingReconnectMatch != null) {
            showReconnectModal(pendingReconnectMatch);
            return;
        }

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

        } catch (GameNetworkException e) {
            showError(e.getMessage());
            LOGGER.log(Level.WARNING, "[Lobby] Lỗi mạng khi vào phòng: " + e.getErrorCode(), e);
        } catch (IOException e) {
            showError("Không thể kết nối tới Server!");
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi vào phòng", e);
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

            case GAME_PENALTY_NOTIFY:
                handlePenaltyNotify(packet.getData());
                break;

            case GAME_RECONNECT_PROMPT:
                handleReconnectPrompt(packet.getData());
                break;

            case GAME_RECONNECT_RES:
                handleReconnectResponse(packet.getData());
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

                // 1. Kiểm tra tự động gỡ phạt nếu phòng bị phạt đã kết thúc hoặc không còn trong danh sách Playing
                if (isPenalized && penalizedRoomId != -1) {
                    boolean penRoomStillPlaying = false;
                    for (RoomDTO r : rooms) {
                        if (r.getRoomId() == penalizedRoomId && "Playing".equalsIgnoreCase(r.getStatus())) {
                            penRoomStillPlaying = true;
                            break;
                        }
                    }
                    if (!penRoomStillPlaying) {
                        isPenalized = false;
                        penalizedRoomId = -1;
                        if (penaltyOverlay != null) {
                            penaltyOverlay.setVisible(false);
                        }
                        if (mainContentPane != null && (reconnectOverlay == null || !reconnectOverlay.isVisible())) {
                            mainContentPane.setEffect(null);
                        }
                        statusLabel.setText("Trận đấu trước đó đã kết thúc. Hình phạt đã được gỡ bỏ.");
                        statusLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                    }
                }

                // 2. Kiểm tra xem trận đấu dở dang (reconnect) đã kết thúc chưa
                if (pendingReconnectMatch != null) {
                    boolean matchStillActive = false;
                    for (RoomDTO r : rooms) {
                        if (r.getRoomId() == pendingReconnectMatch.getRoomId() && "Playing".equalsIgnoreCase(r.getStatus())) {
                            matchStillActive = true;
                            break;
                        }
                    }
                    if (!matchStillActive) {
                        pendingReconnectMatch = null;
                        if (reconnectOverlay != null && reconnectOverlay.isVisible()) {
                            reconnectOverlay.setVisible(false);
                        }
                        if (mainContentPane != null && !isPenalized) {
                            mainContentPane.setEffect(null);
                        }
                        statusLabel.setText("Trận đấu trước đó đã kết thúc.");
                    }
                }
            });

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi cập nhật danh sách phòng", e);
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

            // KIỂM TRA ĐIỀU KIỆN TIÊN QUYẾT: TÊN MÌNH PHẢI CÓ TRONG PHÒNG
            User currentUser = session.getCurrentUser();
            boolean isJoined = currentUser != null 
                    && room.getPlayerNames() != null 
                    && room.getPlayerNames().contains(currentUser.getUsername());

            if (isJoined) {
                LOGGER.info("[Lobby] Vào phòng thành công: " + room.getRoomName());
                openRoom(room);
            } else {
                // Nếu mình không có trong phòng (do phòng đang chơi hoặc đã đầy bị Server từ chối)
                LOGGER.warning("[Lobby] Không thể vào phòng (bị Server từ chối): " + room.getRoomName());
                showError("Không thể vào phòng! Phòng đang chiến đấu hoặc đã đầy.");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi xử lý ROOM_STATE_UPDATE", e);
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

                if (mainContentPane != null) {
                    mainContentPane.setEffect(null);
                }

                RoomController controller = loader.getController();
                controller.setRoom(room);

                Stage stage = (Stage) roomListView.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.setTitle("Tank 2D Online - " + room.getRoomName());
                stage.show();

            } catch (IOException e) {
                // Nếu load thất bại thì hồi phục listener
                session.addPacketListener(packetListener);
                LOGGER.log(Level.SEVERE, "[Lobby] Không thể mở Room", e);
                showError("Không thể mở giao diện phòng!");
            }
        });
    }

    @FXML
    private void handleLogout() {
        try {
            if (mainContentPane != null) {
                mainContentPane.setEffect(null);
            }

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
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi Logout", e);
            showError("Không thể đăng xuất!");
        }
    }

    @FXML
    private void handleLeaderboard() {
        if (isPenalized) {
            penaltyOverlay.setVisible(true);
            mainContentPane.setEffect(new GaussianBlur(12));
            return;
        }

        if (pendingReconnectMatch != null) {
            showReconnectModal(pendingReconnectMatch);
            return;
        }

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
            LOGGER.log(Level.SEVERE, "[Lobby] Không thể mở Leaderboard", e);
            showError("Không thể mở bảng xếp hạng!");
        }
    }

    @FXML
    private void handleMatchHistory() {
        if (isPenalized) {
            penaltyOverlay.setVisible(true);
            mainContentPane.setEffect(new GaussianBlur(12));
            return;
        }

        if (pendingReconnectMatch != null) {
            showReconnectModal(pendingReconnectMatch);
            return;
        }

        try {
            session.removePacketListener(packetListener);

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/tank2d/client/view/match_history.fxml")
            );
            Parent root = loader.load();

            Stage stage = (Stage) matchHistoryButton.getScene().getWindow();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Tank 2D Online - Lịch sử thi đấu");
            stage.show();

        } catch (IOException e) {
            session.addPacketListener(packetListener);
            LOGGER.log(Level.SEVERE, "[Lobby] Không thể mở Lịch sử đấu", e);
            showError("Không thể mở lịch sử đấu!");
        }
    }

    private void handlePenaltyNotify(String message) {
        Platform.runLater(() -> {
            if ("PENALTY_LIFTED".equals(message)) {
                isPenalized = false;
                penalizedRoomId = -1;
                if (penaltyOverlay != null) {
                    penaltyOverlay.setVisible(false);
                }
                if (mainContentPane != null && (reconnectOverlay == null || !reconnectOverlay.isVisible())) {
                    mainContentPane.setEffect(null);
                }
                statusLabel.setText("Hình phạt đã kết thúc. Bạn có thể tiếp tục chơi.");
                statusLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                return;
            }

            if (message != null && message.startsWith("PENALTY_ACTIVE:")) {
                String[] parts = message.split(":", 2);
                if (parts.length > 1) {
                    try {
                        penalizedRoomId = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException ignored) {}
                }
            }

            isPenalized = true;
            if (penaltyOverlay != null) {
                penaltyOverlay.setVisible(true);
            }
            if (mainContentPane != null) {
                mainContentPane.setEffect(new GaussianBlur(12));
            }
            statusLabel.setText("Tài khoản đang bị phạt do thoát trận giữa chừng.");
            statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
        });
    }

    private void handleReconnectPrompt(String rawJson) {
        try {
            ReconnectPromptDTO promptDTO = gson.fromJson(rawJson, ReconnectPromptDTO.class);
            if (promptDTO == null) return;
            pendingReconnectMatch = promptDTO;

            Platform.runLater(() -> {
                showReconnectModal(promptDTO);
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi xử lý GAME_RECONNECT_PROMPT", e);
        }
    }

    private void showReconnectModal(ReconnectPromptDTO promptDTO) {
        if (promptDTO == null) return;
        if (reconnectRoomLabel != null) {
            reconnectRoomLabel.setText("Phòng đấu: " + promptDTO.getRoomName());
        }
        if (reconnectTimeLabel != null) {
            int remainSec = (int) promptDTO.getRemainingTime();
            reconnectTimeLabel.setText(String.format("Thời gian còn lại: khoảng %02d:%02d", remainSec / 60, remainSec % 60));
        }
        if (reconnectOverlay != null) {
            reconnectOverlay.setVisible(true);
        }
        if (mainContentPane != null) {
            mainContentPane.setEffect(new GaussianBlur(12));
        }
        statusLabel.setText("Bạn đang có một trận đấu dở dang tại " + promptDTO.getRoomName());
        statusLabel.setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;");
    }

    @FXML
    private void handleSkipReconnect() {
        if (reconnectOverlay != null) {
            reconnectOverlay.setVisible(false);
        }
        if (mainContentPane != null && !isPenalized) {
            mainContentPane.setEffect(null);
        }
        statusLabel.setText("Bạn đang có một trận đấu dở dang. Hãy vào lại khi sẵn sàng.");
        statusLabel.setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;");
    }

    @FXML
    private void handleConfirmReconnect() {
        try {
            statusLabel.setText("Đang kết nối lại vào trận đấu...");
            statusLabel.setStyle("-fx-text-fill: #2563eb; -fx-font-weight: bold;");
            if (reconnectOverlay != null) {
                reconnectOverlay.setVisible(false);
            }
            if (mainContentPane != null) {
                mainContentPane.setEffect(null);
            }

            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket != null && clientSocket.isConnected()) {
                clientSocket.sendPacket(new Packet(PacketType.GAME_RECONNECT_REQ, ""));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi gửi GAME_RECONNECT_REQ", e);
        }
    }

    private void handleReconnectResponse(String rawJson) {
        try {
            ReconnectResponseDTO resDTO = gson.fromJson(rawJson, ReconnectResponseDTO.class);
            if (resDTO == null || resDTO.getRoom() == null) {
                showError("Không thể khôi phục dữ liệu trận đấu!");
                return;
            }

            Platform.runLater(() -> {
                try {
                    session.removePacketListener(packetListener);
                    pendingReconnectMatch = null;
                    isPenalized = false;

                    if (mainContentPane != null) {
                        mainContentPane.setEffect(null);
                    }

                    Stage stage = (Stage) roomListView.getScene().getWindow();
                    stage.setMinWidth(0);
                    stage.setMinHeight(0);
                    stage.setMaxWidth(Double.MAX_VALUE);
                    stage.setMaxHeight(Double.MAX_VALUE);
                    stage.setResizable(true);

                    GameCanvasApp gameCanvasApp = new GameCanvasApp();
                    gameCanvasApp.setStage(stage);
                    gameCanvasApp.setRoom(resDTO.getRoom());
                    gameCanvasApp.setRemainingTime((int) resDTO.getRemainingTime());
                    gameCanvasApp.setMyTankId(resDTO.getMyTankId());

                    Scene gameScene = gameCanvasApp.createGameScene();
                    stage.setScene(gameScene);
                    stage.setTitle("Tank 2D - Game (Đã kết nối lại)");

                    stage.sizeToScene();
                    stage.setResizable(false);
                    stage.centerOnScreen();
                    stage.show();
                    LOGGER.info("[Lobby] Đã Reconnect thành công vào GameCanvasApp!");

                } catch (Exception e) {
                    session.addPacketListener(packetListener);
                    LOGGER.log(Level.SEVERE, "[Lobby] Lỗi khi chuyển cảnh vào game sau Reconnect", e);
                    showError("Không thể vào lại màn hình game!");
                }
            });

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi xử lý GAME_RECONNECT_RES", e);
        }
    }

    private void showError(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }
}