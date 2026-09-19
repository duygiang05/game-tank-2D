package com.tank2d.server.map;

public class GameMap {
    private final int width;
    private final int height;
    private final int tileSize;
    private final int[][] tiles;
    private final int[][] brickHitsLeft;

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

    public int worldToRow(double worldY) { return (int) Math.floor(worldY / tileSize); }
    public int worldToCol(double worldX) { return (int) Math.floor(worldX / tileSize); }
    public double tileCenterX(int col) { return col * tileSize + tileSize / 2.0; }
    public double tileCenterY(int row) { return row * tileSize + tileSize / 2.0; }

    public boolean isOutOfBounds(double worldX, double worldY) {
        int col = worldToCol(worldX), row = worldToRow(worldY);
        return col < 0 || col >= width || row < 0 || row >= height;
    }

    public TileType getTileAt(double worldX, double worldY) {
        if (isOutOfBounds(worldX, worldY)) return TileType.STONE_WALL;
        return TileType.fromCode(tiles[worldToRow(worldY)][worldToCol(worldX)]);
    }

    public boolean isSolid(double worldX, double worldY) {
        TileType t = getTileAt(worldX, worldY);
        return t == TileType.STONE_WALL || t == TileType.BRICK_WALL;
    }

    /** Kết quả 1 lần bắn trúng tường gạch — cho biết có ô nào thật sự vỡ (đổi thành EMPTY) không, để phát MAP_UPDATE. */
    public static class WallHitResult {
        public final boolean hit;
        public final boolean broken;
        public final int row;
        public final int col;
        private WallHitResult(boolean hit, boolean broken, int row, int col) {
            this.hit = hit; this.broken = broken; this.row = row; this.col = col;
        }
        static WallHitResult none() { return new WallHitResult(false, false, -1, -1); }
        static WallHitResult damaged(int row, int col) { return new WallHitResult(true, false, row, col); }
        static WallHitResult broken(int row, int col) { return new WallHitResult(true, true, row, col); }
    }

    /** @param instaBreak true nếu là đạn tên lửa (vỡ ngay bất kể còn bao nhiêu máu tường). */
    public WallHitResult hitBrickWall(double worldX, double worldY, boolean instaBreak) {
        if (isOutOfBounds(worldX, worldY)) return WallHitResult.none();
        int row = worldToRow(worldY), col = worldToCol(worldX);
        if (tiles[row][col] != TileType.BRICK_WALL.getCode()) return WallHitResult.none();

        if (instaBreak) {
            brickHitsLeft[row][col] = 0;
        } else {
            brickHitsLeft[row][col]--;
        }

        if (brickHitsLeft[row][col] <= 0) {
            tiles[row][col] = TileType.EMPTY.getCode();
            return WallHitResult.broken(row, col);
        }
        return WallHitResult.damaged(row, col);
    }
}