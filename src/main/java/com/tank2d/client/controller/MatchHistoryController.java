package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.dto.MatchDetailDTO;
import com.tank2d.common.dto.MatchParticipantDTO;
import com.tank2d.common.dto.UserMatchHistoryDTO;
import com.tank2d.common.exception.GameNetworkException;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.effect.GaussianBlur;
import com.tank2d.client.util.AssetLoader;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bộ điều khiển giao diện Lịch sử thi đấu (Match History Controller).
 * Hiển thị danh sách các trận đấu gần nhất của người chơi và popup chi tiết
 * thông số tất cả người tham gia trong trận đấu đó.
 */
public class MatchHistoryController {

    private static final Logger LOGGER = Logger.getLogger(MatchHistoryController.class.getName());

    @FXML
    private StackPane rootStackPane;

    @FXML
    private BorderPane mainContentPane;

    @FXML
    private ListView<UserMatchHistoryDTO> matchHistoryListView;

    @FXML
    private Label statusLabel;

    @FXML
    private Button backButton;

    @FXML
    private StackPane detailOverlay;

    @FXML
    private VBox detailCard;

    @FXML
    private Label detailRoomTimeLabel;

    @FXML
    private HBox winnerBanner;

    @FXML
    private Label winnerTextLabel;

    @FXML
    private ListView<MatchParticipantDTO> participantListView;

    private final ClientSession session = ClientSession.getInstance();
    private final Gson gson = new Gson();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    /**
     * Khởi tạo giao diện, cấu hình CellFactory hiển thị danh sách lịch sử trận đấu,
     * sự kiện click xem chi tiết và cấu hình bảng chi tiết thành viên.
     */
    @FXML
    private void initialize() {
        session.addPacketListener(packetListener);

        matchHistoryListView.setCellFactory(listView -> new ListCell<UserMatchHistoryDTO>() {
            @Override
            protected void updateItem(UserMatchHistoryDTO match, boolean empty) {
                super.updateItem(match, empty);

                if (empty || match == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox resultBox = new HBox(6.0);
                    resultBox.setAlignment(Pos.CENTER_LEFT);
                    resultBox.setPrefWidth(125.0);
                    resultBox.setMaxWidth(125.0);

                    Label badgeLabel = new Label();
                    badgeLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 10px; -fx-background-radius: 6px; -fx-padding: 3px 6px; -fx-border-radius: 6px; -fx-border-width: 1px;");

                    Label rankLabel = new Label("#" + match.getRankPosition());
                    rankLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px;");

                    HBox rankBox = new HBox(4.0);
                    rankBox.setAlignment(Pos.CENTER_LEFT);
                    Image medalImg = AssetLoader.getMedalImage(match.getRankPosition());
                    if (medalImg != null) {
                        ImageView medalView = new ImageView(medalImg);
                        medalView.setFitWidth(18);
                        medalView.setFitHeight(18);
                        medalView.setPreserveRatio(true);
                        rankBox.getChildren().add(medalView);
                    }
                    rankBox.getChildren().add(rankLabel);

                    if ("VICTORY".equalsIgnoreCase(match.getResult())) {
                        badgeLabel.setText("WIN");
                        badgeLabel.setStyle(badgeLabel.getStyle() + " -fx-background-color: rgba(16, 185, 129, 0.25); -fx-text-fill: #34d399; -fx-border-color: rgba(16, 185, 129, 0.5);");
                        rankLabel.setStyle(rankLabel.getStyle() + " -fx-text-fill: #34d399;");
                    } else if ("DRAW".equalsIgnoreCase(match.getResult())) {
                        badgeLabel.setText("DRAW");
                        badgeLabel.setStyle(badgeLabel.getStyle() + " -fx-background-color: rgba(245, 158, 11, 0.25); -fx-text-fill: #fbbf24; -fx-border-color: rgba(245, 158, 11, 0.5);");
                        rankLabel.setStyle(rankLabel.getStyle() + " -fx-text-fill: #fbbf24;");
                    } else {
                        badgeLabel.setText("DEFEAT");
                        badgeLabel.setStyle(badgeLabel.getStyle() + " -fx-background-color: rgba(239, 68, 68, 0.25); -fx-text-fill: #f87171; -fx-border-color: rgba(239, 68, 68, 0.5);");
                        rankLabel.setStyle(rankLabel.getStyle() + " -fx-text-fill: #f87171;");
                    }
                    resultBox.getChildren().addAll(badgeLabel, rankBox);

                    VBox roomBox = new VBox(2.0);
                    roomBox.setAlignment(Pos.CENTER_LEFT);
                    roomBox.setPrefWidth(260.0);
                    roomBox.setMaxWidth(260.0);

                    Label roomNameLabel = new Label(match.getRoomName());
                    roomNameLabel.setPrefWidth(260.0);
                    roomNameLabel.setMaxWidth(260.0);
                    roomNameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                    roomNameLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11.5px; -fx-text-fill: #38bdf8;");

                    Label durationLabel = new Label("TIME: " + match.getDurationSeconds() + "S");
                    durationLabel.setStyle("-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 10px;");
                    roomBox.getChildren().addAll(roomNameLabel, durationLabel);

                    Label timeLabel = new Label(match.getPlayedAt());
                    timeLabel.setPrefWidth(140.0);
                    timeLabel.setMaxWidth(140.0);
                    timeLabel.setAlignment(Pos.CENTER_LEFT);
                    timeLabel.setStyle("-fx-font-family: 'Kenney Future Narrow', 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-size: 11px;");

                    Label combatLabel = new Label("🎯 " + match.getKills() + "   💥 " + match.getHits());
                    combatLabel.setPrefWidth(100.0);
                    combatLabel.setMaxWidth(100.0);
                    combatLabel.setAlignment(Pos.CENTER_RIGHT);
                    combatLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #f1f5f9;");

                    int points = match.getPointsEarned();
                    String pointsStr = (points >= 0 ? "+" : "") + points + " XP";
                    Label pointsLabel = new Label(pointsStr);
                    pointsLabel.setPrefWidth(100.0);
                    pointsLabel.setMaxWidth(100.0);
                    pointsLabel.setAlignment(Pos.CENTER_RIGHT);
                    if (points >= 0) {
                        pointsLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #4ade80;");
                    } else {
                        pointsLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #f87171;");
                    }

                    HBox row = new HBox(resultBox, roomBox, timeLabel, combatLabel, pointsLabel);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-padding: 8px 16px; -fx-background-color: transparent; -fx-cursor: hand; -fx-border-color: rgba(56, 189, 248, 0.1); -fx-border-width: 0 0 1px 0;");
                    row.setOnMouseEntered(e -> row.setStyle("-fx-padding: 8px 16px; -fx-background-color: rgba(56, 189, 248, 0.12); -fx-cursor: hand; -fx-border-color: rgba(56, 189, 248, 0.3); -fx-border-width: 0 0 1px 0;"));
                    row.setOnMouseExited(e -> row.setStyle("-fx-padding: 8px 16px; -fx-background-color: transparent; -fx-cursor: hand; -fx-border-color: rgba(56, 189, 248, 0.1); -fx-border-width: 0 0 1px 0;"));

                    setGraphic(row);
                    setText(null);
                }
            }
        });

        matchHistoryListView.setOnMouseClicked(event -> {
            UserMatchHistoryDTO selectedMatch = matchHistoryListView.getSelectionModel().getSelectedItem();
            if (selectedMatch != null) {
                requestMatchDetail(selectedMatch.getMatchId());
            }
        });

        participantListView.setCellFactory(listView -> new ListCell<MatchParticipantDTO>() {
            @Override
            protected void updateItem(MatchParticipantDTO p, boolean empty) {
                super.updateItem(p, empty);

                if (empty || p == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    int myUserId = (session.getCurrentUser() != null) ? session.getCurrentUser().getId() : -1;
                    boolean isMe = (p.getUserId() == myUserId);

                    HBox pRankBox = new HBox(4.0);
                    pRankBox.setPrefWidth(80.0);
                    pRankBox.setMaxWidth(80.0);
                    pRankBox.setAlignment(Pos.CENTER_LEFT);

                    int rank = p.getRankPosition();
                    Image pMedal = AssetLoader.getMedalImage(rank);
                    if (pMedal != null) {
                        ImageView pMedalView = new ImageView(pMedal);
                        pMedalView.setFitWidth(18);
                        pMedalView.setFitHeight(18);
                        pMedalView.setPreserveRatio(true);
                        pRankBox.getChildren().add(pMedalView);
                    }

                    Label rankLabel = new Label(pMedal != null ? "#" + rank : (rank <= 3 ? "#" + rank : "    #" + rank));
                    if (rank == 1) {
                        rankLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #fbbf24; -fx-font-weight: bold; -fx-font-size: 11.5px;");
                    } else if (rank == 2) {
                        rankLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #cbd5e1; -fx-font-weight: bold; -fx-font-size: 11.5px;");
                    } else if (rank == 3) {
                        rankLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #f97316; -fx-font-weight: bold; -fx-font-size: 11.5px;");
                    } else {
                        rankLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-font-size: 11px;");
                    }
                    pRankBox.getChildren().add(rankLabel);

                    Label nameLabel = new Label(p.getUsername() + (isMe ? " (YOU)" : ""));
                    nameLabel.setPrefWidth(250.0);
                    nameLabel.setMaxWidth(250.0);
                    nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                    nameLabel.setAlignment(Pos.CENTER_LEFT);
                    if (isMe) {
                        nameLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11.5px; -fx-text-fill: #38bdf8;");
                    } else {
                        nameLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11.5px; -fx-text-fill: #f1f5f9;");
                    }

                    Label killsLabel = new Label("🎯 " + p.getKills());
                    killsLabel.setPrefWidth(100.0);
                    killsLabel.setMaxWidth(100.0);
                    killsLabel.setAlignment(Pos.CENTER_RIGHT);
                    killsLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #f1f5f9;");

                    Label hitsLabel = new Label("💥 " + p.getHits());
                    hitsLabel.setPrefWidth(110.0);
                    hitsLabel.setMaxWidth(110.0);
                    hitsLabel.setAlignment(Pos.CENTER_RIGHT);
                    hitsLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #f1f5f9;");

                    int pts = p.getPointsEarned();
                    Label ptsLabel = new Label((pts >= 0 ? "+" : "") + pts + " XP");
                    ptsLabel.setPrefWidth(110.0);
                    ptsLabel.setMaxWidth(110.0);
                    ptsLabel.setAlignment(Pos.CENTER_RIGHT);
                    ptsLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + (pts >= 0 ? "#4ade80;" : "#f87171;"));

                    HBox row = new HBox(pRankBox, nameLabel, killsLabel, hitsLabel, ptsLabel);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-padding: 8px 16px; -fx-background-color: " + (isMe ? "rgba(56, 189, 248, 0.15);" : "transparent;"));

                    setGraphic(row);
                    setText(null);
                }
            }
        });

        loadMatchHistory();
    }

    /**
     * Gửi yêu cầu lấy danh sách lịch sử thi đấu của người chơi.
     */
    private void loadMatchHistory() {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                showError("NOT CONNECTED TO SERVER!");
                return;
            }

            Packet request = new Packet(PacketType.MATCH_HISTORY_REQ, "");
            clientSocket.sendPacket(request);

        } catch (GameNetworkException e) {
            showError(e.getMessage());
            LOGGER.log(Level.WARNING, "[MatchHistory] Lỗi mạng khi tải lịch sử: " + e.getErrorCode(), e);
        } catch (IOException e) {
            showError("CANNOT CONNECT TO SERVER!");
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi gửi yêu cầu lịch sử đấu", e);
        }
    }

    /**
     * Gửi yêu cầu lấy chi tiết trận đấu theo mã định danh.
     *
     * @param matchId mã ID trận đấu
     */
    private void requestMatchDetail(int matchId) {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) return;

            Packet request = new Packet(PacketType.MATCH_DETAIL_REQ, gson.toJson(matchId));
            clientSocket.sendPacket(request);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi yêu cầu chi tiết trận đấu", e);
        }
    }

    /**
     * Định tuyến gói tin từ Server về hàm xử lý danh sách hoặc chi tiết trận đấu.
     *
     * @param packet gói tin nhận được từ máy chủ
     */
    private void handleServerPacket(Packet packet) {
        if (packet == null || packet.getType() == null) return;

        if (packet.getType() == PacketType.MATCH_HISTORY_RES) {
            handleMatchHistoryResponse(packet.getData());
        } else if (packet.getType() == PacketType.MATCH_DETAIL_RES) {
            handleMatchDetailResponse(packet.getData());
        }
    }

    /**
     * Hiển thị danh sách lịch sử đấu nhận được lên ListView.
     *
     * @param rawJson chuỗi JSON danh sách UserMatchHistoryDTO
     */
    private void handleMatchHistoryResponse(String rawJson) {
        try {
            Type listType = new TypeToken<List<UserMatchHistoryDTO>>() {}.getType();
            List<UserMatchHistoryDTO> list = gson.fromJson(rawJson, listType);

            Platform.runLater(() -> {
                if (list == null || list.isEmpty()) {
                    matchHistoryListView.getItems().clear();
                    statusLabel.setText("NO RECENT BATTLE RECORDS FOUND.");
                    statusLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #94a3b8; -fx-font-size: 11px;");
                } else {
                    matchHistoryListView.getItems().setAll(list);
                    statusLabel.setText("DISPLAYING LATEST " + list.size() + " MATCHES.");
                    statusLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #38bdf8; -fx-font-size: 11px;");
                }
            });

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi phân tích dữ liệu lịch sử", e);
            Platform.runLater(() -> showError("FAILED TO LOAD MATCH HISTORY FROM SERVER!"));
        }
    }

    /**
     * Hiển thị popup modal chi tiết trận đấu cùng danh sách tất cả người tham gia.
     *
     * @param rawJson dữ liệu MatchDetailDTO dạng JSON
     */
    private void handleMatchDetailResponse(String rawJson) {
        try {
            MatchDetailDTO detail = gson.fromJson(rawJson, MatchDetailDTO.class);
            if (detail == null) return;

            Platform.runLater(() -> {
                detailRoomTimeLabel.setText("ROOM: " + detail.getRoomName() + " • " + detail.getDurationSeconds() + "S • " + detail.getPlayedAt());

                if (detail.getWinnerUsername() != null && !detail.getWinnerUsername().isEmpty()) {
                    winnerTextLabel.setText("VICTOR: " + detail.getWinnerUsername());
                    winnerBanner.setStyle("-fx-background-color: rgba(234, 179, 8, 0.15); -fx-padding: 8px 16px; -fx-background-radius: 10px; -fx-border-color: rgba(234, 179, 8, 0.4); -fx-border-radius: 10px; -fx-border-width: 1px;");
                } else {
                    winnerTextLabel.setText("RESULT: TIME LIMIT REACHED (DRAW)");
                    winnerBanner.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-padding: 8px 16px; -fx-background-radius: 10px; -fx-border-color: rgba(245, 158, 11, 0.4); -fx-border-radius: 10px; -fx-border-width: 1px;");
                }

                participantListView.getItems().setAll(detail.getParticipants());

                mainContentPane.setEffect(new GaussianBlur(12));
                detailOverlay.setVisible(true);
            });

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi phân tích chi tiết trận đấu", e);
        }
    }

    /**
     * Đóng modal chi tiết trận đấu và gỡ bỏ hiệu ứng mờ nền.
     */
    @FXML
    private void handleCloseDetail() {
        detailOverlay.setVisible(false);
        mainContentPane.setEffect(null);
    }

    /**
     * Xử lý đóng modal khi người dùng click vào vùng backdrop mờ bên ngoài.
     *
     * @param event sự kiện chuột
     */
    @FXML
    private void handleOverlayClicked(MouseEvent event) {
        if (event.getTarget() == detailOverlay) {
            handleCloseDetail();
        }
    }

    /**
     * Quay trở về màn hình sảnh chờ (Lobby).
     */
    @FXML
    private void handleBack() {
        try {
            session.removePacketListener(packetListener);

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/tank2d/client/view/lobby.fxml")
            );
            Parent root = loader.load();

            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root, com.tank2d.common.config.ConfigLoader.getWindowWidth(), com.tank2d.common.config.ConfigLoader.getWindowHeight()));
            stage.setTitle("Tank 2D Online - Lobby");
            stage.show();

        } catch (IOException e) {
            session.addPacketListener(packetListener);
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi quay lại Lobby", e);
            showError("FAILED TO RETURN TO LOBBY!");
        }
    }

    /**
     * Cập nhật thông báo lỗi lên nhãn trạng thái giao diện.
     *
     * @param message nội dung thông báo lỗi
     */
    private void showError(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-text-fill: #f87171; -fx-font-size: 11px;");
        }
    }
}
