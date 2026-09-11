package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.dto.LoginRequest;
import com.tank2d.common.dto.LoginResponse;
import com.tank2d.common.dto.RegisterResponse;
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

public class LoginController {

    // Login form
    @FXML
    private VBox loginForm;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    // Register form
    @FXML
    private VBox registerForm;

    @FXML
    private TextField registerUsernameField;

    @FXML
    private PasswordField registerPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    private final Gson gson = new Gson();

    // Xử lý Login
    @FXML
    private void handleLogin(ActionEvent event) {

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        // Validate
        if (username.isEmpty() || password.isEmpty()) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Lỗi đăng nhập",
                    "Vui lòng nhập đầy đủ Username và Password!"
            );
            return;
        }

        // Chạy network ở thread riêng để không làm treo JavaFX UI
        Thread loginThread = new Thread(() -> {

            ClientSocket clientSocket = new ClientSocket();

            try {
                // 1. Kết nối tới Server
                clientSocket.connect();

                // 2. Tạo LoginRequest
                LoginRequest request =
                        new LoginRequest(username, password);

                // 3. Chuyển LoginRequest thành JSON
                String requestJson = gson.toJson(request);

                // 4. Đóng gói thành Packet AUTH_LOGIN_REQ
                Packet packet = new Packet(
                        PacketType.AUTH_LOGIN_REQ,
                        requestJson
                );

                // 5. Gửi Packet tới Server
                clientSocket.sendPacket(packet);

                // 6. Nhận phản hồi từ Server
                Packet responsePacket = clientSocket.receivePacket();

                if (responsePacket == null) {
                    showError("Không nhận được phản hồi từ Server!");
                    return;
                }

                // Kiểm tra loại Packet
                if (responsePacket.getType()
                        != PacketType.AUTH_LOGIN_RES) {

                    showError("Server trả về phản hồi không hợp lệ!");
                    return;
                }

                // 7. Chuyển JSON response thành LoginResponse
                LoginResponse response =
                        gson.fromJson(
                                responsePacket.getData(),
                                LoginResponse.class
                        );

                // 8. Hiển thị kết quả trên JavaFX UI
                Platform.runLater(() -> {

                    if (response.isSuccess()) {

                        showAlert(
                                Alert.AlertType.INFORMATION,
                                "Đăng nhập",
                                response.getMessage()
                        );

                    } else {

                        showAlert(
                                Alert.AlertType.ERROR,
                                "Lỗi đăng nhập",
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
                        "[Login] Lỗi kết nối: "
                        + e.getMessage()
                );

            } finally {

                // Đóng socket sau khi Login xong
                clientSocket.close();
            }

        });

        loginThread.setDaemon(true);
        loginThread.start();
    }

    // Xử lý Register
    @FXML
private void handleRegister(ActionEvent event) {

    String username = registerUsernameField.getText().trim();
    String password = registerPasswordField.getText().trim();
    String confirmPassword = confirmPasswordField.getText().trim();

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

    if (!password.equals(confirmPassword)) {

        showAlert(
                Alert.AlertType.ERROR,
                "Lỗi đăng ký",
                "Mật khẩu xác nhận không khớp!"
        );
        return;
    }

    // Chạy network ở thread riêng
    Thread registerThread = new Thread(() -> {

        ClientSocket clientSocket = new ClientSocket();

        try {
            // 1. Kết nối Server
            clientSocket.connect();

            // 2. Tạo LoginRequest
            LoginRequest request =
                    new LoginRequest(username, password);

            // 3. Chuyển request thành JSON
            String requestJson = gson.toJson(request);

            // 4. Đóng gói Packet
            Packet packet = new Packet(
                    PacketType.AUTH_REGISTER_REQ,
                    requestJson
            );

            // 5. Gửi tới Server
            clientSocket.sendPacket(packet);

            // 6. Nhận response
            Packet responsePacket =
                    clientSocket.receivePacket();

            if (responsePacket == null) {
                showError("Không nhận được phản hồi từ Server!");
                return;
            }

            // 7. Kiểm tra PacketType
            if (responsePacket.getType()
                    != PacketType.AUTH_REGISTER_RES) {

                showError(
                        "Server trả về phản hồi không hợp lệ!"
                );
                return;
            }

            // 8. Chuyển JSON thành RegisterResponse
            RegisterResponse response =
                    gson.fromJson(
                            responsePacket.getData(),
                            RegisterResponse.class
                    );

            // 9. Hiển thị kết quả
            Platform.runLater(() -> {

                if (response.isSuccess()) {

                    showAlert(
                            Alert.AlertType.INFORMATION,
                            "Đăng ký",
                            response.getMessage()
                    );

                    // Xóa form sau khi đăng ký thành công
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
    @FXML
    private void showRegister(ActionEvent event) {

        loginForm.setVisible(false);
        loginForm.setManaged(false);

        registerForm.setVisible(true);
        registerForm.setManaged(true);
    }

    // Chuyển về Login
    @FXML
    private void showLogin(ActionEvent event) {

        registerForm.setVisible(false);
        registerForm.setManaged(false);

        loginForm.setVisible(true);
        loginForm.setManaged(true);
    }

    // Hiển thị Alert trên JavaFX UI thread
    private void showAlert(
            Alert.AlertType type,
            String title,
            String message) {

        Platform.runLater(() -> {

            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();

        });
    }

    // Hiển thị lỗi từ background thread
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