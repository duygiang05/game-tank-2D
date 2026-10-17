package com.tank2d.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

/** Visual-only component showroom. No networking or game-state dependency. */
public final class ComponentPreviewApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(
            "/com/tank2d/client/view/component_preview.fxml"));
        @SuppressWarnings("unchecked")
        ListView<String> list = (ListView<String>) root.lookup(".game-ui-list");
        if (list != null) {
            list.getItems().addAll(
                "Room Alpha      2/4 players",
                "Room Bravo      4/4 players",
                "Room Charlie    1/4 players");
        }
        stage.setScene(new Scene(root, 900, 650));
        stage.setTitle("Tank2D — Sprint 2 Component Preview");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
