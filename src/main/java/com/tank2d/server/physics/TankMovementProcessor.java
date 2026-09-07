package com.tank2d.server.physics;

import com.tank2d.server.model.TankEntity;

public final class TankMovementProcessor {

    private TankMovementProcessor() {} // utility class, không cần khởi tạo

    public static void update(TankEntity tank, double deltaTime) {
        double angle = tank.getAngle();

        if (tank.getRotateState() == TankEntity.RotateState.LEFT) {
            angle -= tank.getRotationSpeed() * deltaTime;
        } else if (tank.getRotateState() == TankEntity.RotateState.RIGHT) {
            angle += tank.getRotationSpeed() * deltaTime;
        }
        angle = normalizeAngle(angle);
        tank.setAngle(angle);

        if (tank.getMoveState() != TankEntity.MoveState.NONE) {
            double radians = Math.toRadians(angle);
            double vx = tank.getSpeed() * Math.cos(radians) * deltaTime;
            double vy = tank.getSpeed() * Math.sin(radians) * deltaTime;

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