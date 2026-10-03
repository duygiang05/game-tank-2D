package com.tank2d.server.map;

/**
 * Bản đồ trận đấu 2D dạng lưới ô vuông (Grid Tile Map).
 * <p>
 * Lưu trữ thông tin ma trận ô (gạch, đá, bụi cỏ, ô trống), độ bền tường gạch và các nhãn cụm bụi cỏ.
 */
public class GameMap {

    public static final int NO_CLUSTER = -1;

    private final int width;
    private final int height;
    private final int tileSize;
    private final int[][] tiles;
    private final int[][] brickHitsLeft;
    private final int[][] bushClusterIds;
    private int bushClusterCount = 0;

    /**
     * Khởi tạo bản đồ trận đấu.
     *
     * @param width        chiều rộng bản đồ tính theo số ô (cột)
     * @param height       chiều cao bản đồ tính theo số ô (hàng)
     * @param tileSize     kích thước mỗi ô vuông tính bằng pixel
     * @param tiles        ma trận số nguyên biểu diễn các loại ô
     * @param brickMaxHits số phát bắn cần thiết để phá vỡ hoàn toàn một ô tường gạch
     */
    public GameMap(int width, int height, int tileSize, int[][] tiles, int brickMaxHits) {
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.tiles = tiles;
        this.brickHitsLeft = new int[height][width];
        this.bushClusterIds = new int[height][width];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                bushClusterIds[r][c] = NO_CLUSTER;
                if (tiles[r][c] == TileType.BRICK_WALL.getCode()) {
                    brickHitsLeft[r][c] = brickMaxHits;
                }
            }
        }
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getTileSize() { return tileSize; }

    /**
     * Chuyển đổi tọa độ thế giới Y sang chỉ số hàng (row) trên lưới.
     */
    public int worldToRow(double worldY) {
        return (int) Math.floor(worldY / tileSize);
    }

    /**
     * Chuyển đổi tọa độ thế giới X sang chỉ số cột (col) trên lưới.
     */
    public int worldToCol(double worldX) {
        return (int) Math.floor(worldX / tileSize);
    }

    /**
     * Lấy tọa độ tâm X của ô tại cột chỉ định.
     */
    public double tileCenterX(int col) {
        return col * tileSize + tileSize / 2.0;
    }

    /**
     * Lấy tọa độ tâm Y của ô tại hàng chỉ định.
     */
    public double tileCenterY(int row) {
        return row * tileSize + tileSize / 2.0;
    }

    /**
     * Kiểm tra xem chỉ số ô (row, col) có nằm trong biên bản đồ hay không.
     */
    public boolean isInside(int row, int col) {
        return row >= 0 && row < height && col >= 0 && col < width;
    }

    /**
     * Kiểm tra xem tọa độ thực (worldX, worldY) có nằm ngoài phạm vi bản đồ hay không.
     */
    public boolean isOutOfBounds(double worldX, double worldY) {
        return !isInside(worldToRow(worldY), worldToCol(worldX));
    }

    /**
     * Lấy mã số ô tại vị trí (row, col). Ngoài biên được coi là đá kiên cố.
     */
    public int getTileCode(int row, int col) {
        if (!isInside(row, col)) {
            return TileType.STONE_WALL.getCode();
        }
        return tiles[row][col];
    }

    /**
     * Lấy loại địa hình {@link TileType} tại tọa độ thực.
     */
    public TileType getTileAt(double worldX, double worldY) {
        if (isOutOfBounds(worldX, worldY)) {
            return TileType.STONE_WALL;
        }
        return TileType.fromCode(tiles[worldToRow(worldY)][worldToCol(worldX)]);
    }

    /**
     * Kiểm tra xem tại vị trí tọa độ thực có phải là khối chắn kiên cố (đá hoặc gạch) hay không.
     */
    public boolean isSolid(double worldX, double worldY) {
        TileType t = getTileAt(worldX, worldY);
        return t == TileType.STONE_WALL || t == TileType.BRICK_WALL;
    }

    /**
     * Lấy mã cụm bụi cỏ tại ô (row, col).
     */
    public int getBushClusterId(int row, int col) {
        if (!isInside(row, col)) {
            return NO_CLUSTER;
        }
        return bushClusterIds[row][col];
    }

    /**
     * Gán mã cụm bụi cỏ cho ô (row, col).
     */
    public void setBushClusterId(int row, int col, int clusterId) {
        if (isInside(row, col)) {
            bushClusterIds[row][col] = clusterId;
        }
    }

    /**
     * Lấy mã cụm bụi cỏ tại tọa độ thực.
     */
    public int getBushClusterIdAt(double worldX, double worldY) {
        return getBushClusterId(worldToRow(worldY), worldToCol(worldX));
    }

    public int getBushClusterCount() {
        return bushClusterCount;
    }

    public void setBushClusterCount(int count) {
        this.bushClusterCount = count;
    }

    /**
     * Kết quả xử lý khi đạn bắn trúng tường gạch.
     */
    public static class WallHitResult {
        public final boolean hit;
        public final boolean broken;
        public final int row;
        public final int col;

        private WallHitResult(boolean hit, boolean broken, int row, int col) {
            this.hit = hit;
            this.broken = broken;
            this.row = row;
            this.col = col;
        }

        static WallHitResult none() {
            return new WallHitResult(false, false, -1, -1);
        }

        static WallHitResult damaged(int row, int col) {
            return new WallHitResult(true, false, row, col);
        }

        static WallHitResult broken(int row, int col) {
            return new WallHitResult(true, true, row, col);
        }
    }

    /**
     * Xử lý tác động sát thương đạn lên tường gạch tại tọa độ chỉ định.
     *
     * @param worldX     tọa độ X điểm va chạm
     * @param worldY     tọa độ Y điểm va chạm
     * @param instaBreak {@code true} nếu là đạn tên lửa (phá hủy tường ngay lập tức)
     * @return đối tượng {@link WallHitResult} chứa thông tin vỡ tường
     */
    public WallHitResult hitBrickWall(double worldX, double worldY, boolean instaBreak) {
        if (isOutOfBounds(worldX, worldY)) {
            return WallHitResult.none();
        }
        int row = worldToRow(worldY);
        int col = worldToCol(worldX);
        if (tiles[row][col] != TileType.BRICK_WALL.getCode()) {
            return WallHitResult.none();
        }

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
