package com.tank2d.client.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.logging.Logger;

/**
 * Tiện ích hiển thị thông báo Toast thân thiện (5 giây) ở góc trên màn hình UI.
 */
public class ToastUtil {

    private static final Logger LOGGER = Logger.getLogger(ToastUtil.class.getName());

    /**
     * Hiển thị thông báo Toast với thời gian mặc định 5 giây.
     *
     * @param window cửa sổ hiển thị thông báo
     * @param message nội dung thông báo
     * @param isSuccess true nếu thông báo thành công, false nếu là thông báo lỗi
     */
    public static void showToast(Window window, String message, boolean isSuccess) {
        showToast(window, message, isSuccess, 5.0);
    }

    /**
     * Hiển thị thông báo Toast với thời gian tùy chỉnh.
     *
     * @param window cửa sổ hiển thị thông báo
     * @param message nội dung thông báo
     * @param isSuccess true nếu thông báo thành công, false nếu là thông báo lỗi
     * @param durationSeconds thời gian hiển thị tính bằng giây
     */
    public static void showToast(Window window, String message, boolean isSuccess, double durationSeconds) {
        Platform.runLater(() -> {
            try {
                Window targetWindow = window;
                if (targetWindow == null || !targetWindow.isShowing()) {
                    for (Window w : Window.getWindows()) {
                        if (w.isShowing() && w instanceof Stage) {
                            targetWindow = w;
                            break;
                        }
                    }
                }

                if (targetWindow == null || !targetWindow.isShowing()) {
                    LOGGER.warning("[ToastUtil] " + message);
                    return;
                }

                Parent root = targetWindow.getScene().getRoot();
                if (root instanceof javafx.scene.layout.Pane parentPane) {
                    parentPane.getChildren().removeIf(node -> "in-scene-toast".equals(node.getId()));

                    HBox toastCard = new HBox(6);
                    toastCard.setId("in-scene-toast");
                    toastCard.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    toastCard.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
                    toastCard.setPrefHeight(24);
                    toastCard.setMouseTransparent(true);

                    String icon = isSuccess ? "✔ " : "✖ ";
                    Label label = new Label(icon + message);
                    label.setWrapText(false);

                    String textColor = isSuccess ? "#4ade80" : "#f87171";
                    String bgColor = isSuccess ? "rgba(34, 197, 94, 0.04)" : "rgba(239, 68, 68, 0.04)";
                    String borderColor = isSuccess ? "rgba(74, 222, 128, 0.30)" : "rgba(248, 113, 113, 0.30)";

                    toastCard.setStyle(
                            "-fx-background-color: " + bgColor + ";"
                            + "-fx-padding: 3px 10px;"
                            + "-fx-background-radius: 6px;"
                            + "-fx-border-color: " + borderColor + ";"
                            + "-fx-border-width: 1px;"
                            + "-fx-border-radius: 6px;"
                    );
                    label.setStyle("-fx-text-fill: " + textColor + "; -fx-font-family: 'Kenney Future', 'Segoe UI', sans-serif; -fx-font-size: 9.5px; -fx-font-weight: bold;");
                    toastCard.getChildren().add(label);

                    if (parentPane instanceof javafx.scene.layout.StackPane stackPane) {
                        javafx.scene.layout.StackPane.setAlignment(toastCard, javafx.geometry.Pos.TOP_RIGHT);
                        javafx.scene.layout.StackPane.setMargin(toastCard, new javafx.geometry.Insets(12, 14, 0, 0));
                        stackPane.getChildren().add(toastCard);
                        toastCard.toFront();
                    } else {
                        toastCard.setLayoutX(Math.max(20, parentPane.getWidth() - 320));
                        toastCard.setLayoutY(12);
                        parentPane.getChildren().add(toastCard);
                        toastCard.toFront();
                    }

                    toastCard.setOpacity(0.0);
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(250), toastCard);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);

                    PauseTransition stay = new PauseTransition(Duration.seconds(durationSeconds));
                    stay.setOnFinished(e -> {
                        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), toastCard);
                        fadeOut.setFromValue(1.0);
                        fadeOut.setToValue(0.0);
                        fadeOut.setOnFinished(f -> parentPane.getChildren().remove(toastCard));
                        fadeOut.play();
                    });

                    fadeIn.setOnFinished(e -> stay.play());
                    fadeIn.play();
                }

            } catch (Exception ignored) {}
        });
    }

    /**
     * Hiển thị thông báo Toast gắn liền với một Node trên Scene.
     *
     * @param node thành phần UI chứa Scene hiển thị
     * @param message nội dung thông báo
     * @param isSuccess true nếu thông báo thành công, false nếu là thông báo lỗi
     */
    public static void showToast(Node node, String message, boolean isSuccess) {
        showToast(node, message, isSuccess, 5.0);
    }

    public static void showToast(Node node, String message, boolean isSuccess, double durationSeconds) {
        if (node != null && node.getScene() != null) {
            showToast(node.getScene().getWindow(), message, isSuccess, durationSeconds);
        } else {
            showToast((Window) null, message, isSuccess, durationSeconds);
        }
    }

    public static void showInfo(Node node, String message) {
        showToast(node, message, true, 4.0);
    }

    public static void showSuccess(Node node, String message) {
        showToast(node, message, true, 4.0);
    }

    public static void showWarning(Node node, String message) {
        showToast(node, message, false, 4.0);
    }

    public static void showError(Node node, String message) {
        showToast(node, message, false, 5.0);
    }
}
