package com.tank2d.client.view;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tank2d.common.model.TankDTO;

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

    // Khởi tạo kích thước Canvas cố định 800x600
    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 600;

    private Canvas canvas;
    private GraphicsContext gc;
    private final Gson gson = new Gson();

    // Biến đo đạc chỉ số FPS
    private long lastFpsCheck = System.nanoTime();
    private int frameCounter = 0;
    private int currentFps = 0;

    // Chuỗi Mock JSON mô phỏng dữ liệu GAME_SNAPSHOT từ Hoàng
    private final String mockJsonData = "["
            + "{\"id\":\"tank_1\",\"x\":200.0,\"y\":150.0,\"angle\":45.0},"
            + "{\"id\":\"tank_2\",\"x\":500.0,\"y\":350.0,\"angle\":180.0},"
            + "{\"id\":\"tank_3\",\"x\":650.0,\"y\":100.0,\"angle\":270.0}"
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
                // Tính toán FPS
                updateFpsCounter(now);

                // 1. Clear nền màu đen chuẩn 800x600
                gc.setFill(Color.BLACK);
                gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

                // 2. Bóc tách chuỗi Mock JSON
                Type listType = new TypeToken<List<TankDTO>>() {}.getType();
                List<TankDTO> tanks = gson.fromJson(mockJsonData, listType);

                // 3. Render danh sách xe tăng
                if (tanks != null) {
                    for (TankDTO tank : tanks) {
                        drawTank(tank);
                    }
                }

                // 4. Hiển thị FPS ở góc màn hình
                renderFpsInfo();
            }
        }.start();
    }

    /**
     * Quy trình vẽ xe tăng dùng GraphicsContext theo đúng yêu cầu:
     * gc.save() -> gc.translate(x, y) -> gc.rotate(angle) -> draw -> gc.restore()
     */
    private void drawTank(TankDTO tank) {
    gc.save();

    gc.translate(tank.getX(), tank.getY());
    gc.rotate(tank.getAngle());

    double tankSize = tank.getSize();
    double halfSize = tankSize / 2.0;

    // 1. Thân xe (Kích thước tankSize x tankSize)
    gc.setFill(Color.FORESTGREEN);
    gc.fillRect(-halfSize, -halfSize, tankSize, tankSize);

    // 2. Xích xe (Tự động co giãn theo tankSize)
    double treadWidth = tankSize * 0.14; // Bề rộng xích
    gc.setFill(Color.DARKSLATEGRAY);
    gc.fillRect(-halfSize - treadWidth, -halfSize, treadWidth, tankSize); // Xích trái
    gc.fillRect(halfSize, -halfSize, treadWidth, tankSize);               // Xích phải

    // 3. Nòng súng
    double cannonWidth = tankSize * 0.16;
    double cannonLength = tankSize * 0.55;
    gc.setFill(Color.ORANGE);
    gc.fillRect(-cannonWidth / 2, -halfSize - cannonLength + (tankSize * 0.2), cannonWidth, cannonLength);

    // 4. Tháp pháo
    double turretSize = tankSize * 0.5;
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