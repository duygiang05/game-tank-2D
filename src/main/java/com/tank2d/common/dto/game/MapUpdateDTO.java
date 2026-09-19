package com.tank2d.common.dto.game;

public class MapUpdateDTO {
    private int row;
    private int col;
    private int newTileCode;

    public MapUpdateDTO(int row, int col, int newTileCode) {
        this.row = row; this.col = col; this.newTileCode = newTileCode;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public int getNewTileCode() { return newTileCode; }
}