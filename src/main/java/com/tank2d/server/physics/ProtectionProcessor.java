package com.tank2d.server.physics;

import com.tank2d.server.model.TankEntity;

/**
 * Xử lý đếm ngược thời gian bảo hộ bất tử (Ghost / Protection state) của xe tăng sau khi hồi sinh.
 */
public final class ProtectionProcessor {

    private ProtectionProcessor() {}

    /**
     * Cập nhật và đếm lùi thời gian bảo hộ của xe tăng.
     * Khi hết thời gian, trạng thái bảo hộ sẽ tự động hủy bỏ.
     *
     * @param tank      thực thể xe tăng cần cập nhật
     * @param deltaTime khoảng thời gian của tick vật lý (giây)
     */
    public static void update(TankEntity tank, double deltaTime) {
        if (tank == null || !tank.isProtected()) {
            return;
        }
        double remaining = tank.getProtectionTimer() - deltaTime;
        if (remaining <= 0.0) {
            tank.setProtectionTimer(0.0);
            tank.setProtected(false);
        } else {
            tank.setProtectionTimer(remaining);
        }
    }
}