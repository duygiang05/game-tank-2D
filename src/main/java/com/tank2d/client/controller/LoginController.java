package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.dto.LoginRequest;
import com.tank2d.common.dto.LoginResponse;
import com.tank2d.common.dto.RegisterResponse;
import com.tank2d.common.model.User;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.function.Consumer;

public class LoginController {

    // =========================
    // LOGIN FORM
    // =========================
    @FXML
    private VBox loginForm;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    // =========================
    // REGISTER FORM
    // =========================
    @FXML
    private VBox registerForm;

    @FXML
    private TextField registerUsernameField;

    @FXML
    private PasswordField registerPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    private final Gson gson = new Gson();

    private final ClientSession session = ClientSession.getInstance();

    // Listener dùng chung với Reader Thread
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    // =========================
    // XỬ LÝ LOGIN
    // =========================
    @FXML
    private void handleLogin(ActionEvent event) {

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        // Validate dữ liệu
        if (username.isEmpty() || password.isEmpty()) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Lỗi đăng nhập",
                    "Vui lòng nhập đầy đủ Username và Password!"
            );

            return;
        }

        Thread loginThread = new Thread(() -> {

            try {

                // Kết nối ClientSession
                session.connect();

                ClientSocket clientSocket = session.getClientSocket();

                if (clientSocket == null || !clientSocket.isConnected()) {
                    showError("Không thể kết nối tới Server!");
                    return;
                }

                /*
                 * QUAN TRỌNG:
                 * Không gọi receivePacket() ở đây.
                 *
                 * ClientSession đã có Reader Thread.
                 * Reader Thread sẽ nhận AUTH_LOGIN_RES
                 * và chuyển packet vào handleServerPacket().
                 */
                session.addPacketListener(packetListener);

                // Tạo LoginRequest
                LoginRequest request =
                        new LoginRequest(username, password);

                String requestJson =
                        gson.toJson(request);

                Packet packet =
                        new Packet(
                                PacketType.AUTH_LOGIN_REQ,
                                requestJson
                        );

                // Chỉ gửi request
                clientSocket.sendPacket(packet);

                System.out.println(
                        "[Login] Đã gửi yêu cầu đăng nhập: " + username
                );

            } catch (IOException e) {

                session.removePacketListener(packetListener);

                showError(
                        "Không thể kết nối tới Server!\n"
                        + "Vui lòng kiểm tra Server đang chạy."
                );

                System.err.println(
                        "[Login] Lỗi kết nối: "
                        + e.getMessage()
                );
            }

        });

        loginThread.setDaemon(true);
        loginThread.start();
    }

    // =========================
    // NHẬN PACKET TỪ SERVER
    // =========================
    private void handleServerPacket(Packet packet) {

        if (packet == null || packet.getType() == null) {
            return;
        }

        if (packet.getType() != PacketType.AUTH_LOGIN_RES) {
            return;
        }

        System.out.println(
                "[Login] Nhận AUTH_LOGIN_RES từ Server."
        );

        try {

            LoginResponse response =
                    gson.fromJson(
                            packet.getData(),
                            LoginResponse.class
                    );

            // Không cần LoginController nghe nữa
            session.removePacketListener(packetListener);

            Platform.runLater(() -> {

                if (response.isSuccess()) {

                    // Lưu thông tin user
                    session.setCurrentUser(
                            new User(
                                    response.getUserId(),
                                    response.getUsername()
                            )
                    );

                    System.out.println(
                            "[Login] Đăng nhập thành công: "
                            + response.getUsername()
                    );

                    Alert alert =
                            new Alert(
                                    Alert.AlertType.INFORMATION
                            );

                    alert.setTitle("Đăng nhập");
                    alert.setHeaderText(null);
                    alert.setContentText(
                            response.getMessage()
                    );

                    alert.showAndWait();

                    // Mở Lobby
                    openLobby();

                } else {

                    showAlert(
                            Alert.AlertType.ERROR,
                            "Lỗi đăng nhập",
                            response.getMessage()
                    );
                }
            });

        } catch (Exception e) {

            session.removePacketListener(packetListener);

            System.err.println(
                    "[Login] Lỗi xử lý AUTH_LOGIN_RES: "
                    + e.getMessage()
            );

            showError(
                    "Không thể xử lý phản hồi đăng nhập!"
            );
        }
    }

    // =========================
    // XỬ LÝ REGISTER
    // =========================
    @FXML
    private void handleRegister(ActionEvent event) {

        String username =
                registerUsernameField.getText().trim();

        String password =
                registerPasswordField.getText().trim();

        String confirmPassword =
                confirmPasswordField.getText().trim();

        // Validate
        if (username.isEmpty()
                || password.isEmpty()
                || confirmPassword.isEmpty()) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Lỗi đăng ký",
                    "Vui lòng nhập đầy đủ thông tin!"
            );

            return;
        }

        // Kiểm tra password
        if (!password.equals(confirmPassword)) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Lỗi đăng ký",
                    "Mật khẩu xác nhận không khớp!"
            );

            return;
        }

        Thread registerThread = new Thread(() -> {

            ClientSocket clientSocket =
                    new ClientSocket();

            try {

                // Kết nối Server
                clientSocket.connect();

                // Tạo LoginRequest
                LoginRequest request =
                        new LoginRequest(
                                username,
                                password
                        );

                String requestJson =
                        gson.toJson(request);

                Packet packet =
                        new Packet(
                                PacketType.AUTH_REGISTER_REQ,
                                requestJson
                        );

                // Gửi tới Server
                clientSocket.sendPacket(packet);

                // Register dùng socket riêng nên vẫn nhận response trực tiếp
                Packet responsePacket =
                        clientSocket.receivePacket();

                if (responsePacket == null) {

                    showError(
                            "Không nhận được phản hồi từ Server!"
                    );

                    return;
                }

                if (responsePacket.getType()
                        != PacketType.AUTH_REGISTER_RES) {

                    showError(
                            "Server trả về phản hồi không hợp lệ!"
                    );

                    return;
                }

                RegisterResponse response =
                        gson.fromJson(
                                responsePacket.getData(),
                                RegisterResponse.class
                        );

                Platform.runLater(() -> {

                    if (response.isSuccess()) {

                        showAlert(
                                Alert.AlertType.INFORMATION,
                                "Đăng ký",
                                response.getMessage()
                        );

                        // Xóa form
                        registerUsernameField.clear();
                        registerPasswordField.clear();
                        confirmPasswordField.clear();

                        // Quay về Login
                        showLogin(null);

                    } else {

                        showAlert(
                                Alert.AlertType.ERROR,
                                "Lỗi đăng ký",
                                response.getMessage()
                        );
                    }
                });

            } catch (IOException e) {

                showError(
                        "Không thể kết nối tới Server!\n"
                        + "Vui lòng kiểm tra Server đang chạy."
                );

                System.err.println(
                        "[Register] Lỗi kết nối: "
                        + e.getMessage()
                );

            } finally {

                clientSocket.close();
            }

        });

        registerThread.setDaemon(true);
        registerThread.start();
    }

    // =========================
    // CHUYỂN LOGIN → REGISTER
    // =========================
    @FXML
    private void showRegister(ActionEvent event) {

        loginForm.setVisible(false);
        loginForm.setManaged(false);

        registerForm.setVisible(true);
        registerForm.setManaged(true);
    }

    // =========================
    // CHUYỂN REGISTER → LOGIN
    // =========================
    @FXML
    private void showLogin(ActionEvent event) {

        registerForm.setVisible(false);
        registerForm.setManaged(false);

        loginForm.setVisible(true);
        loginForm.setManaged(true);
    }

    // =========================
    // MỞ LOBBY
    // =========================
    private void openLobby() {

        try {

            javafx.fxml.FXMLLoader loader =
                    new javafx.fxml.FXMLLoader(
                            getClass().getResource(
                                    "/com/tank2d/client/view/lobby.fxml"
                            )
                    );

            javafx.scene.Parent lobbyRoot =
                    loader.load();

            javafx.scene.Scene lobbyScene =
                    new javafx.scene.Scene(lobbyRoot);

            javafx.stage.Stage stage =
                    (javafx.stage.Stage) usernameField
                            .getScene()
                            .getWindow();

            stage.setScene(lobbyScene);

            stage.setTitle(
                    "Tank 2D Online - Lobby"
            );

        } catch (IOException e) {

            e.printStackTrace();

            showError(
                    "Không thể mở màn hình Lobby!"
            );
        }
    }

    // =========================
    // HIỂN THỊ ALERT
    // =========================
    private void showAlert(
            Alert.AlertType type,
            String title,
            String message) {

        Platform.runLater(() -> {

            Alert alert =
                    new Alert(type);

            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            alert.showAndWait();
        });
    }

    // =========================
    // HIỂN THỊ LỖI
    // =========================
    private void showError(String message) {

        Platform.runLater(() -> {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Lỗi kết nối",
                    message
            );
        });
    }
}