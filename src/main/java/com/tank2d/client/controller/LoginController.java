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

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.IOException;
import java.util.function.Consumer;

public class LoginController {

    // ==========================================
    // LOGIN FORM
    // ==========================================
    @FXML
    private VBox loginForm;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField passwordTextField;

    @FXML
    private Button togglePasswordBtn;

    // ==========================================
    // REGISTER FORM
    // ==========================================
    @FXML
    private VBox registerForm;

    @FXML
    private TextField registerUsernameField;

    @FXML
    private PasswordField registerPasswordField;

    @FXML
    private TextField registerPasswordTextField;

    @FXML
    private Button toggleRegisterPasswordBtn;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private TextField confirmPasswordTextField;

    @FXML
    private Button toggleConfirmPasswordBtn;

    // ==========================================
    // TRẠNG THÁI ẨN / HIỆN MẬT KHẨU
    // ==========================================
    private boolean isPasswordVisible = false;
    private boolean isRegisterPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    private final Gson gson = new Gson();
    private final ClientSession session = ClientSession.getInstance();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    // ==========================================
    // KHỞI TẠO & RÀNG BUỘC ĐỒNG BỘ MẬT KHẨU
    // ==========================================
    @FXML
    public void initialize() {
        if (passwordField != null && passwordTextField != null) {
            passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());
        }
        if (registerPasswordField != null && registerPasswordTextField != null) {
            registerPasswordTextField.textProperty().bindBidirectional(registerPasswordField.textProperty());
        }
        if (confirmPasswordField != null && confirmPasswordTextField != null) {
            confirmPasswordTextField.textProperty().bindBidirectional(confirmPasswordField.textProperty());
        }
    }

    // ==========================================
    // CÁC HÀM TOGGLE MẮT XEM MẬT KHẨU
    // ==========================================
    @FXML
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        updateVisibility(passwordField, passwordTextField, togglePasswordBtn, isPasswordVisible);
    }

    @FXML
    private void toggleRegisterPasswordVisibility() {
        isRegisterPasswordVisible = !isRegisterPasswordVisible;
        updateVisibility(registerPasswordField, registerPasswordTextField, toggleRegisterPasswordBtn, isRegisterPasswordVisible);
    }

    @FXML
    private void toggleConfirmPasswordVisibility() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible;
        updateVisibility(confirmPasswordField, confirmPasswordTextField, toggleConfirmPasswordBtn, isConfirmPasswordVisible);
    }

    private void updateVisibility(PasswordField pf, TextField tf, Button btn, boolean visible) {
        if (pf == null || tf == null || btn == null) return;

        if (visible) {
            tf.setVisible(true);
            tf.setManaged(true);
            pf.setVisible(false);
            pf.setManaged(false);
            btn.setText("🙈");
            tf.requestFocus();
            tf.positionCaret(tf.getText().length());
        } else {
            pf.setVisible(true);
            pf.setManaged(true);
            tf.setVisible(false);
            tf.setManaged(false);
            btn.setText("👁");
            pf.requestFocus();
            pf.positionCaret(pf.getText().length());
        }
    }

    // ==========================================
    // XỬ LÝ LOGIN
    // ==========================================
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showToast("Vui lòng nhập đầy đủ Username và Password!", false);
            return;
        }

        Thread loginThread = new Thread(() -> {
            try {
                session.connect();
                ClientSocket clientSocket = session.getClientSocket();

                if (clientSocket == null || !clientSocket.isConnected()) {
                    showToast("Không thể kết nối tới Server!", false);
                    return;
                }

                session.addPacketListener(packetListener);

                LoginRequest request = new LoginRequest(username, password);
                String requestJson = gson.toJson(request);
                Packet packet = new Packet(PacketType.AUTH_LOGIN_REQ, requestJson);

                clientSocket.sendPacket(packet);
                System.out.println("[Login] Đã gửi yêu cầu đăng nhập: " + username);

            } catch (IOException e) {
                session.removePacketListener(packetListener);
                showToast("Không thể kết nối tới Server!\nVui lòng kiểm tra Server đang chạy.", false);
                System.err.println("[Login] Lỗi kết nối: " + e.getMessage());
            }
        });

        loginThread.setDaemon(true);
        loginThread.start();
    }

    // ==========================================
    // NHẬN PACKET TỪ SERVER
    // ==========================================
    private void handleServerPacket(Packet packet) {
        if (packet == null || packet.getType() == null) {
            return;
        }

        if (packet.getType() != PacketType.AUTH_LOGIN_RES) {
            return;
        }

        System.out.println("[Login] Nhận AUTH_LOGIN_RES từ Server.");

        try {
            LoginResponse response = gson.fromJson(packet.getData(), LoginResponse.class);
            session.removePacketListener(packetListener);

            Platform.runLater(() -> {
                if (response.isSuccess()) {
                    session.setCurrentUser(new User(response.getUserId(), response.getUsername()));
                    System.out.println("[Login] Đăng nhập thành công: " + response.getUsername());

                    showToast("Đăng nhập thành công! Đang vào sảnh...", true);

                    PauseTransition delay = new PauseTransition(Duration.seconds(1));
                    delay.setOnFinished(e -> openLobby());
                    delay.play();

                } else {
                    showToast(response.getMessage() != null ? response.getMessage() : "Sai tài khoản hoặc mật khẩu!", false);
                }
            });

        } catch (Exception e) {
            session.removePacketListener(packetListener);
            System.err.println("[Login] Lỗi xử lý AUTH_LOGIN_RES: " + e.getMessage());
            showToast("Không thể xử lý phản hồi đăng nhập!", false);
        }
    }

    // ==========================================
    // XỬ LÝ REGISTER
    // ==========================================
    @FXML
    private void handleRegister(ActionEvent event) {
        String username = registerUsernameField.getText().trim();
        String password = registerPasswordField.getText().trim();
        String confirmPassword = confirmPasswordField.getText().trim();

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showToast("Vui lòng nhập đầy đủ thông tin đăng ký!", false);
            return;
        }

        if (!password.equals(confirmPassword)) {
            showToast("Mật khẩu xác nhận không khớp!", false);
            return;
        }

        Thread registerThread = new Thread(() -> {
            ClientSocket clientSocket = new ClientSocket();
            try {
                clientSocket.connect();

                LoginRequest request = new LoginRequest(username, password);
                String requestJson = gson.toJson(request);
                Packet packet = new Packet(PacketType.AUTH_REGISTER_REQ, requestJson);

                clientSocket.sendPacket(packet);

                Packet responsePacket = clientSocket.receivePacket();
                if (responsePacket == null || responsePacket.getType() != PacketType.AUTH_REGISTER_RES) {
                    showToast("Không nhận được phản hồi hợp lệ từ Server!", false);
                    return;
                }

                RegisterResponse response = gson.fromJson(responsePacket.getData(), RegisterResponse.class);

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        showToast(response.getMessage() != null ? response.getMessage() : "Đăng ký thành công!", true);

                        registerUsernameField.clear();
                        registerPasswordField.clear();
                        confirmPasswordField.clear();

                        showLogin(null);
                    } else {
                        showToast(response.getMessage() != null ? response.getMessage() : "Đăng ký thất bại!", false);
                    }
                });

            } catch (IOException e) {
                showToast("Không thể kết nối tới Server để đăng ký!", false);
                System.err.println("[Register] Lỗi kết nối: " + e.getMessage());
            } finally {
                clientSocket.close();
            }
        });

        registerThread.setDaemon(true);
        registerThread.start();
    }

    // ==========================================
    // CHUYỂN ĐỔI GIỮA CÁC FORM
    // ==========================================
    @FXML
    private void showRegister(ActionEvent event) {
        loginForm.setVisible(false);
        loginForm.setManaged(false);

        registerForm.setVisible(true);
        registerForm.setManaged(true);
    }

    @FXML
    private void showLogin(ActionEvent event) {
        registerForm.setVisible(false);
        registerForm.setManaged(false);

        loginForm.setVisible(true);
        loginForm.setManaged(true);
    }

    // ==========================================
    // CHUYỂN VÀO LOBBY
    // ==========================================
    private void openLobby() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/com/tank2d/client/view/lobby.fxml")
            );
            javafx.scene.Parent lobbyRoot = loader.load();
            javafx.scene.Scene lobbyScene = new javafx.scene.Scene(lobbyRoot);

            javafx.stage.Stage stage = (javafx.stage.Stage) usernameField.getScene().getWindow();
            stage.setScene(lobbyScene);
            stage.setTitle("Tank 2D Online - Lobby");

        } catch (IOException e) {
            e.printStackTrace();
            showToast("Không thể mở màn hình Lobby!", false);
        }
    }

    // ==========================================
    // TOAST NOTIFICATION (TỰ TẮT SAU 5 GIÂY)
    // ==========================================
    private void showToast(String message, boolean isSuccess) {
        Platform.runLater(() -> {
            try {
                Window window = usernameField.getScene() != null ? usernameField.getScene().getWindow() : null;
                if (window == null) return;

                Popup popup = new Popup();
                popup.setAutoFix(true);

                Label label = new Label(message);
                label.setWrapText(true);
                label.setMaxWidth(350);

                String bgColor = isSuccess ? "rgba(22, 101, 52, 0.95)" : "rgba(185, 28, 28, 0.95)";
                String borderColor = isSuccess ? "#4ade80" : "#f87171";

                label.setStyle(
                        "-fx-background-color: " + bgColor + ";"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 13.5px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 10px 18px;"
                        + "-fx-background-radius: 8px;"
                        + "-fx-border-color: " + borderColor + ";"
                        + "-fx-border-width: 1.5px;"
                        + "-fx-border-radius: 8px;"
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.35), 10, 0, 0, 4);"
                );

                popup.getContent().add(label);

                popup.show(window);
                popup.setX(window.getX() + (window.getWidth() - label.getWidth()) / 2.0);
                popup.setY(window.getY() + 65.0);

                PauseTransition visiblePause = new PauseTransition(Duration.seconds(4.5));
                visiblePause.setOnFinished(e -> {
                    FadeTransition fade = new FadeTransition(Duration.millis(500), label);
                    fade.setFromValue(1.0);
                    fade.setToValue(0.0);
                    fade.setOnFinished(f -> popup.hide());
                    fade.play();
                });
                visiblePause.play();

            } catch (Exception ignored) {}
        });
    }
}