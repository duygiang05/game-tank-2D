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

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

public class LeaderboardController {

    @FXML
    private ListView<LeaderboardDTO> leaderboardListView;

    @FXML
    private Label statusLabel;

    @FXML
    private Button backButton;

    private final ClientSession session = ClientSession.getInstance();
    private final Gson gson = new Gson();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    @FXML
    private void initialize() {
        session.addPacketListener(packetListener);

        // Cấu hình từng dòng hiển thị chuẩn xác, không bao giờ cuộn ngang
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

                    // Cột 1: Hạng (80px)
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

                    // Cột 2: Tên người chơi (200px) - Tự động rút gọn dấu ... nếu quá dài
                    Label nameLabel = new Label(player.getUsername());
                    nameLabel.setPrefWidth(200);
                    nameLabel.setMaxWidth(200);
                    nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                    nameLabel.setAlignment(Pos.CENTER_LEFT);
                    nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px;");

                    // Cột 3: Điểm số (120px)
                    Label pointsLabel = new Label(String.format("%,d", player.getTotalPoints()));
                    pointsLabel.setPrefWidth(120);
                    pointsLabel.setAlignment(Pos.CENTER_RIGHT);
                    pointsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px;");

                    // Cột 4: Kills (120px)
                    Label killsLabel = new Label(String.valueOf(player.getTotalKills()));
                    killsLabel.setPrefWidth(120);
                    killsLabel.setAlignment(Pos.CENTER_RIGHT);
                    killsLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 13px;");

                    // Cột 5: Wins (100px) - Hiển thị đầy đủ không bị mất
                    Label winsLabel = new Label(String.valueOf(player.getTotalWins()));
                    winsLabel.setPrefWidth(100);
                    winsLabel.setAlignment(Pos.CENTER_RIGHT);
                    winsLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-font-size: 13px;");

                    HBox row = new HBox(rankLabel, nameLabel, pointsLabel, killsLabel, winsLabel);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-padding: 6px 10px; -fx-background-radius: 8px;");

                    // Định dạng màu nền cao cấp cho Top 1, 2, 3 và người chơi thường
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
        } catch (IOException e) {
            statusLabel.setText("Không thể kết nối tới Server!");
            System.err.println("[Leaderboard] Lỗi gửi request: " + e.getMessage());
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
            System.err.println("[Leaderboard] Lỗi xử lý dữ liệu: " + e.getMessage());
        }
    }

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
            System.err.println("[Leaderboard] Không thể quay lại Lobby: " + e.getMessage());
        }
    }
}