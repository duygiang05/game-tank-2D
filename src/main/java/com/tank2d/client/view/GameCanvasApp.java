package com.tank2d.client.view;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tank2d.common.dto.game.TankSnapshotDTO;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.lang.reflect.Type;
import java.util.List;

public class GameCanvasApp extends Application {

    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 600;
    
    // Kích thước xe tăng mặc định theo quy định dự án
    public static final double TANK_SIZE = 36.0;

    private Canvas canvas;
    private GraphicsContext gc;
    private final Gson gson = new Gson();

    private long lastFpsCheck = System.nanoTime();
    private int frameCounter = 0;
    private int currentFps = 0;

    // Chuỗi Mock JSON cập nhật theo đúng kiểu dữ liệu TankSnapshotDTO (id là int, có hp và isAlive)
    private final String mockJsonData = "["
            + "{\"id\":1,\"x\":200.0,\"y\":150.0,\"angle\":45.0,\"hp\":100,\"isAlive\":true},"
            + "{\"id\":2,\"x\":500.0,\"y\":350.0,\"angle\":180.0,\"hp\":50,\"isAlive\":true},"
            + "{\"id\":3,\"x\":650.0,\"y\":100.0,\"angle\":270.0,\"hp\":0,\"isAlive\":false}"
            + "]";

    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, CANVAS_WIDTH, CANVAS_HEIGHT);

        primaryStage.setTitle("Tank 2D - Canvas Render Engine (Tùng)");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        startRenderLoop();
    }

    private void startRenderLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateFpsCounter(now);

                // 1. Clear màn hình
                gc.setFill(Color.BLACK);
                gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

                // 2. Parse dữ liệu snapshot bằng TankSnapshotDTO
                Type listType = new TypeToken<List<TankSnapshotDTO>>() {}.getType();
                List<TankSnapshotDTO> tanks = gson.fromJson(mockJsonData, listType);

                // 3. Render các xe tăng còn sống
                if (tanks != null) {
                    for (TankSnapshotDTO tank : tanks) {
                        if (tank.isAlive()) {
                            drawTank(tank);
                        }
                    }
                }

                // 4. Hiển thị thông số FPS
                renderFpsInfo();
            }
        }.start();
    }

    /**
     * Vẽ xe tăng sử dụng TankSnapshotDTO theo đúng quy tắc save/restore và căn giữa tâm
     */
    private void drawTank(TankSnapshotDTO tank) {
        gc.save();

        // Dịch chuyển gốc tọa độ về tâm xe và xoay góc
        gc.translate(tank.getX(), tank.getY());
        gc.rotate(tank.getAngle());

        double halfSize = TANK_SIZE / 2.0;

        // 1. Vẽ Thân xe
        gc.setFill(Color.FORESTGREEN);
        gc.fillRect(-halfSize, -halfSize, TANK_SIZE, TANK_SIZE);

        // 2. Vẽ Xích xe 2 bên
        double treadWidth = TANK_SIZE * 0.14;
        gc.setFill(Color.DARKSLATEGRAY);
        gc.fillRect(-halfSize - treadWidth, -halfSize, treadWidth, TANK_SIZE);
        gc.fillRect(halfSize, -halfSize, treadWidth, TANK_SIZE);

        // 3. Vẽ Nòng súng
        double cannonWidth = TANK_SIZE * 0.16;
        double cannonLength = TANK_SIZE * 0.55;
        gc.setFill(Color.ORANGE);
        gc.fillRect(-cannonWidth / 2, -halfSize - cannonLength + (TANK_SIZE * 0.2), cannonWidth, cannonLength);

        // 4. Vẽ Tháp pháo tròn
        double turretSize = TANK_SIZE * 0.5;
        gc.setFill(Color.LIMEGREEN);
        gc.fillOval(-turretSize / 2, -turretSize / 2, turretSize, turretSize);

        gc.restore();
    }

    private void updateFpsCounter(long now) {
        frameCounter++;
        if (now - lastFpsCheck >= 1_000_000_000L) {
            currentFps = frameCounter;
            frameCounter = 0;
            lastFpsCheck = now;
        }
    }

    private void renderFpsInfo() {
        gc.setFill(Color.YELLOW);
        gc.fillText("FPS: " + currentFps, 12, 24);
    }

    public static void main(String[] args) {
        launch(args);
    }
}