package com.tank2d.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Standalone visual-only verification of Kenney resources. */
public final class AssetPreviewApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/com/tank2d/client/view/asset_preview.fxml"));
        stage.setTitle("Tank 2D Online - Kenney Asset Preview");
        stage.setScene(new Scene(root, 840, 580));
        stage.show();
    }
    public static void main(String[] args) {
        launch(args);
    }
}
