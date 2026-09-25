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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

public class LeaderboardController {

    @FXML
    private ListView<String> leaderboardListView;

    @FXML
    private Label statusLabel;

    @FXML
    private Button backButton;

    private final ClientSession session
            = ClientSession.getInstance();

    private final Gson gson
            = new Gson();

    private final Consumer<Packet> packetListener
            = this::handleServerPacket;

    @FXML
    private void initialize() {

        session.addPacketListener(packetListener);

        leaderboardListView.setCellFactory(listView -> new ListCell<String>() {

            @Override
            protected void updateItem(String item, boolean empty) {

                super.updateItem(item, empty);

                if (empty || item == null) {

                    setText(null);
                    setStyle("");

                } else {

                    setText(item);

                    int index = getIndex();

                    // TOP 1
                    if (index == 0) {

                        setStyle(
                                "-fx-background-color: #fff4c2;"
                                + "-fx-text-fill: #8a5a00;"
                                + "-fx-font-size: 16px;"
                                + "-fx-font-weight: bold;"
                                + "-fx-padding: 10px;"
                        );

                        // TOP 2
                    } else if (index == 1) {

                        setStyle(
                                "-fx-background-color: #eef2f5;"
                                + "-fx-text-fill: #4b5563;"
                                + "-fx-font-size: 16px;"
                                + "-fx-font-weight: bold;"
                                + "-fx-padding: 10px;"
                        );

                        // TOP 3
                    } else if (index == 2) {

                        setStyle(
                                "-fx-background-color: #fff0df;"
                                + "-fx-text-fill: #9a5b20;"
                                + "-fx-font-size: 16px;"
                                + "-fx-font-weight: bold;"
                                + "-fx-padding: 10px;"
                        );

                        // Các hạng còn lại
                    } else {

                        setStyle(
                                "-fx-background-color: white;"
                                + "-fx-text-fill: #1f2937;"
                                + "-fx-font-size: 15px;"
                                + "-fx-padding: 9px;"
                        );
                    }
                }
            }
        });

        loadLeaderboard();
    }

    /**
     * Gửi request lấy bảng xếp hạng.
     */
    private void loadLeaderboard() {

        try {

            ClientSocket clientSocket
                    = session.getClientSocket();

            if (clientSocket == null
                    || !clientSocket.isConnected()) {

                statusLabel.setText(
                        "Chưa kết nối tới Server!"
                );

                return;
            }

            Packet request = new Packet(
                    PacketType.LEADERBOARD_REQ,
                    ""
            );

            clientSocket.sendPacket(request);

            statusLabel.setText(
                    "Đang tải bảng xếp hạng..."
            );

        } catch (IOException e) {

            statusLabel.setText(
                    "Không thể kết nối tới Server!"
            );

            System.err.println(
                    "[Leaderboard] Lỗi gửi request: "
                    + e.getMessage()
            );
        }
    }

    /**
     * Nhận dữ liệu từ Server.
     */
    private void handleServerPacket(Packet packet) {

        if (packet == null
                || packet.getType() == null) {

            return;
        }

        if (packet.getType()
                == PacketType.LEADERBOARD_RES) {

            handleLeaderboardResponse(
                    packet.getData()
            );
        }
    }

    /**
     * Hiển thị bảng xếp hạng.
     */
    private void handleLeaderboardResponse(
            String rawJson) {

        try {

            Type type
                    = new TypeToken<List<LeaderboardDTO>>() {
                    }.getType();

            List<LeaderboardDTO> leaderboard
                    = gson.fromJson(
                            rawJson,
                            type
                    );

            Platform.runLater(() -> {

                leaderboardListView
                        .getItems()
                        .clear();

                if (leaderboard == null
                        || leaderboard.isEmpty()) {

                    statusLabel.setText(
                            "Chưa có dữ liệu xếp hạng."
                    );

                    return;
                }

                for (LeaderboardDTO player
                        : leaderboard) {

                    String rankIcon = "";

                    if (player.getRank() == 1) {
                        rankIcon = "🥇 ";
                    } else if (player.getRank() == 2) {
                        rankIcon = "🥈 ";
                    } else if (player.getRank() == 3) {
                        rankIcon = "🥉 ";
                    }

                    String row
                            = String.format(
                                    "%s#%d   %-15s   Điểm: %-6d   Kills: %-4d   Wins: %-4d",
                                    rankIcon,
                                    player.getRank(),
                                    player.getUsername(),
                                    player.getTotalPoints(),
                                    player.getTotalKills(),
                                    player.getTotalWins()
                            );

                    leaderboardListView
                            .getItems()
                            .add(row);
                }

                statusLabel.setText(
                        "Đang hiển thị "
                        + leaderboard.size()
                        + " người chơi."
                );
            });

        } catch (Exception e) {

            System.err.println(
                    "[Leaderboard] Lỗi xử lý dữ liệu: "
                    + e.getMessage()
            );
        }
    }

    /**
     * Quay lại Lobby.
     */
    @FXML
    private void handleBack() {

        session.removePacketListener(
                packetListener
        );

        try {

            FXMLLoader loader
                    = new FXMLLoader(
                            getClass().getResource(
                                    "/com/tank2d/client/view/lobby.fxml"
                            )
                    );

            Parent root
                    = loader.load();

            Stage stage
                    = (Stage) backButton
                            .getScene()
                            .getWindow();

            stage.setScene(
                    new Scene(root, 800, 600)
            );

            stage.setTitle(
                    "Tank 2D Online - Lobby"
            );

            stage.show();

        } catch (IOException e) {

            System.err.println(
                    "[Leaderboard] Không thể quay lại Lobby: "
                    + e.getMessage()
            );
        }
    }
}
