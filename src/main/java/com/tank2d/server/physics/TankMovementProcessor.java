package com.tank2d.server.physics;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.model.TankEntity;

public final class TankMovementProcessor {

    public static void update(TankEntity tank, double deltaTime) {
        if (!tank.isAlive()) return;

        long now = System.currentTimeMillis();
        boolean nitroActive = tank.getNitroActiveUntilMillis() > now;
        double nitroMultiplier = nitroActive ? ConfigLoader.getNitroSpeedMultiplier() : 1.0;

        double angle = tank.getAngle();
        double rotSpeed = tank.getRotationSpeed() * nitroMultiplier;

        if (tank.getRotateState() == TankEntity.RotateState.LEFT) {
            angle -= rotSpeed * deltaTime;
        } else if (tank.getRotateState() == TankEntity.RotateState.RIGHT) {
            angle += rotSpeed * deltaTime;
        }
        angle = normalizeAngle(angle);
        tank.setAngle(angle);

        if (tank.getMoveState() != TankEntity.MoveState.NONE) {
            double speed = tank.getSpeed() * nitroMultiplier;
            double radians = Math.toRadians(angle);
            double vx = speed * Math.cos(radians) * deltaTime;
            double vy = speed * Math.sin(radians) * deltaTime;

            if (tank.getMoveState() == TankEntity.MoveState.FORWARD) {
                tank.setX(tank.getX() + vx);
                tank.setY(tank.getY() + vy);
            } else if (tank.getMoveState() == TankEntity.MoveState.BACKWARD) {
                tank.setX(tank.getX() - vx);
                tank.setY(tank.getY() - vy);
            }
        }
    }

    private static double normalizeAngle(double angle) {
        angle %= 360.0;
        if (angle < 0) angle += 360.0;
        return angle;
    }
}