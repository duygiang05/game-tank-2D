package com.tank2d.client.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Explosion {
    private double x, y;
    private double radius = 4;
    private final double maxRadius = 26;
    private double alpha = 1.0;
    private boolean active = true;

    public Explosion(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void update() {
        radius += 1.8;
        alpha -= 0.06;
        if (alpha <= 0 || radius >= maxRadius) {
            active = false;
        }
    }

    public void render(GraphicsContext gc) {
        if (!active) return;
        gc.save();
        gc.setGlobalAlpha(Math.max(0, alpha));

        // Vòng ngoài màu cam
        gc.setFill(Color.ORANGE);
        gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);

        // Lõi trong màu đỏ
        gc.setFill(Color.RED);
        gc.fillOval(x - (radius * 0.5), y - (radius * 0.5), radius, radius);

        gc.restore();
    }

    public boolean isActive() { return active; }
}