package com.tank2d.client.controller;

import com.google.gson.Gson;
import com.tank2d.client.ClientSession;
import com.tank2d.client.network.ClientSocket;
import com.tank2d.client.util.ToastUtil;
import com.tank2d.common.dto.LoginRequest;
import com.tank2d.common.dto.LoginResponse;
import com.tank2d.common.dto.RegisterResponse;
import com.tank2d.common.exception.GameNetworkException;
import com.tank2d.common.model.User;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Điều khiển màn hình Đăng nhập và Đăng ký tài khoản (Login/Register View).
 */
public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());
    private static final Gson GSON = new Gson();

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

    private boolean isPasswordVisible = false;
    private boolean isRegisterPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    private final ClientSession session = ClientSession.getInstance();
    private final Consumer<Packet> packetListener = this::handleServerPacket;

    /**
     * Khởi tạo giao diện và liên kết 2 chiều giữa trường PasswordField và TextField hiển thị mật khẩu.
     */
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

    /**
     * Xử lý sự kiện bấm nút Đăng nhập.
     */
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
                String requestJson = GSON.toJson(request);
                Packet packet = new Packet(PacketType.AUTH_LOGIN_REQ, requestJson);

                clientSocket.sendPacket(packet);
                LOGGER.info("[Login] Đã gửi yêu cầu đăng nhập: " + username);

            } catch (GameNetworkException e) {
                session.removePacketListener(packetListener);
                showToast(e.getMessage(), false);
                LOGGER.log(Level.WARNING, "[Login] Lỗi nghiệp vụ mạng: " + e.getErrorCode(), e);
            } catch (IOException e) {
                session.removePacketListener(packetListener);
                showToast("Không thể kết nối tới Server!\nVui lòng kiểm tra Server đang chạy.", false);
                LOGGER.log(Level.SEVERE, "[Login] Lỗi kết nối", e);
            }
        });

        loginThread.setDaemon(true);
        loginThread.start();
    }

    /**
     * Nhận và xử lý gói tin phản hồi xác thực đăng nhập từ Server.
     */
    private void handleServerPacket(Packet packet) {
        if (packet == null || packet.getType() == null) {
            return;
        }

        if (packet.getType() != PacketType.AUTH_LOGIN_RES) {
            return;
        }

        LOGGER.info("[Login] Nhận AUTH_LOGIN_RES từ Server.");

        try {
            LoginResponse response = GSON.fromJson(packet.getData(), LoginResponse.class);
            session.removePacketListener(packetListener);

            Platform.runLater(() -> {
                if (response.isSuccess()) {
                    session.setCurrentUser(new User(response.getUserId(), response.getUsername()));
                    LOGGER.info("[Login] Đăng nhập thành công: " + response.getUsername());

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
            LOGGER.log(Level.SEVERE, "[Login] Lỗi xử lý AUTH_LOGIN_RES", e);
            showToast("Không thể xử lý phản hồi đăng nhập!", false);
        }
    }

    /**
     * Xử lý sự kiện bấm nút Đăng ký tài khoản mới.
     */
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
                String requestJson = GSON.toJson(request);
                Packet packet = new Packet(PacketType.AUTH_REGISTER_REQ, requestJson);

                clientSocket.sendPacket(packet);

                Packet responsePacket = clientSocket.receivePacket();
                if (responsePacket == null || responsePacket.getType() != PacketType.AUTH_REGISTER_RES) {
                    showToast("Không nhận được phản hồi hợp lệ từ Server!", false);
                    return;
                }

                RegisterResponse response = GSON.fromJson(responsePacket.getData(), RegisterResponse.class);

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

            } catch (GameNetworkException e) {
                showToast(e.getMessage(), false);
                LOGGER.log(Level.WARNING, "[Register] Lỗi nghiệp vụ mạng: " + e.getErrorCode(), e);
            } catch (IOException e) {
                showToast("Không thể kết nối tới Server để đăng ký!", false);
                LOGGER.log(Level.SEVERE, "[Register] Lỗi kết nối", e);
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

    @FXML
    private void showLogin(ActionEvent event) {
        registerForm.setVisible(false);
        registerForm.setManaged(false);

        loginForm.setVisible(true);
        loginForm.setManaged(true);
    }

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
            LOGGER.log(Level.SEVERE, "[Login] Không thể mở màn hình Lobby", e);
            showToast("Không thể mở màn hình Lobby!", false);
        }
    }

    private void showToast(String message, boolean isSuccess) {
        Window window = usernameField.getScene() != null ? usernameField.getScene().getWindow() : null;
        ToastUtil.showToast(window, message, isSuccess);
    }
}