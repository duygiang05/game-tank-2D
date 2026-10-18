package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.dto.LeaderboardDTO;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Leaderboard UI. Network messages, DTO parsing and navigation are unchanged.
 * Visual formatting is delegated to leaderboard-theme.css.
 */
public class LeaderboardController {
    private static final Logger LOGGER = Logger.getLogger(LeaderboardController.class.getName());

    @FXML private ListView<LeaderboardDTO> leaderboardListView;
    @FXML private Label statusLabel;
    @FXML private Button backButton;

    private final ClientSession session = ClientSession.getInstance();
    private final Gson gson = new Gson();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    @FXML
    private void initialize() {
        session.addPacketListener(packetListener);
        leaderboardListView.setCellFactory(list -> new ListCell<LeaderboardDTO>() {
            @Override
            protected void updateItem(LeaderboardDTO player, boolean empty) {
                super.updateItem(player, empty);
                setText(null);
                if (empty || player == null) {
                    setGraphic(null);
                    return;
                }

                int rank = player.getRank();
                Label rankLabel = new Label(rank <= 3
                        ? (rank == 1 ? "★ #1" : rank == 2 ? "◆ #2" : "▲ #3")
                        : "#" + rank);
                rankLabel.getStyleClass().add("leaderboard-rank");
                rankLabel.setPrefWidth(73);
                rankLabel.setMinWidth(73);

                Label nameLabel = new Label(player.getUsername());
                nameLabel.getStyleClass().add("leaderboard-name");
                nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                nameLabel.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(nameLabel, Priority.ALWAYS);

                Label pointsLabel = new Label(String.format("%,d", player.getTotalPoints()));
                pointsLabel.getStyleClass().add("leaderboard-points");
                pointsLabel.setAlignment(Pos.CENTER_RIGHT);
                pointsLabel.setPrefWidth(110);
                pointsLabel.setMinWidth(110);

                Label killsLabel = new Label(String.valueOf(player.getTotalKills()));
                killsLabel.getStyleClass().add("leaderboard-kills");
                killsLabel.setAlignment(Pos.CENTER_RIGHT);
                killsLabel.setPrefWidth(85);
                killsLabel.setMinWidth(85);

                Label winsLabel = new Label(String.valueOf(player.getTotalWins()));
                winsLabel.getStyleClass().add("leaderboard-wins");
                winsLabel.setAlignment(Pos.CENTER_RIGHT);
                winsLabel.setPrefWidth(85);
                winsLabel.setMinWidth(85);

                HBox row = new HBox(0, rankLabel, nameLabel, pointsLabel, killsLabel, winsLabel);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("leaderboard-entry");
                if (rank == 1) row.getStyleClass().add("leaderboard-gold");
                else if (rank == 2) row.getStyleClass().add("leaderboard-silver");
                else if (rank == 3) row.getStyleClass().add("leaderboard-bronze");
                setGraphic(row);
            }
        });
        loadLeaderboard();
    }

    private void loadLeaderboard() {
        try {
            ClientSocket socket = session.getClientSocket();
            if (socket == null || !socket.isConnected()) {
                statusLabel.setText("Chưa kết nối tới Server!");
                return;
            }
            socket.sendPacket(new Packet(PacketType.LEADERBOARD_REQ, ""));
            statusLabel.setText("Đang tải bảng xếp hạng...");
        } catch (GameNetworkException e) {
            statusLabel.setText(e.getMessage());
            LOGGER.log(Level.WARNING, "[Leaderboard] Lỗi nghiệp vụ khi tải bảng xếp hạng", e);
        } catch (IOException e) {
            statusLabel.setText("Không thể kết nối tới Server!");
            LOGGER.log(Level.SEVERE, "[Leaderboard] Lỗi gửi request", e);
        }
    }

    private void handleServerPacket(Packet packet) {
        if (packet == null || packet.getType() == null) return;
        if (packet.getType() == PacketType.LEADERBOARD_RES) {
            handleLeaderboardResponse(packet.getData());
        }
    }

    private void handleLeaderboardResponse(String rawJson) {
        try {
            Type type = new TypeToken<List<LeaderboardDTO>>() {}.getType();
            List<LeaderboardDTO> leaderboard = gson.fromJson(rawJson, type);
            Platform.runLater(() -> {
                leaderboardListView.getItems().clear();
                if (leaderboard == null || leaderboard.isEmpty()) {
                    statusLabel.setText("Chưa có dữ liệu xếp hạng.");
                    return;
                }
                leaderboardListView.getItems().addAll(leaderboard);
                statusLabel.setText("Đang hiển thị Top " + leaderboard.size() + " người chơi hàng đầu.");
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Leaderboard] Lỗi xử lý dữ liệu", e);
        }
    }

    @FXML
    private void handleBack() {
        session.removePacketListener(packetListener);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/tank2d/client/view/lobby.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root,
                    com.tank2d.common.config.ConfigLoader.getWindowWidth(),
                    com.tank2d.common.config.ConfigLoader.getWindowHeight()));
            stage.setTitle("Tank 2D Online - Lobby");
            stage.show();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Leaderboard] Không thể quay lại Lobby", e);
        }
    }
}
