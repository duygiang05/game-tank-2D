package com.tank2d.server.physics;

import com.tank2d.server.model.BulletEntity;

public final class BulletMovementProcessor {

    private BulletMovementProcessor() {}

    public static void update(BulletEntity bullet, double deltaTime) {
        bullet.setX(bullet.getX() + bullet.getVx() * deltaTime);
        bullet.setY(bullet.getY() + bullet.getVy() * deltaTime);
    }
}