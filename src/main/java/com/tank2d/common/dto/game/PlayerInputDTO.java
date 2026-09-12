package com.tank2d.common.dto.game;

public class PlayerInputDTO {
    private String move;   // "FORWARD" | "BACKWARD" | "NONE"
    private String rotate; // "LEFT" | "RIGHT" | "NONE"

    public PlayerInputDTO() {}

    public PlayerInputDTO(String move, String rotate) {
        this.move = move;
        this.rotate = rotate;
    }

    public String getMove() { return move; }
    public String getRotate() { return rotate; }
}