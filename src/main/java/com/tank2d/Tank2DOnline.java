package com.tank2d;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.stage.Stage;


/**
 * Điểm khởi chạy chính của ứng dụng giao diện người dùng JavaFX (Client).
 * Quản lý khởi tạo màn hình, kích thước cửa sổ cố định và dọn dẹp kết nối khi thoát.
 */
public class Tank2DOnline extends Application {

    private static final double WINDOW_WIDTH = com.tank2d.common.config.ConfigLoader.getWindowWidth();
    private static final double WINDOW_HEIGHT = com.tank2d.common.config.ConfigLoader.getWindowHeight();

    /**
     * Khởi tạo và thiết lập Stage chính với màn hình đăng nhập.
     *
     * @param stage cửa sổ chính JavaFX
     * @throws Exception khi gặp sự cố tải file FXML
     */
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(
                getClass().getResource("/com/tank2d/client/view/login.fxml")
        );

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

        stage.setTitle("Tank 2D Online");
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setResizable(false);

        stage.setOnCloseRequest(event -> {
            try {
                if (com.tank2d.client.ClientSession.getInstance().getClientSocket() != null) {
                    com.tank2d.client.ClientSession.getInstance().getClientSocket().close();
                }
            } catch (Exception ignored) {}
            javafx.application.Platform.exit();
            System.exit(0);
        });

        stage.setScene(scene);
        stage.show();
    }

    /**
     * Giải phóng tài nguyên và đóng phiên kết nối mạng khi ứng dụng dừng.
     *
     * @throws Exception khi xảy ra lỗi trong quá trình dừng ứng dụng
     */
    @Override
    public void stop() throws Exception {
        try {
            if (com.tank2d.client.ClientSession.getInstance().getClientSocket() != null) {
                com.tank2d.client.ClientSession.getInstance().getClientSocket().close();
            }
        } catch (Exception ignored) {}
        super.stop();
        System.exit(0);
    }

    /**
     * Điểm nhập chính của chương trình.
     *
     * @param args tham số dòng lệnh
     */
    public static void main(String[] args) {
        launch(args);
    }
}
