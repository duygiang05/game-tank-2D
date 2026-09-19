package com.tank2d.common.dto.game;

public class TankPlayerDTO {

    private int tankId;
    private String username;

    public TankPlayerDTO() {
    }

    public TankPlayerDTO(int tankId, String username) {
        this.tankId = tankId;
        this.username = username;
    }

    public int getTankId() {
        return tankId;
    }

    public String getUsername() {
        return username;
    }
}