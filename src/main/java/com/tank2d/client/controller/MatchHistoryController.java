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
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** History UI; existing network protocol and navigation retained. */
public class MatchHistoryController {
    private static final Logger LOGGER = Logger.getLogger(MatchHistoryController.class.getName());
    @FXML private StackPane rootStackPane;
    @FXML private BorderPane mainContentPane;
    @FXML private ListView<UserMatchHistoryDTO> matchHistoryListView;
    @FXML private Label statusLabel;
    @FXML private Button backButton;
    @FXML private StackPane detailOverlay;
    @FXML private VBox detailCard;
    @FXML private Label detailRoomTimeLabel;
    @FXML private HBox winnerBanner;
    @FXML private Label winnerTextLabel;
    @FXML private ListView<MatchParticipantDTO> participantListView;
    private final ClientSession session = ClientSession.getInstance();
    private final Gson gson = new Gson();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    private Label column(String content, String css, double width) {
        Label label = new Label(content);
        label.getStyleClass().add(css);
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
    }

    @FXML private void initialize() {
        session.addPacketListener(packetListener);
        matchHistoryListView.setCellFactory(view -> new ListCell<UserMatchHistoryDTO>() {
            @Override protected void updateItem(UserMatchHistoryDTO match, boolean empty) {
                super.updateItem(match, empty);
                setText(null);
                if (empty || match == null) { setGraphic(null); return; }
                String result = match.getResult() == null ? "" : match.getResult();
                Label badge = new Label("VICTORY".equalsIgnoreCase(result) ? "THẮNG"
                        : "DRAW".equalsIgnoreCase(result) ? "HÒA" : "THUA");
                badge.getStyleClass().add("history-badge");
                badge.getStyleClass().add("VICTORY".equalsIgnoreCase(result) ? "history-win"
                        : "DRAW".equalsIgnoreCase(result) ? "history-draw" : "history-lose");
                Label rank = new Label("#" + match.getRankPosition());
                rank.getStyleClass().add("history-rank");
                HBox placement = new HBox(6, badge, rank);
                placement.setAlignment(Pos.CENTER_LEFT);
                placement.setMinWidth(143); placement.setPrefWidth(143);
                Label room = new Label(match.getRoomName());
                room.getStyleClass().add("history-room-name");
                room.setTextOverrun(OverrunStyle.ELLIPSIS);
                Label duration = new Label("Thời lượng: " + match.getDurationSeconds() + "s");
                duration.getStyleClass().add("history-secondary");
                VBox roomInfo = new VBox(3, room, duration);
                roomInfo.setMinWidth(150);
                roomInfo.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(roomInfo, Priority.ALWAYS);
                Label played = column(match.getPlayedAt(), "history-date", 145);
                played.setAlignment(Pos.CENTER_LEFT);
                Label combat = column("🎯 " + match.getKills() + "   💥 " + match.getHits(), "history-combat", 112);
                int xp = match.getPointsEarned();
                Label points = column((xp >= 0 ? "+" : "") + xp + " XP", xp >= 0 ? "history-xp-positive" : "history-xp-negative", 90);
                HBox row = new HBox(placement, roomInfo, played, combat, points);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("history-row");
                setGraphic(row);
            }
        });
        matchHistoryListView.setOnMouseClicked(event -> {
            UserMatchHistoryDTO match = matchHistoryListView.getSelectionModel().getSelectedItem();
            if (match != null) requestMatchDetail(match.getMatchId());
        });
        participantListView.setCellFactory(view -> new ListCell<MatchParticipantDTO>() {
            @Override protected void updateItem(MatchParticipantDTO p, boolean empty) {
                super.updateItem(p, empty);
                setText(null);
                if (empty || p == null) { setGraphic(null); return; }
                int currentId = session.getCurrentUser() == null ? -1 : session.getCurrentUser().getId();
                boolean me = p.getUserId() == currentId;
                Label rank = column("#" + p.getRankPosition(), "history-rank", 62);
                rank.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(p.getUsername() + (me ? " (Bạn)" : ""));
                name.getStyleClass().add(me ? "history-player-me" : "history-player");
                name.setMaxWidth(Double.MAX_VALUE);
                name.setTextOverrun(OverrunStyle.ELLIPSIS);
                HBox.setHgrow(name, Priority.ALWAYS);
                Label kills = column(String.valueOf(p.getKills()), "history-combat", 73);
                Label hits = column(String.valueOf(p.getHits()), "history-combat", 81);
                int xp = p.getPointsEarned();
                Label points = column((xp >= 0 ? "+" : "") + xp + " XP", xp >= 0 ? "history-xp-positive" : "history-xp-negative", 96);
                HBox row = new HBox(rank, name, kills, hits, points);
                row.getStyleClass().add("history-participant-row");
                if (me) row.getStyleClass().add("history-own-row");
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        loadMatchHistory();
    }

    private void loadMatchHistory() {
        try {
            ClientSocket socket = session.getClientSocket();
            if (socket == null || !socket.isConnected()) { showError("Chưa kết nối tới Server!"); return; }
            socket.sendPacket(new Packet(PacketType.MATCH_HISTORY_REQ, ""));
        } catch (GameNetworkException e) {
            showError(e.getMessage());
            LOGGER.log(Level.WARNING, "[MatchHistory] Lỗi mạng khi tải lịch sử: " + e.getErrorCode(), e);
        } catch (IOException e) {
            showError("Không thể kết nối tới Server!");
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi gửi yêu cầu lịch sử đấu", e);
        }
    }
    private void requestMatchDetail(int matchId) {
        try {
            ClientSocket socket = session.getClientSocket();
            if (socket != null && socket.isConnected())
                socket.sendPacket(new Packet(PacketType.MATCH_DETAIL_REQ, gson.toJson(matchId)));
        } catch (Exception e) { LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi yêu cầu chi tiết trận đấu", e); }
    }
    private void handleServerPacket(Packet packet) {
        if (packet == null || packet.getType() == null) return;
        if (packet.getType() == PacketType.MATCH_HISTORY_RES) handleMatchHistoryResponse(packet.getData());
        else if (packet.getType() == PacketType.MATCH_DETAIL_RES) handleMatchDetailResponse(packet.getData());
    }
    private void handleMatchHistoryResponse(String rawJson) {
        try {
            Type type = new TypeToken<List<UserMatchHistoryDTO>>() {}.getType();
            List<UserMatchHistoryDTO> matches = gson.fromJson(rawJson, type);
            Platform.runLater(() -> {
                if (matches == null || matches.isEmpty()) {
                    matchHistoryListView.getItems().clear();
                    statusLabel.setText("Bạn chưa có trận đấu nào được ghi nhận gần đây.");
                } else {
                    matchHistoryListView.getItems().setAll(matches);
                    statusLabel.setText("Hiển thị " + matches.size() + " trận đấu gần nhất của bạn.");
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi phân tích dữ liệu lịch sử", e);
            Platform.runLater(() -> showError("Lỗi tải lịch sử đấu từ Server!"));
        }
    }
    private void handleMatchDetailResponse(String rawJson) {
        try {
            MatchDetailDTO detail = gson.fromJson(rawJson, MatchDetailDTO.class);
            if (detail == null) return;
            Platform.runLater(() -> {
                detailRoomTimeLabel.setText("Phòng: " + detail.getRoomName() + " • "
                        + detail.getDurationSeconds() + "s • " + detail.getPlayedAt());
                boolean hasWinner = detail.getWinnerUsername() != null && !detail.getWinnerUsername().isEmpty();
                winnerTextLabel.setText(hasWinner ? "Người chiến thắng: " + detail.getWinnerUsername()
                        : "Trận đấu hòa thời gian — Không có người thắng");
                winnerBanner.getStyleClass().removeAll("history-winner", "history-draw-banner");
                winnerBanner.getStyleClass().add(hasWinner ? "history-winner" : "history-draw-banner");
                participantListView.getItems().setAll(detail.getParticipants() == null ? List.of() : detail.getParticipants());
                mainContentPane.setEffect(new GaussianBlur(12));
                detailOverlay.setVisible(true);
            });
        } catch (Exception e) { LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi phân tích chi tiết trận đấu", e); }
    }
    @FXML private void handleCloseDetail() {
        detailOverlay.setVisible(false);
        mainContentPane.setEffect(null);
    }
    @FXML private void handleOverlayClicked(MouseEvent event) {
        if (event.getTarget() == detailOverlay) handleCloseDetail();
    }
    @FXML private void handleBack() {
        try {
            session.removePacketListener(packetListener);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tank2d/client/view/lobby.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root,
                    com.tank2d.common.config.ConfigLoader.getWindowWidth(),
                    com.tank2d.common.config.ConfigLoader.getWindowHeight()));
            stage.setTitle("Tank 2D Online - Lobby");
            stage.show();
        } catch (IOException e) {
            session.addPacketListener(packetListener);
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi quay lại Lobby", e);
            showError("Không thể quay lại sảnh!");
        }
    }
    private void showError(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.getStyleClass().add("history-error");
        }
    }
}
