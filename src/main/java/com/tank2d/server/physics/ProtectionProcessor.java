package com.tank2d.server.physics;

import com.tank2d.server.model.TankEntity;

public final class ProtectionProcessor {
    private ProtectionProcessor() {}

    public static void update(TankEntity tank, double deltaTime) {
        if (!tank.isProtected()) return;
        double remaining = tank.getProtectionTimer() - deltaTime;
        if (remaining <= 0) {
            tank.setProtectionTimer(0);
            tank.setProtected(false);
        } else {
            tank.setProtectionTimer(remaining);
        }
    }
}