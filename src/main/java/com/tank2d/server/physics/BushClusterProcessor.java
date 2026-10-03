package com.tank2d.server.physics;

import com.tank2d.server.map.GameMap;
import com.tank2d.server.map.TileType;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Xử lý gom nhóm các ô bụi cỏ liền kề thành từng cụm duy nhất (Bush Clusters).
 * <p>
 * <b>Thuật toán:</b>
 * Sử dụng giải thuật Loang theo chiều rộng (Breadth-First Search - Flood Fill) trên lưới 2D với liên thông 4 hướng:
 * <ul>
 *     <li>Hai ô bụi cỏ được coi là thuộc cùng một cụm nếu chúng tiếp xúc nhau theo một trong bốn hướng (trên, dưới, trái, phải).</li>
 *     <li>Mỗi cụm bụi cỏ được gán một chỉ số định danh số nguyên không âm tăng dần (0, 1, 2, ...).</li>
 *     <li>Thuật toán chỉ cần chạy một lần duy nhất khi khởi tạo bản đồ do bụi cỏ là địa hình tĩnh bất biến trong trận đấu.</li>
 * </ul>
 */
public final class BushClusterProcessor {

    private BushClusterProcessor() {}

    private static final int[] ROW_OFFSETS = {-1, 1, 0, 0};
    private static final int[] COL_OFFSETS = {0, 0, -1, 1};

    /**
     * Duyệt qua toàn bộ bản đồ và gán nhãn định danh cụm cho tất cả các ô bụi cỏ.
     *
     * @param map bản đồ trận đấu cần đánh dấu cụm bụi cỏ
     * @return tổng số lượng cụm bụi cỏ tìm thấy được (từ 0 đến count - 1)
     */
    public static int assignClusters(GameMap map) {
        if (map == null) {
            return 0;
        }

        int bushCode = TileType.BUSH.getCode();

        for (int r = 0; r < map.getHeight(); r++) {
            for (int c = 0; c < map.getWidth(); c++) {
                map.setBushClusterId(r, c, GameMap.NO_CLUSTER);
            }
        }

        int nextClusterId = 0;
        for (int r = 0; r < map.getHeight(); r++) {
            for (int c = 0; c < map.getWidth(); c++) {
                boolean isUnlabeledBush = map.getTileCode(r, c) == bushCode
                        && map.getBushClusterId(r, c) == GameMap.NO_CLUSTER;
                if (isUnlabeledBush) {
                    floodFill(map, r, c, nextClusterId, bushCode);
                    nextClusterId++;
                }
            }
        }

        map.setBushClusterCount(nextClusterId);
        return nextClusterId;
    }

    /**
     * Lan truyền gán nhãn cụm bụi cỏ từ điểm xuất phát (startRow, startCol) bằng hàng đợi BFS.
     *
     * @param map       bản đồ trận đấu
     * @param startRow  tọa độ hàng bắt đầu
     * @param startCol  tọa độ cột bắt đầu
     * @param clusterId mã định danh của cụm đang gán
     * @param bushCode  mã ô định danh của địa hình bụi cỏ
     */
    private static void floodFill(GameMap map, int startRow, int startCol, int clusterId, int bushCode) {
        Deque<int[]> queue = new ArrayDeque<>();
        map.setBushClusterId(startRow, startCol, clusterId);
        queue.add(new int[]{startRow, startCol});

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nr = cur[0] + ROW_OFFSETS[i];
                int nc = cur[1] + COL_OFFSETS[i];
                if (!map.isInside(nr, nc)) {
                    continue;
                }
                if (map.getTileCode(nr, nc) != bushCode) {
                    continue;
                }
                if (map.getBushClusterId(nr, nc) != GameMap.NO_CLUSTER) {
                    continue;
                }

                map.setBushClusterId(nr, nc, clusterId);
                queue.add(new int[]{nr, nc});
            }
        }
    }
}
