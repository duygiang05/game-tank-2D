package com.tank2d.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

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

    // Xử lý Login
    @FXML
    private void handleLogin(ActionEvent event) {

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(
                Alert.AlertType.ERROR,
                "Lỗi đăng nhập",
                "Vui lòng nhập đầy đủ Username và Password!"
            );
        } else {
            showAlert(
                Alert.AlertType.INFORMATION,
                "Đăng nhập",
                "Đăng nhập thành công!"
            );
        }
    }

    // Xử lý Register
    @FXML
    private void handleRegister(ActionEvent event) {

        String username = registerUsernameField.getText().trim();
        String password = registerPasswordField.getText().trim();
        String confirmPassword = confirmPasswordField.getText().trim();

        if (username.isEmpty()
                || password.isEmpty()
                || confirmPassword.isEmpty()) {

            showAlert(
                Alert.AlertType.ERROR,
                "Lỗi đăng ký",
                "Vui lòng nhập đầy đủ thông tin!"
            );

        } else if (!password.equals(confirmPassword)) {

            showAlert(
                Alert.AlertType.ERROR,
                "Lỗi đăng ký",
                "Mật khẩu xác nhận không khớp!"
            );

        } else {

            showAlert(
                Alert.AlertType.INFORMATION,
                "Đăng ký",
                "Đăng ký thành công!"
            );
        }
    }

    // Chuyển sang Register
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

    // Hiển thị thông báo
    private void showAlert(
            Alert.AlertType type,
            String title,
            String message) {

        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}