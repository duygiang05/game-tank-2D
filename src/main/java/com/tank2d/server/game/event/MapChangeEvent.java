package com.tank2d.server.game.event;

public class MapChangeEvent {
    private final int row;
    private final int col;
    private final int newTileCode;

    public MapChangeEvent(int row, int col, int newTileCode) {
        this.row = row; this.col = col; this.newTileCode = newTileCode;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public int getNewTileCode() { return newTileCode; }
}