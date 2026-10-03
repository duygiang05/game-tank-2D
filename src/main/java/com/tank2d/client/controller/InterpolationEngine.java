package com.tank2d.client.controller;

/**
 * Bộ xử lý nội suy chuyển động (Interpolation Engine) cho Client.
 * <p>
 * Áp dụng giải thuật Nội suy tuyến tính (Linear Interpolation - LERP) nhằm làm mượt
 * tọa độ di chuyển (x, y) và góc xoay nòng/thân xe tăng giữa các frame vẽ (60 FPS)
 * khi nhận các gói tin snapshot rời rạc từ máy chủ.
 */
public final class InterpolationEngine {

    private static final double FULL_ROTATION_DEGREES = 360.0;
    private static final double HALF_ROTATION_DEGREES = 180.0;

    private InterpolationEngine() {}

    /**
     * Nội suy tuyến tính giá trị vô hướng giữa 2 mốc bắt đầu và kết thúc:
     * <p>
     * {@code y = start + (end - start) * alpha}
     *
     * @param start giá trị ban đầu
     * @param end   giá trị đích
     * @param alpha hệ số làm mượt trong khoảng [0.0, 1.0]
     * @return giá trị nội suy tại thời điểm hiện tại
     */
    public static double lerp(double start, double end, double alpha) {
        return start + (end - start) * alpha;
    }

    /**
     * Nội suy góc quay 360° theo cung đường tròn ngắn nhất (Shortest Angular Path Interpolation).
     * <p>
     * <b>Thuật toán:</b>
     * <ol>
     *     <li>Tính độ lệch góc: {@code diff = (endAngle - startAngle) % 360°}.</li>
     *     <li>Nếu {@code diff &lt; -180°} thì {@code diff += 360°} (quay theo chiều ngược lại ngắn hơn).</li>
     *     <li>Nếu {@code diff &gt; 180°} thì {@code diff -= 360°}.</li>
     *     <li>Góc nội suy: {@code angle = startAngle + diff * alpha}.</li>
     * </ol>
     * Tránh hiện tượng xe bị quay đảo một vòng tròn 360 độ vô nghĩa khi góc vượt ngưỡng giao thoa 0° và 360°.
     *
     * @param startAngle góc quay xuất phát tính bằng độ
     * @param endAngle   góc quay đích tính bằng độ
     * @param alpha      hệ số làm mượt trong khoảng [0.0, 1.0]
     * @return góc quay nội suy mượt mà theo cung ngắn nhất
     */
    public static double lerpAngle(double startAngle, double endAngle, double alpha) {
        double diff = (endAngle - startAngle) % FULL_ROTATION_DEGREES;
        if (diff < -HALF_ROTATION_DEGREES) {
            diff += FULL_ROTATION_DEGREES;
        }
        if (diff > HALF_ROTATION_DEGREES) {
            diff -= FULL_ROTATION_DEGREES;
        }
        return startAngle + diff * alpha;
    }
}