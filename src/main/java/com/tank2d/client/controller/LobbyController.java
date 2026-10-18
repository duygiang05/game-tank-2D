package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.client.view.GameCanvasApp;
import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.dto.game.ReconnectPromptDTO;
import com.tank2d.common.dto.game.ReconnectResponseDTO;
import com.tank2d.common.config.ConfigLoader;
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
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import com.tank2d.client.util.ToastUtil;
import com.tank2d.common.dto.InviteBattleNotifyDTO;
import com.tank2d.common.dto.InviteBattleRequestDTO;
import com.tank2d.common.dto.InviteBattleResponseDTO;
import com.tank2d.common.dto.OnlinePlayerDTO;
import com.tank2d.common.exception.GameNetworkException;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.tank2d.client.util.SoundManager;
import com.tank2d.common.dto.UserProfileDTO;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bộ điều khiển giao diện sảnh chờ (Lobby Controller).
 * Quản lý danh sách phòng, tạo phòng, tham gia phòng, bộ lọc thời gian trận đấu,
 * cảnh báo phạt khi thoát trận và điều hướng kết nối lại (reconnect).
 */
public class LobbyController {

    private static final Logger LOGGER = Logger.getLogger(LobbyController.class.getName());

    @FXML
    private ListView<RoomDTO> roomListView;

    @FXML
    private StackPane rootStackPane;

    @FXML
    private ListView<OnlinePlayerDTO> onlinePlayersListView;

    @FXML
    private Label onlineCountBadge;

    @FXML
    private StackPane inviteOverlay;

    @FXML
    private Label inviteMessageLabel;

    private int pendingInviterUserId = -1;

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
    private HBox playerBadgeBox;

    @FXML
    private Button lobbySettingsButton;

    private StackPane activeLobbyOverlay = null;
    private UserProfileDTO latestUserProfile = null;
    private VBox profileStatsCardContent = null;

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

    /**
     * Khởi tạo giao diện sảnh chờ, đăng ký lắng nghe gói tin mạng,
     * thiết lập bộ lọc và render danh sách phòng.
     */
    @FXML
    private void initialize() {
        session.addPacketListener(packetListener);

        durationFilterComboBox.getItems().addAll("ALL", "45S", "60S", "90S", "180S");
        durationFilterComboBox.setValue("ALL");
        durationFilterComboBox.setOnAction(event -> applyDurationFilter());

        if (session.getCurrentUser() != null) {
            usernameLabel.setText(session.getCurrentUser().getUsername());
        } else {
            usernameLabel.setText("PLAYER");
        }

        statusLabel.setText("LOADING ROOMS...");
        statusLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 11.5px;");

        roomListView.setCellFactory(listView -> new ListCell<RoomDTO>() {
            @Override
            protected void updateItem(RoomDTO room, boolean empty) {
                super.updateItem(room, empty);

                if (empty || room == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label nameLabel = new Label("🎮  " + room.getRoomName());
                    nameLabel.setPrefWidth(290);
                    nameLabel.setAlignment(Pos.CENTER_LEFT);
                    nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #38bdf8; -fx-font-size: 12px; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif;");

                    Label playersLabel = new Label(room.getCurrentPlayers() + " / " + room.getMaxPlayers());
                    playersLabel.setPrefWidth(95);
                    playersLabel.setAlignment(Pos.CENTER);
                    playersLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #f8fafc; -fx-font-size: 12px; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif;");

                    Label durationLabel = new Label("⏱ " + room.getDuration() + "S");
                    durationLabel.setPrefWidth(90);
                    durationLabel.setAlignment(Pos.CENTER);
                    durationLabel.setStyle("-fx-text-fill: #fbbf24; -fx-font-weight: bold; -fx-font-size: 11.5px; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif;");

                    String status = room.getStatus() != null ? room.getStatus() : "WAITING";
                    boolean isWaiting = status.equalsIgnoreCase("WAITING") || status.toLowerCase().contains("chờ");
                    Label statusLabelCell = new Label(isWaiting ? "● WAITING" : "▶ IN BATTLE");
                    statusLabelCell.setPrefWidth(130);
                    statusLabelCell.setAlignment(Pos.CENTER_RIGHT);

                    if (isWaiting) {
                        statusLabelCell.setStyle("-fx-text-fill: #4ade80; -fx-font-weight: bold; -fx-font-size: 11px; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif;");
                    } else {
                        statusLabelCell.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold; -fx-font-size: 11px; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif;");
                    }

                    HBox row = new HBox(nameLabel, playersLabel, durationLabel, statusLabelCell);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-padding: 6px 10px; -fx-background-color: transparent;");

                    setGraphic(row);
                    setText(null);
                }
            }
        });

        if (onlinePlayersListView != null) {
            onlinePlayersListView.setCellFactory(listView -> new ListCell<OnlinePlayerDTO>() {
                @Override
                protected void updateItem(OnlinePlayerDTO player, boolean empty) {
                    super.updateItem(player, empty);
                    if (empty || player == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        boolean isLobby = "LOBBY".equalsIgnoreCase(player.getStatus());

                        Circle dot = new Circle(3.5);
                        dot.setFill(isLobby ? Color.web("#4ade80") : Color.web("#f59e0b"));

                        Label nameLbl = new Label(player.getUsername());
                        nameLbl.setPrefWidth(105);
                        nameLbl.setAlignment(Pos.CENTER_LEFT);
                        nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #38bdf8; -fx-font-size: 10.5px; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif;");

                        HBox nameBox = new HBox(5, dot, nameLbl);
                        nameBox.setAlignment(Pos.CENTER_LEFT);
                        HBox.setHgrow(nameBox, javafx.scene.layout.Priority.ALWAYS);

                        Button inviteBtn = new Button("INVITE");
                        inviteBtn.setPrefWidth(60);
                        inviteBtn.setPrefHeight(24);

                        if (!isLobby) {
                            inviteBtn.setDisable(true);
                            inviteBtn.setText("BUSY");
                            inviteBtn.setStyle("-fx-background-color: rgba(51, 65, 85, 0.7); -fx-text-fill: #94a3b8; -fx-background-radius: 6px; -fx-border-color: rgba(148, 163, 184, 0.3); -fx-border-radius: 6px; -fx-border-width: 1px; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-size: 9px; -fx-font-weight: bold;");
                        } else {
                            inviteBtn.setDisable(false);
                            inviteBtn.setText("INVITE");
                            inviteBtn.setStyle("-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_green_normal.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: white; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0 4px 2px 4px;");
                            inviteBtn.setOnMouseEntered(e -> {
                                if (!inviteBtn.isDisabled()) {
                                    inviteBtn.setStyle("-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_green_hover.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: white; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0 4px 2px 4px;");
                                }
                            });
                            inviteBtn.setOnMouseExited(e -> {
                                if (!inviteBtn.isDisabled()) {
                                    inviteBtn.setStyle("-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_green_normal.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: white; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0 4px 2px 4px;");
                                }
                            });
                            inviteBtn.setOnAction(e -> sendBattleInvite(player));
                        }

                        HBox row = new HBox(6, nameBox, inviteBtn);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setStyle("-fx-padding: 2px 4px; -fx-background-color: transparent;");

                        setGraphic(row);
                        setText(null);
                    }
                }
            });
        }

        loadRooms();
    }

    /**
     * Gửi yêu cầu lấy danh sách phòng hiện có từ máy chủ.
     */
    private void loadRooms() {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                showError("Chưa kết nối tới Server!");
                return;
            }

            Packet request = new Packet(PacketType.LOBBY_GET_ROOMS_REQ, "");
            clientSocket.sendPacket(request);

            Packet onlineReq = new Packet(PacketType.ONLINE_PLAYERS_REQ, "");
            clientSocket.sendPacket(onlineReq);

        } catch (GameNetworkException e) {
            showError(e.getMessage());
            LOGGER.log(Level.WARNING, "[Lobby] Lỗi mạng khi tải phòng: " + e.getErrorCode(), e);
        } catch (IOException e) {
            showError("Không thể kết nối tới Server!");
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi tải phòng", e);
        }
    }

    /**
     * Xử lý sự kiện tạo phòng mới.
     */
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

    /**
     * Xử lý sự kiện tham gia phòng được chọn.
     */
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
            statusLabel.setText("PLEASE SELECT A ROOM FROM THE LIST!");
            statusLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #fbbf24; -fx-font-size: 11.5px; -fx-font-weight: bold;");
            return;
        }

        int selectedRoomId = selectedRoom.getRoomId();
        statusLabel.setText("JOINING " + selectedRoom.getRoomName() + "...");
        statusLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 11.5px; -fx-font-weight: bold;");

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

    /**
     * Định tuyến gói tin phản hồi từ Server về các hàm xử lý tương ứng.
     *
     * @param packet gói tin nhận được từ máy chủ
     */
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

            case ONLINE_PLAYERS_RES:
                handleOnlinePlayersUpdate(packet.getData());
                break;

            case INVITE_BATTLE_NOTIFY:
                handleInviteBattleNotify(packet.getData());
                break;

            case INVITE_DECLINED_NOTIFY:
                handleInviteDeclinedNotify(packet.getData());
                break;

            case USER_PROFILE_RES:
                handleUserProfileResponse(packet.getData());
                break;

            default:
                break;
        }
    }

    /**
     * Cập nhật danh sách phòng hiển thị và kiểm tra trạng thái gỡ phạt/kết thúc trận.
     *
     * @param rawJson chuỗi JSON danh sách phòng từ server
     */
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

            try {
                if (session.getClientSocket() != null && session.getClientSocket().isConnected()) {
                    session.getClientSocket().sendPacket(new Packet(PacketType.ONLINE_PLAYERS_REQ, ""));
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi cập nhật danh sách phòng", e);
        }
    }

    /**
     * Áp dụng bộ lọc thời gian trận đấu lên danh sách hiển thị.
     */
    private void applyDurationFilter() {
        String selectedFilter = durationFilterComboBox.getValue();
        if (selectedFilter == null) {
            selectedFilter = "ALL";
        }

        roomListView.getItems().clear();

        if ("ALL".equalsIgnoreCase(selectedFilter) || "Tất cả".equals(selectedFilter)) {
            roomListView.getItems().addAll(allRooms);
        } else {
            int duration = Integer.parseInt(selectedFilter.toLowerCase().replace("s", "").trim());
            for (RoomDTO room : allRooms) {
                if (room.getDuration() == duration) {
                    roomListView.getItems().add(room);
                }
            }
        }

        int count = roomListView.getItems().size();
        if (count == 0) {
            statusLabel.setText("NO MATCHING ROOMS FOUND.");
            statusLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 11.5px;");
        } else {
            statusLabel.setText("DISPLAYING " + count + " WAITING ROOM(S).");
            statusLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 11.5px;");
        }
    }

    /**
     * Xử lý cập nhật trạng thái phòng khi tham gia hoặc mở phòng.
     *
     * @param rawJson dữ liệu phòng dạng JSON
     */
    private void handleRoomStateUpdate(String rawJson) {
        try {
            RoomDTO room = gson.fromJson(rawJson, RoomDTO.class);
            if (room == null) {
                showError("Không thể xử lý thông tin phòng!");
                return;
            }

            User currentUser = session.getCurrentUser();
            boolean isJoined = currentUser != null 
                    && room.getPlayerNames() != null 
                    && room.getPlayerNames().contains(currentUser.getUsername());

            if (isJoined) {
                LOGGER.info("[Lobby] Vào phòng thành công: " + room.getRoomName());
                openRoom(room);
            } else {
                LOGGER.warning("[Lobby] Không thể vào phòng (bị Server từ chối): " + room.getRoomName());
                showError("Không thể vào phòng! Phòng đang chiến đấu hoặc đã đầy.");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi xử lý ROOM_STATE_UPDATE", e);
            showError("Không thể xử lý thông tin phòng!");
        }
    }

    /**
     * Mở giao diện phòng chơi (Room view).
     *
     * @param room đối tượng RoomDTO đại diện thông tin phòng
     */
    private void openRoom(RoomDTO room) {
        Platform.runLater(() -> {
            try {
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
                stage.setScene(new Scene(root, ConfigLoader.getWindowWidth(), ConfigLoader.getWindowHeight()));
                stage.setTitle("Tank 2D Online - " + room.getRoomName());
                stage.show();

            } catch (IOException e) {
                session.addPacketListener(packetListener);
                LOGGER.log(Level.SEVERE, "[Lobby] Không thể mở Room", e);
                showError("Không thể mở giao diện phòng!");
            }
        });
    }

    /**
     * Xử lý đăng xuất tài khoản và quay trở lại màn hình đăng nhập.
     */
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
            stage.setScene(new Scene(root, ConfigLoader.getWindowWidth(), ConfigLoader.getWindowHeight()));
            stage.setTitle("Tank 2D Online - Login");
            stage.show();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Lobby] Lỗi Logout", e);
            showError("Không thể đăng xuất!");
        }
    }

    /**
     * Mở màn hình Bảng xếp hạng người chơi.
     */
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
            session.removePacketListener(packetListener);

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/tank2d/client/view/leaderboard.fxml")
            );
            Parent root = loader.load();

            Stage stage = (Stage) leaderboardButton.getScene().getWindow();
            stage.setScene(new Scene(root, ConfigLoader.getWindowWidth(), ConfigLoader.getWindowHeight()));
            stage.setTitle("Tank 2D Online - Leaderboard");
            stage.show();

        } catch (IOException e) {
            session.addPacketListener(packetListener);
            LOGGER.log(Level.SEVERE, "[Lobby] Không thể mở Leaderboard", e);
            showError("Không thể mở bảng xếp hạng!");
        }
    }

    /**
     * Mở màn hình Lịch sử thi đấu của người chơi.
     */
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
            stage.setScene(new Scene(root, ConfigLoader.getWindowWidth(), ConfigLoader.getWindowHeight()));
            stage.setTitle("Tank 2D Online - Lịch sử thi đấu");
            stage.show();

        } catch (IOException e) {
            session.addPacketListener(packetListener);
            LOGGER.log(Level.SEVERE, "[Lobby] Không thể mở Lịch sử đấu", e);
            showError("Không thể mở lịch sử đấu!");
        }
    }

    /**
     * Xử lý thông báo phạt khi người chơi thoát trận giữa chừng hoặc khi hết phạt.
     *
     * @param message nội dung thông báo trạng thái phạt
     */
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

    /**
     * Xử lý gói tin nhắc nhở trận đấu dở dang khi đăng nhập lại.
     *
     * @param rawJson dữ liệu ReconnectPromptDTO dạng JSON
     */
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

    /**
     * Hiển thị hộp thoại Modal hỏi người chơi có muốn kết nối lại trận đấu không.
     *
     * @param promptDTO thông tin phòng và thời gian còn lại
     */
    private void showReconnectModal(ReconnectPromptDTO promptDTO) {
        if (promptDTO == null) return;
        if (reconnectRoomLabel != null) {
            reconnectRoomLabel.setText("ROOM ARENA: " + promptDTO.getRoomName().toUpperCase());
        }
        if (reconnectTimeLabel != null) {
            int remainSec = (int) promptDTO.getRemainingTime();
            reconnectTimeLabel.setText(String.format("TIME REMAINING: %02d:%02d", remainSec / 60, remainSec % 60));
        }
        if (reconnectOverlay != null) {
            reconnectOverlay.setVisible(true);
        }
        if (mainContentPane != null) {
            mainContentPane.setEffect(new GaussianBlur(12));
        }
        statusLabel.setText("Active battle detected in " + promptDTO.getRoomName() + ".");
        statusLabel.setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;");
    }

    /**
     * Tạm bỏ qua hộp thoại kết nối lại và cho phép xem sảnh chờ.
     */
    @FXML
    private void handleSkipReconnect() {
        if (reconnectOverlay != null) {
            reconnectOverlay.setVisible(false);
        }
        if (mainContentPane != null && !isPenalized) {
            mainContentPane.setEffect(null);
        }
        statusLabel.setText("Battle in progress. Reconnect whenever you are ready.");
        statusLabel.setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;");
    }

    /**
     * Xác nhận quay lại trận đấu đang diễn ra.
     */
    @FXML
    private void handleConfirmReconnect() {
        try {
            statusLabel.setText("Reconnecting to battle arena...");
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

    /**
     * Xử lý dữ liệu phản hồi khôi phục trạng thái game và chuyển sang màn hình trận đấu.
     *
     * @param rawJson dữ liệu ReconnectResponseDTO dạng JSON
     */
    private void handleReconnectResponse(String rawJson) {
        try {
            ReconnectResponseDTO resDTO = gson.fromJson(rawJson, ReconnectResponseDTO.class);
            if (resDTO == null || resDTO.getRoom() == null) {
                showError("Unable to restore match session data!");
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
                    stage.setTitle("Tank 2D - Game (Reconnected)");

                    stage.setResizable(false);
                    stage.sizeToScene();
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

    /**
     * Hiển thị thông báo lỗi lên nhãn trạng thái giao diện.
     *
     * @param message nội dung thông báo lỗi
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #f87171; -fx-font-weight: bold; -fx-font-size: 11.5px;");
        });
    }

    private void sendBattleInvite(OnlinePlayerDTO player) {
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
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                showError("Chưa kết nối tới Server!");
                return;
            }
            Packet request = new Packet(
                    PacketType.INVITE_BATTLE_REQ,
                    gson.toJson(new InviteBattleRequestDTO(player.getUserId()))
            );
            clientSocket.sendPacket(request);
            ToastUtil.showInfo(rootStackPane, "INVITATION SENT TO " + player.getUsername().toUpperCase());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[Lobby] Failed to send invite", e);
            ToastUtil.showError(rootStackPane, "FAILED TO SEND INVITATION");
        }
    }

    private void handleOnlinePlayersUpdate(String rawJson) {
        try {
            Type listType = new TypeToken<List<OnlinePlayerDTO>>() {}.getType();
            List<OnlinePlayerDTO> players = gson.fromJson(rawJson, listType);
            if (players == null) return;

            int myUserId = session.getCurrentUser() != null ? session.getCurrentUser().getId() : -1;
            List<OnlinePlayerDTO> otherPlayers = new ArrayList<>();
            for (OnlinePlayerDTO p : players) {
                if (p.getUserId() != myUserId) {
                    otherPlayers.add(p);
                }
            }

            Platform.runLater(() -> {
                if (onlinePlayersListView != null) {
                    onlinePlayersListView.getItems().setAll(otherPlayers);
                }
                if (onlineCountBadge != null) {
                    onlineCountBadge.setText(otherPlayers.size() + " ON");
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[Lobby] Error updating online players", e);
        }
    }

    private void handleInviteBattleNotify(String rawJson) {
        try {
            InviteBattleNotifyDTO notify = gson.fromJson(rawJson, InviteBattleNotifyDTO.class);
            if (notify == null) return;

            Platform.runLater(() -> {
                pendingInviterUserId = notify.getInviterUserId();
                if (inviteMessageLabel != null) {
                    inviteMessageLabel.setText("OPERATOR " + notify.getInviterUsername().toUpperCase() + " CHALLENGES YOU TO A DUEL!");
                }
                if (inviteOverlay != null) {
                    inviteOverlay.setVisible(true);
                }
                if (mainContentPane != null) {
                    mainContentPane.setEffect(new GaussianBlur(12));
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[Lobby] Error handling invite notification", e);
        }
    }

    private void handleInviteDeclinedNotify(String rawJson) {
        try {
            String msg = gson.fromJson(rawJson, String.class);
            Platform.runLater(() -> {
                ToastUtil.showWarning(rootStackPane, msg != null ? msg : "INVITATION WAS DECLINED");
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[Lobby] Error handling invite decline notify", e);
        }
    }

    @FXML
    private void handleDeclineInvite() {
        if (inviteOverlay != null) {
            inviteOverlay.setVisible(false);
        }
        if (mainContentPane != null && !isPenalized && (reconnectOverlay == null || !reconnectOverlay.isVisible())) {
            mainContentPane.setEffect(null);
        }

        if (pendingInviterUserId != -1) {
            try {
                ClientSocket clientSocket = session.getClientSocket();
                if (clientSocket != null && clientSocket.isConnected()) {
                    Packet packet = new Packet(PacketType.INVITE_BATTLE_RESPONSE,
                            gson.toJson(new InviteBattleResponseDTO(pendingInviterUserId, false)));
                    clientSocket.sendPacket(packet);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[Lobby] Error declining invite", e);
            }
            pendingInviterUserId = -1;
        }
    }

    @FXML
    private void handleAcceptInvite() {
        if (inviteOverlay != null) {
            inviteOverlay.setVisible(false);
        }
        if (mainContentPane != null && !isPenalized && (reconnectOverlay == null || !reconnectOverlay.isVisible())) {
            mainContentPane.setEffect(null);
        }

        if (pendingInviterUserId != -1) {
            try {
                ClientSocket clientSocket = session.getClientSocket();
                if (clientSocket != null && clientSocket.isConnected()) {
                    Packet packet = new Packet(PacketType.INVITE_BATTLE_RESPONSE,
                            gson.toJson(new InviteBattleResponseDTO(pendingInviterUserId, true)));
                    clientSocket.sendPacket(packet);
                    ToastUtil.showSuccess(rootStackPane, "ACCEPTING DUEL... PREPARING BATTLEFIELD!");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[Lobby] Error accepting invite", e);
            }
            pendingInviterUserId = -1;
        }
    }

    private void handleUserProfileResponse(String data) {
        try {
            UserProfileDTO profile = gson.fromJson(data, UserProfileDTO.class);
            if (profile != null) {
                this.latestUserProfile = profile;
                Platform.runLater(() -> {
                    if (profileStatsCardContent != null) {
                        populateProfileStatsGrid(profileStatsCardContent, profile);
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[Lobby] Lỗi giải mã USER_PROFILE_RES", e);
        }
    }

    @FXML
    private void handleViewProfile() {
        closeActiveLobbyModal();
        if (rootStackPane == null) return;

        try {
            int uId = (session.getCurrentUser() != null) ? session.getCurrentUser().getId() : -1;
            ClientSocket socket = session.getClientSocket();
            if (socket != null && socket.isConnected()) {
                socket.sendPacket(new Packet(PacketType.USER_PROFILE_REQ, String.valueOf(uId)));
            }
        } catch (Exception ignored) {}

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(6, 10, 20, 0.78);");
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) closeActiveLobbyModal();
        });

        VBox card = new VBox(14);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(22, 28, 22, 28));
        card.setMaxWidth(530);
        card.setStyle("-fx-background-color: rgba(13, 20, 36, 0.98); -fx-background-radius: 18px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.85), 32, 0.45, 0, 8); -fx-border-color: rgba(56, 189, 248, 0.8); -fx-border-width: 2px; -fx-border-radius: 18px;");

        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);

        Label avatarLbl = new Label("🎖");
        avatarLbl.setStyle("-fx-font-size: 28px;");

        VBox userTitleBox = new VBox(3);
        String name = (session.getCurrentUser() != null) ? session.getCurrentUser().getUsername() : "PLAYER";
        Label nameLbl = new Label(name.toUpperCase());
        nameLbl.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 17px; -fx-font-weight: bold;");

        Label badgeLbl = new Label("PILOT COMMANDER • COMBAT CAREER PROFILE");
        badgeLbl.setStyle("-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 10.5px; -fx-font-weight: bold;");
        userTitleBox.getChildren().addAll(nameLbl, badgeLbl);
        HBox.setHgrow(userTitleBox, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;"));
        closeBtn.setOnAction(e -> closeActiveLobbyModal());

        header.getChildren().addAll(avatarLbl, userTitleBox, closeBtn);

        profileStatsCardContent = new VBox(10);
        populateProfileStatsGrid(profileStatsCardContent, latestUserProfile);

        Button okBtn = new Button("✕ CLOSE PROFILE");
        okBtn.setMaxWidth(Double.MAX_VALUE);
        okBtn.setPrefHeight(42);
        okBtn.setStyle("-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_blue_normal.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: white; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 12px; -fx-cursor: hand;");
        okBtn.setOnAction(e -> closeActiveLobbyModal());

        card.getChildren().addAll(header, profileStatsCardContent, okBtn);
        overlay.getChildren().add(card);

        this.activeLobbyOverlay = overlay;
        rootStackPane.getChildren().add(overlay);
    }

    private void populateProfileStatsGrid(VBox container, UserProfileDTO profile) {
        container.getChildren().clear();

        Label careerTitle = new Label("🏆 CAREER COMBAT PERFORMANCE");
        careerTitle.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #fbbf24; -fx-font-size: 11px; -fx-font-weight: bold;");
        container.getChildren().add(careerTitle);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        String rankTier = (profile != null && profile.getRankTier() != null) ? profile.getRankTier().toUpperCase() : "BRONZE";
        int rankPos = (profile != null) ? profile.getRankPosition() : 1;
        int points = (profile != null) ? profile.getTotalPoints() : 0;
        int matches = (profile != null) ? profile.getTotalMatches() : 0;
        int wins = (profile != null) ? profile.getTotalWins() : 0;
        int kills = (profile != null) ? profile.getTotalKills() : 0;
        int hits = (profile != null) ? profile.getTotalHits() : 0;
        double winRate = (profile != null) ? profile.getWinRate() : 0.0;
        int losses = Math.max(0, matches - wins);

        grid.add(createStatItem("RANK & LEADERBOARD", rankTier + " (TOP #" + rankPos + ")", "#fbbf24"), 0, 0);
        grid.add(createStatItem("COMBAT MMR POINTS", points + " PTS", "#38bdf8"), 1, 0);
        grid.add(createStatItem("TOTAL MATCHES", matches + " BATTLES", "#f1f5f9"), 0, 1);
        grid.add(createStatItem("VICTORY RATE", String.format("%.1f%%", winRate), "#4ade80"), 1, 1);
        grid.add(createStatItem("VICTORIES / DEFEATS", wins + " W - " + losses + " L", "#c084fc"), 0, 2);
        grid.add(createStatItem("TOTAL KILLS / HITS", kills + " K - " + hits + " HITS", "#f87171"), 1, 2);

        container.getChildren().add(grid);
    }

    private VBox createStatItem(String caption, String value, String colorHex) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8, 14, 8, 14));
        box.setPrefWidth(228);
        box.setStyle("-fx-background-color: rgba(30, 41, 59, 0.75); -fx-background-radius: 10px; -fx-border-color: rgba(148, 163, 184, 0.25); -fx-border-radius: 10px; -fx-border-width: 1.2px;");

        Label capLbl = new Label(caption);
        capLbl.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 8.5px; -fx-font-weight: bold;");

        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: " + colorHex + "; -fx-font-size: 14px; -fx-font-weight: bold;");

        box.getChildren().addAll(capLbl, valLbl);
        return box;
    }

    @FXML
    private void handleOpenSettings() {
        closeActiveLobbyModal();
        if (rootStackPane == null) return;

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(6, 10, 20, 0.78);");
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) closeActiveLobbyModal();
        });

        VBox card = new VBox(14);
        card.setPadding(new Insets(20, 26, 20, 26));
        card.setMaxWidth(510);
        card.setStyle("-fx-background-color: rgba(15, 23, 42, 0.98); -fx-background-radius: 18px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.85), 32, 0.45, 0, 8); -fx-border-color: rgba(245, 158, 11, 0.8); -fx-border-width: 2px; -fx-border-radius: 18px;");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Image setIconImg = com.tank2d.client.util.AssetLoader.getSettingsIcon();
        javafx.scene.image.ImageView iconView = new javafx.scene.image.ImageView();
        iconView.setFitWidth(28);
        iconView.setFitHeight(28);
        iconView.setPreserveRatio(true);
        if (setIconImg != null) {
            iconView.setImage(setIconImg);
        }

        Label titleLbl = new Label("GAMEPLAY SETTINGS");
        titleLbl.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #fbbf24; -fx-font-size: 15px; -fx-font-weight: bold;");
        HBox.setHgrow(titleLbl, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 17px; -fx-font-weight: bold; -fx-cursor: hand;"));
        closeBtn.setOnAction(e -> closeActiveLobbyModal());
        header.getChildren().addAll(iconView, titleLbl, closeBtn);

        // Audio controls
        VBox audioBox = new VBox(10);
        audioBox.setPadding(new Insets(12, 14, 12, 14));
        audioBox.setStyle("-fx-background-color: rgba(30, 41, 59, 0.65); -fx-background-radius: 10px; -fx-border-color: rgba(56, 189, 248, 0.3); -fx-border-radius: 10px; -fx-border-width: 1px;");

        Label audioTitle = new Label("🔊 AUDIO & VOLUME PREFERENCES");
        audioTitle.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 10.5px; -fx-font-weight: bold;");

        HBox sfxRow = new HBox(10);
        sfxRow.setAlignment(Pos.CENTER_LEFT);
        Label sfxLbl = new Label("SFX:");
        sfxLbl.setPrefWidth(50);
        sfxLbl.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 10px;");
        Slider sfxSlider = new Slider(0, 100, SoundManager.getSFXVolume() * 100);
        HBox.setHgrow(sfxSlider, Priority.ALWAYS);
        Label sfxVal = new Label((int) (SoundManager.getSFXVolume() * 100) + "%");
        sfxVal.setPrefWidth(45);
        sfxVal.setStyle("-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #4ade80; -fx-font-size: 11px; -fx-font-weight: bold;");
        sfxSlider.valueProperty().addListener((obs, oldV, newV) -> {
            SoundManager.setSFXVolume(newV.floatValue() / 100f);
            sfxVal.setText(newV.intValue() + "%");
        });
        sfxSlider.setOnMouseReleased(e -> SoundManager.playSound("tank_hit"));
        sfxRow.getChildren().addAll(sfxLbl, sfxSlider, sfxVal);

        HBox bgmRow = new HBox(10);
        bgmRow.setAlignment(Pos.CENTER_LEFT);
        Label bgmLbl = new Label("BGM:");
        bgmLbl.setPrefWidth(50);
        bgmLbl.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 10px;");
        Slider bgmSlider = new Slider(0, 100, SoundManager.getBGMVolume() * 100);
        HBox.setHgrow(bgmSlider, Priority.ALWAYS);
        Label bgmVal = new Label((int) (SoundManager.getBGMVolume() * 100) + "%");
        bgmVal.setPrefWidth(45);
        bgmVal.setStyle("-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #4ade80; -fx-font-size: 11px; -fx-font-weight: bold;");
        bgmSlider.valueProperty().addListener((obs, oldV, newV) -> {
            SoundManager.setBGMVolume(newV.floatValue() / 100f);
            bgmVal.setText(newV.intValue() + "%");
        });
        bgmRow.getChildren().addAll(bgmLbl, bgmSlider, bgmVal);

        CheckBox muteCb = new CheckBox("MUTE ALL SOUNDS (MASTER MUTE)");
        muteCb.setSelected(SoundManager.isMuted());
        muteCb.setStyle("-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #f87171; -fx-font-size: 10.5px; -fx-cursor: hand;");
        muteCb.setOnAction(e -> SoundManager.setMuted(muteCb.isSelected()));

        audioBox.getChildren().addAll(audioTitle, sfxRow, bgmRow, muteCb);

        // Guide Box
        VBox guideBox = new VBox(4);
        guideBox.setPadding(new Insets(10, 14, 10, 14));
        guideBox.setStyle("-fx-background-color: rgba(30, 41, 59, 0.5); -fx-background-radius: 10px;");
        Label guideTitle = new Label("⌨ COMBAT CONTROLS REFERENCE");
        guideTitle.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 9.5px; -fx-font-weight: bold;");
        Label key1 = new Label("• MOVE: W, A, S, D OR ARROW KEYS");
        key1.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 11px;");
        Label key2 = new Label("• FIRE CANNON: SPACEBAR OR LEFT MOUSE CLICK");
        key2.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 11px;");
        guideBox.getChildren().addAll(guideTitle, key1, key2);

        Button saveBtn = new Button("✔ SAVE & CLOSE");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setPrefHeight(40);
        saveBtn.setStyle("-fx-background-image: url('/com/tank2d/client/assets/ui/auth/buttons/btn_gold_normal.png'); -fx-background-size: 100% 100%; -fx-background-color: transparent; -fx-border-width: 0; -fx-text-fill: #0f172a; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 12px; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> closeActiveLobbyModal());

        card.getChildren().addAll(header, audioBox, guideBox, saveBtn);
        overlay.getChildren().add(card);

        this.activeLobbyOverlay = overlay;
        rootStackPane.getChildren().add(overlay);
    }

    private void closeActiveLobbyModal() {
        if (activeLobbyOverlay != null && rootStackPane != null) {
            rootStackPane.getChildren().remove(activeLobbyOverlay);
            activeLobbyOverlay = null;
        }
    }
}