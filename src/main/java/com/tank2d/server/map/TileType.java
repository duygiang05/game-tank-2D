package com.tank2d.server.map;

public enum TileType {
    EMPTY(0), STONE_WALL(1), BRICK_WALL(2), BUSH(3);

    private final int code;
    TileType(int code) { this.code = code; }
    public int getCode() { return code; }

    public static TileType fromCode(int code) {
        for (TileType t : values()) {
            if (t.code == code) return t;
        }
        return EMPTY; 
    }
}