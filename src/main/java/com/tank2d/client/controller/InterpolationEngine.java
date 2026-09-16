package com.tank2d.client.controller;

/**
 * Xử lý nội suy tuyến tính (LERP) giúp mượt di chuyển và xoay nòng/thân xe ở 60 FPS
 */
public class InterpolationEngine {

    public static double lerp(double start, double end, double alpha) {
        return start + (end - start) * alpha;
    }

    public static double lerpAngle(double startAngle, double endAngle, double alpha) {
        double diff = (endAngle - startAngle) % 360.0;
        if (diff < -180.0) diff += 360.0;
        if (diff > 180.0) diff -= 360.0;
        return startAngle + diff * alpha;
    }
}