package com.tank2d.server.input;

import com.google.gson.Gson;
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
        return loop.handleShootRequest(tank); // chỉ forward, KHÔNG tự tính toán gì cả
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