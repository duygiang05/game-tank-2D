package com.tank2d.server.map;

public class GameMap {
    private final int width;
    private final int height;
    private final int tileSize;
    private final int[][] tiles;          // giá trị hiện tại (có thể đổi khi tường gạch vỡ)
    private final int[][] brickHitsLeft;   // số phát đạn còn chịu được, chỉ có ý nghĩa với ô BRICK_WALL

    public GameMap(int width, int height, int tileSize, int[][] tiles, int brickMaxHits) {
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.tiles = tiles;
        this.brickHitsLeft = new int[height][width];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                if (tiles[r][c] == TileType.BRICK_WALL.getCode()) {
                    brickHitsLeft[r][c] = brickMaxHits;
                }
            }
        }
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getTileSize() { return tileSize; }

    private int toCol(double worldX) { return (int) Math.floor(worldX / tileSize); }
    private int toRow(double worldY) { return (int) Math.floor(worldY / tileSize); }

    public boolean isOutOfBounds(double worldX, double worldY) {
        int col = toCol(worldX);
        int row = toRow(worldY);
        return col < 0 || col >= width || row < 0 || row >= height;
    }

    public TileType getTileAt(double worldX, double worldY) {
        if (isOutOfBounds(worldX, worldY)) return TileType.STONE_WALL; // ngoài map coi như tường chặn
        return TileType.fromCode(tiles[toRow(worldY)][toCol(worldX)]);
    }

    public boolean isSolid(double worldX, double worldY) {
        TileType t = getTileAt(worldX, worldY);
        return t == TileType.STONE_WALL || t == TileType.BRICK_WALL;
    }

    public boolean hitBrickWall(double worldX, double worldY) {
        if (isOutOfBounds(worldX, worldY)) return false;
        int row = toRow(worldY), col = toCol(worldX);
        if (tiles[row][col] != TileType.BRICK_WALL.getCode()) return false;

        brickHitsLeft[row][col]--;
        if (brickHitsLeft[row][col] <= 0) {
            tiles[row][col] = TileType.EMPTY.getCode(); // tường vỡ -> thành đất trống
            return true;
        }
        return false;
    }
}