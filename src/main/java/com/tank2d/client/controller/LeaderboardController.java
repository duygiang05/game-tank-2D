package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.dto.LeaderboardDTO;
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
import javafx.stage.Stage;

import com.tank2d.common.exception.GameNetworkException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bộ điều khiển màn hình Bảng xếp hạng người chơi (Leaderboard Controller).
 * Hiển thị thứ hạng Top người chơi theo điểm tích lũy, số mạng hạ gục và số trận thắng.
 */
public class LeaderboardController {

    private static final Logger LOGGER = Logger.getLogger(LeaderboardController.class.getName());

    @FXML
    private ListView<LeaderboardDTO> leaderboardListView;

    @FXML
    private Label statusLabel;

    @FXML
    private Button backButton;

    private final ClientSession session = ClientSession.getInstance();
    private final Gson gson = new Gson();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    /**
     * Khởi tạo giao diện bảng xếp hạng và gửi yêu cầu tải dữ liệu từ server.
     */
    @FXML
    private void initialize() {
        session.addPacketListener(packetListener);

        leaderboardListView.setCellFactory(listView -> new ListCell<LeaderboardDTO>() {
            @Override
            protected void updateItem(LeaderboardDTO player, boolean empty) {
                super.updateItem(player, empty);

                if (empty || player == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    int rank = player.getRank();

                    Label rankLabel = new Label();
                    rankLabel.setPrefWidth(80);
                    rankLabel.setAlignment(Pos.CENTER_LEFT);

                    if (rank == 1) {
                        rankLabel.setText("🥇 #1");
                        rankLabel.setStyle("-fx-text-fill: #b45309; -fx-font-weight: bold; -fx-font-size: 13.5px;");
                    } else if (rank == 2) {
                        rankLabel.setText("🥈 #2");
                        rankLabel.setStyle("-fx-text-fill: #475569; -fx-font-weight: bold; -fx-font-size: 13.5px;");
                    } else if (rank == 3) {
                        rankLabel.setText("🥉 #3");
                        rankLabel.setStyle("-fx-text-fill: #c2410c; -fx-font-weight: bold; -fx-font-size: 13.5px;");
                    } else {
                        rankLabel.setText("    #" + rank);
                        rankLabel.setStyle("-fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 13px;");
                    }

                    Label nameLabel = new Label(player.getUsername());
                    nameLabel.setPrefWidth(200);
                    nameLabel.setMaxWidth(200);
                    nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                    nameLabel.setAlignment(Pos.CENTER_LEFT);
                    nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px;");

                    Label pointsLabel = new Label(String.format("%,d", player.getTotalPoints()));
                    pointsLabel.setPrefWidth(120);
                    pointsLabel.setAlignment(Pos.CENTER_RIGHT);
                    pointsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px;");

                    Label killsLabel = new Label(String.valueOf(player.getTotalKills()));
                    killsLabel.setPrefWidth(120);
                    killsLabel.setAlignment(Pos.CENTER_RIGHT);
                    killsLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 13px;");

                    Label winsLabel = new Label(String.valueOf(player.getTotalWins()));
                    winsLabel.setPrefWidth(100);
                    winsLabel.setAlignment(Pos.CENTER_RIGHT);
                    winsLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-font-size: 13px;");

                    HBox row = new HBox(rankLabel, nameLabel, pointsLabel, killsLabel, winsLabel);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-padding: 6px 10px; -fx-background-radius: 8px;");

                    if (rank == 1) {
                        row.setStyle(row.getStyle() + "-fx-background-color: #fefce8; -fx-border-color: #fef08a; -fx-border-width: 1px; -fx-border-radius: 8px;");
                        nameLabel.setStyle(nameLabel.getStyle() + "-fx-text-fill: #854d0e;");
                        pointsLabel.setStyle(pointsLabel.getStyle() + "-fx-text-fill: #d97706;");
                    } else if (rank == 2) {
                        row.setStyle(row.getStyle() + "-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-border-radius: 8px;");
                        nameLabel.setStyle(nameLabel.getStyle() + "-fx-text-fill: #1e293b;");
                        pointsLabel.setStyle(pointsLabel.getStyle() + "-fx-text-fill: #2563eb;");
                    } else if (rank == 3) {
                        row.setStyle(row.getStyle() + "-fx-background-color: #fff7ed; -fx-border-color: #ffedd5; -fx-border-width: 1px; -fx-border-radius: 8px;");
                        nameLabel.setStyle(nameLabel.getStyle() + "-fx-text-fill: #9a3412;");
                        pointsLabel.setStyle(pointsLabel.getStyle() + "-fx-text-fill: #ea580c;");
                    } else {
                        row.setStyle(row.getStyle() + "-fx-background-color: transparent;");
                        nameLabel.setStyle(nameLabel.getStyle() + "-fx-text-fill: #334155;");
                        pointsLabel.setStyle(pointsLabel.getStyle() + "-fx-text-fill: #2563eb;");
                    }

                    setGraphic(row);
                    setText(null);
                    setStyle("-fx-background-color: transparent; -fx-padding: 2px 4px;");
                }
            }
        });

        loadLeaderboard();
    }

    /**
     * Gửi yêu cầu lấy dữ liệu bảng xếp hạng từ máy chủ.
     */
    private void loadLeaderboard() {
        try {
            ClientSocket clientSocket = session.getClientSocket();
            if (clientSocket == null || !clientSocket.isConnected()) {
                statusLabel.setText("Chưa kết nối tới Server!");
                return;
            }

            Packet request = new Packet(PacketType.LEADERBOARD_REQ, "");
            clientSocket.sendPacket(request);
            statusLabel.setText("Đang tải bảng xếp hạng...");
        } catch (GameNetworkException e) {
            statusLabel.setText(e.getMessage());
            LOGGER.log(Level.WARNING, "[Leaderboard] Lỗi nghiệp vụ khi tải bảng xếp hạng", e);
        } catch (IOException e) {
            statusLabel.setText("Không thể kết nối tới Server!");
            LOGGER.log(Level.SEVERE, "[Leaderboard] Lỗi gửi request", e);
        }
    }

    /**
     * Xử lý gói tin nhận từ máy chủ.
     *
     * @param packet gói tin nhận được
     */
    private void handleServerPacket(Packet packet) {
        if (packet == null || packet.getType() == null) return;

        if (packet.getType() == PacketType.LEADERBOARD_RES) {
            handleLeaderboardResponse(packet.getData());
        }
    }

    /**
     * Phân tích và render danh sách xếp hạng nhận được lên ListView.
     *
     * @param rawJson chuỗi JSON danh sách LeaderboardDTO
     */
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

    /**
     * Quay trở về màn hình sảnh chờ (Lobby).
     */
    @FXML
    private void handleBack() {
        session.removePacketListener(packetListener);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tank2d/client/view/lobby.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Tank 2D Online - Lobby");
            stage.show();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Leaderboard] Không thể quay lại Lobby", e);
        }
    }
}