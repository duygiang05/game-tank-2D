package com.tank2d.client.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
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

                Popup popup = new Popup();
                popup.setAutoFix(true);

                Label label = new Label(message);
                label.setWrapText(true);
                label.setMaxWidth(400);

                String bgColor = isSuccess ? "rgba(22, 101, 52, 0.95)" : "rgba(185, 28, 28, 0.95)";
                String borderColor = isSuccess ? "#4ade80" : "#f87171";

                label.setStyle(
                        "-fx-background-color: " + bgColor + ";"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 13.5px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 12px 20px;"
                        + "-fx-background-radius: 8px;"
                        + "-fx-border-color: " + borderColor + ";"
                        + "-fx-border-width: 1.5px;"
                        + "-fx-border-radius: 8px;"
                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 10, 0, 0, 4);"
                );

                popup.getContent().add(label);
                popup.show(targetWindow);

                popup.setX(targetWindow.getX() + (targetWindow.getWidth() - label.getWidth()) / 2.0);
                popup.setY(targetWindow.getY() + 65.0);

                double displayTime = Math.max(1.0, durationSeconds - 0.5);
                PauseTransition visiblePause = new PauseTransition(Duration.seconds(displayTime));
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

    /**
     * Hiển thị thông báo Toast gắn liền với một Node trên Scene.
     *
     * @param node thành phần UI chứa Scene hiển thị
     * @param message nội dung thông báo
     * @param isSuccess true nếu thông báo thành công, false nếu là thông báo lỗi
     */
    public static void showToast(Node node, String message, boolean isSuccess) {
        if (node != null && node.getScene() != null) {
            showToast(node.getScene().getWindow(), message, isSuccess);
        } else {
            showToast((Window) null, message, isSuccess);
        }
    }
}
