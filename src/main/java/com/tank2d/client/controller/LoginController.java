package com.tank2d.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;
    @FXML
private PasswordField confirmPasswordField;

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

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
    @FXML
private void handleRegister(ActionEvent event) {
    String username = usernameField.getText();
    String password = passwordField.getText();
    String confirmPassword = confirmPasswordField.getText();

    if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
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

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}