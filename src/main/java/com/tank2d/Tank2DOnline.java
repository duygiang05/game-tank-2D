package com.tank2d;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.stage.Stage;


public class Tank2DOnline extends Application {

    private static final double WINDOW_WIDTH = 800;
    private static final double WINDOW_HEIGHT = 600;

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(
                getClass().getResource("/com/tank2d/client/view/login.fxml")
        );

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

        stage.setTitle("Tank 2D Online");

        // Kích thước cố định
        stage.setWidth(WINDOW_WIDTH);
        stage.setHeight(WINDOW_HEIGHT);
        stage.setMinWidth(WINDOW_WIDTH);
        stage.setMaxWidth(WINDOW_WIDTH);
        stage.setMinHeight(WINDOW_HEIGHT);
        stage.setMaxHeight(WINDOW_HEIGHT);

        // Không cho phép kéo thay đổi kích thước
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

    public static void main(String[] args) {
        launch(args);
    }
}
