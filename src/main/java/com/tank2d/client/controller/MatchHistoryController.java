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
import javafx.scene.effect.GaussianBlur;
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
                    resultBox.setPrefWidth(135.0);
                    resultBox.setMaxWidth(135.0);

                    Label badgeLabel = new Label();
                    badgeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-background-radius: 6px; -fx-padding: 3px 7px;");

                    Label rankLabel = new Label("#" + match.getRankPosition());
                    rankLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

                    if ("VICTORY".equalsIgnoreCase(match.getResult())) {
                        badgeLabel.setText("🏆 THẮNG");
                        badgeLabel.setStyle(badgeLabel.getStyle() + " -fx-background-color: #dcfce7; -fx-text-fill: #15803d;");
                        rankLabel.setStyle(rankLabel.getStyle() + " -fx-text-fill: #166534;");
                    } else if ("DRAW".equalsIgnoreCase(match.getResult())) {
                        badgeLabel.setText("🤝 HÒA");
                        badgeLabel.setStyle(badgeLabel.getStyle() + " -fx-background-color: #fef3c7; -fx-text-fill: #b45309;");
                        rankLabel.setStyle(rankLabel.getStyle() + " -fx-text-fill: #92400e;");
                    } else {
                        badgeLabel.setText("💀 THUA");
                        badgeLabel.setStyle(badgeLabel.getStyle() + " -fx-background-color: #fee2e2; -fx-text-fill: #b91c1c;");
                        rankLabel.setStyle(rankLabel.getStyle() + " -fx-text-fill: #991b1b;");
                    }
                    resultBox.getChildren().addAll(badgeLabel, rankLabel);

                    VBox roomBox = new VBox(2.0);
                    roomBox.setAlignment(Pos.CENTER_LEFT);
                    roomBox.setPrefWidth(175.0);
                    roomBox.setMaxWidth(175.0);

                    Label roomNameLabel = new Label(match.getRoomName());
                    roomNameLabel.setPrefWidth(175.0);
                    roomNameLabel.setMaxWidth(175.0);
                    roomNameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                    roomNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f172a;");

                    Label durationLabel = new Label("Thời lượng: " + match.getDurationSeconds() + "s");
                    durationLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11.5px;");
                    roomBox.getChildren().addAll(roomNameLabel, durationLabel);

                    Label timeLabel = new Label(match.getPlayedAt());
                    timeLabel.setPrefWidth(145.0);
                    timeLabel.setMaxWidth(145.0);
                    timeLabel.setAlignment(Pos.CENTER_LEFT);
                    timeLabel.setStyle("-fx-text-fill: #475569; -fx-font-size: 12.5px; -fx-font-weight: 500;");

                    Label combatLabel = new Label("🎯 " + match.getKills() + "   💥 " + match.getHits());
                    combatLabel.setPrefWidth(110.0);
                    combatLabel.setMaxWidth(110.0);
                    combatLabel.setAlignment(Pos.CENTER_RIGHT);
                    combatLabel.setStyle("-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-text-fill: #334155;");

                    int points = match.getPointsEarned();
                    String pointsStr = (points >= 0 ? "+" : "") + points + " XP";
                    Label pointsLabel = new Label(pointsStr);
                    pointsLabel.setPrefWidth(95.0);
                    pointsLabel.setMaxWidth(95.0);
                    pointsLabel.setAlignment(Pos.CENTER_RIGHT);
                    if (points >= 0) {
                        pointsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #16a34a;");
                    } else {
                        pointsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #dc2626;");
                    }

                    HBox row = new HBox(resultBox, roomBox, timeLabel, combatLabel, pointsLabel);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-padding: 8px 16px; -fx-background-color: transparent; -fx-cursor: hand;");

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

                    Label rankLabel = new Label();
                    rankLabel.setPrefWidth(70.0);
                    rankLabel.setMaxWidth(70.0);
                    rankLabel.setAlignment(Pos.CENTER_LEFT);
                    int rank = p.getRankPosition();
                    if (rank == 1) {
                        rankLabel.setText("🥇 #1");
                        rankLabel.setStyle("-fx-text-fill: #b45309; -fx-font-weight: bold; -fx-font-size: 13px;");
                    } else if (rank == 2) {
                        rankLabel.setText("🥈 #2");
                        rankLabel.setStyle("-fx-text-fill: #475569; -fx-font-weight: bold; -fx-font-size: 13px;");
                    } else if (rank == 3) {
                        rankLabel.setText("🥉 #3");
                        rankLabel.setStyle("-fx-text-fill: #c2410c; -fx-font-weight: bold; -fx-font-size: 13px;");
                    } else {
                        rankLabel.setText("    #" + rank);
                        rankLabel.setStyle("-fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 12.5px;");
                    }

                    Label nameLabel = new Label(p.getUsername() + (isMe ? " (Bạn)" : ""));
                    nameLabel.setPrefWidth(190.0);
                    nameLabel.setMaxWidth(190.0);
                    nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                    nameLabel.setAlignment(Pos.CENTER_LEFT);
                    if (isMe) {
                        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #2563eb;");
                    } else {
                        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f172a;");
                    }

                    Label killsLabel = new Label("🎯 " + p.getKills());
                    killsLabel.setPrefWidth(85.0);
                    killsLabel.setMaxWidth(85.0);
                    killsLabel.setAlignment(Pos.CENTER_RIGHT);
                    killsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12.5px; -fx-text-fill: #334155;");

                    Label hitsLabel = new Label("💥 " + p.getHits());
                    hitsLabel.setPrefWidth(95.0);
                    hitsLabel.setMaxWidth(95.0);
                    hitsLabel.setAlignment(Pos.CENTER_RIGHT);
                    hitsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12.5px; -fx-text-fill: #334155;");

                    int pts = p.getPointsEarned();
                    Label ptsLabel = new Label((pts >= 0 ? "+" : "") + pts + " XP");
                    ptsLabel.setPrefWidth(95.0);
                    ptsLabel.setMaxWidth(95.0);
                    ptsLabel.setAlignment(Pos.CENTER_RIGHT);
                    ptsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12.5px; -fx-text-fill: " + (pts >= 0 ? "#16a34a;" : "#dc2626;"));

                    HBox row = new HBox(rankLabel, nameLabel, killsLabel, hitsLabel, ptsLabel);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-padding: 8px 16px; -fx-background-color: " + (isMe ? "#eff6ff;" : "transparent;"));

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
                showError("Chưa kết nối tới Server!");
                return;
            }

            Packet request = new Packet(PacketType.MATCH_HISTORY_REQ, "");
            clientSocket.sendPacket(request);

        } catch (GameNetworkException e) {
            showError(e.getMessage());
            LOGGER.log(Level.WARNING, "[MatchHistory] Lỗi mạng khi tải lịch sử: " + e.getErrorCode(), e);
        } catch (IOException e) {
            showError("Không thể kết nối tới Server!");
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
                    statusLabel.setText("Bạn chưa có trận đấu nào được ghi nhận gần đây.");
                    statusLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12.5px;");
                } else {
                    matchHistoryListView.getItems().setAll(list);
                    statusLabel.setText("Hiển thị " + list.size() + " trận đấu gần nhất của bạn.");
                    statusLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-font-size: 12.5px;");
                }
            });

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi phân tích dữ liệu lịch sử", e);
            Platform.runLater(() -> showError("Lỗi tải lịch sử đấu từ Server!"));
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
                detailRoomTimeLabel.setText("Phòng: " + detail.getRoomName() + " • " + detail.getDurationSeconds() + "s • " + detail.getPlayedAt());

                if (detail.getWinnerUsername() != null && !detail.getWinnerUsername().isEmpty()) {
                    winnerTextLabel.setText("Người chiến thắng: " + detail.getWinnerUsername());
                    winnerBanner.setStyle("-fx-background-color: #eff6ff; -fx-padding: 8px 14px; -fx-background-radius: 10px; -fx-border-color: #bfdbfe; -fx-border-radius: 10px;");
                } else {
                    winnerTextLabel.setText("Kết quả: Trận đấu hòa thời gian (Không có người thắng)");
                    winnerBanner.setStyle("-fx-background-color: #fefce8; -fx-padding: 8px 14px; -fx-background-radius: 10px; -fx-border-color: #fef08a; -fx-border-radius: 10px;");
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
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Tank 2D Online - Lobby");
            stage.show();

        } catch (IOException e) {
            session.addPacketListener(packetListener);
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi quay lại Lobby", e);
            showError("Không thể quay lại sảnh!");
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
            statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 12.5px;");
        }
    }
}
