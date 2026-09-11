package com.tank2d.server.input;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.game.PlayerInputDTO;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.model.TankEntity;

public class PlayerInputHandler {

    private static final Gson gson = new Gson();

    public void handlePlayerInput(TankEntity tank, String jsonData) {
        if (tank == null || jsonData == null) return;

        PlayerInputDTO input = gson.fromJson(jsonData, PlayerInputDTO.class);
        if (input == null) return;

        tank.setMoveState(parseMoveState(input.getMove()));
        tank.setRotateState(parseRotateState(input.getRotate()));
    }

    public boolean handleShootRequest(GameLoop loop, TankEntity tank) {
        if (loop == null || tank == null) return false;

        long cooldownMs = ConfigLoader.getFireCooldownMs();
        long now = System.currentTimeMillis();

        if (!tank.canShoot(cooldownMs, now)) {
            return false; // đang khóa spam đạn
        }

        double bulletSpeed = ConfigLoader.getBulletSpeedPerSecond();

        double radians = Math.toRadians(tank.getAngle());
        double vx = bulletSpeed * Math.cos(radians);
        double vy = bulletSpeed * Math.sin(radians);

        loop.spawnBullet(tank.getId(), tank.getX(), tank.getY(), vx, vy);
        tank.registerShot(now);
        return true;
    }

    private TankEntity.MoveState parseMoveState(String raw) {
        if (raw == null) return TankEntity.MoveState.NONE;
        try {
            return TankEntity.MoveState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return TankEntity.MoveState.NONE; // dữ liệu client gửi sai định dạng -> an toàn về NONE
        }
    }

    private TankEntity.RotateState parseRotateState(String raw) {
        if (raw == null) return TankEntity.RotateState.NONE;
        try {
            return TankEntity.RotateState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return TankEntity.RotateState.NONE;
        }
    }
}