package com.tank2d.server.input;

import com.google.gson.Gson;
import com.tank2d.common.dto.game.PlayerInputDTO;
import com.tank2d.common.dto.game.PlayerShootRequestDTO;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.model.BulletEntity;
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

    /** jsonData: payload PLAYER_SHOOT_REQ, có thể null/"{}" -> mặc định bắn đạn NORMAL. */
    public boolean handleShootRequest(GameLoop loop, TankEntity tank, String jsonData) {
        if (loop == null || tank == null) return false;

        BulletEntity.BulletType requestedType = BulletEntity.BulletType.NORMAL;
        if (jsonData != null) {
            try {
                PlayerShootRequestDTO req = gson.fromJson(jsonData, PlayerShootRequestDTO.class);
                if (req != null && "ROCKET".equalsIgnoreCase(req.getBulletType())) {
                    requestedType = BulletEntity.BulletType.ROCKET;
                }
            } catch (Exception ignored) {
                // payload rỗng hoặc sai định dạng -> giữ mặc định NORMAL
            }
        }

        return loop.handleShootRequest(tank, requestedType); // chỉ forward, KHÔNG tự tính toán gì cả
    }

    private TankEntity.MoveState parseMoveState(String raw) {
        if (raw == null) return TankEntity.MoveState.NONE;
        try {
            return TankEntity.MoveState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return TankEntity.MoveState.NONE;
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