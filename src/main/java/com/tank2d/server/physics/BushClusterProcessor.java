package com.tank2d.server.physics;

import com.tank2d.server.map.GameMap;
import com.tank2d.server.map.TileType;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Gán clusterId cho từng cụm bụi bằng BFS / Flood Fill.
 * Các ô bụi liền nhau theo 4 hướng (trên, dưới, trái, phải) thuộc cùng 1 cụm.
 * Chỉ cần chạy 1 lần khi nạp map, vì bụi cỏ không bị phá huỷ trong trận.
 */
public final class BushClusterProcessor {

    private BushClusterProcessor() {}

    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    /** @return số cụm bụi tìm được (clusterId chạy từ 0 đến số cụm - 1). */
    public static int assignClusters(GameMap map) {
        if (map == null) return 0;

        int bushCode = TileType.BUSH.getCode();

        // Xoá nhãn cũ (phòng trường hợp gọi lại trên cùng 1 map)
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

    private static void floodFill(GameMap map, int startRow, int startCol, int clusterId, int bushCode) {
        Deque<int[]> queue = new ArrayDeque<>();
        map.setBushClusterId(startRow, startCol, clusterId);
        queue.add(new int[]{startRow, startCol});

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nr = cur[0] + DR[i];
                int nc = cur[1] + DC[i];
                if (!map.isInside(nr, nc)) continue;
                if (map.getTileCode(nr, nc) != bushCode) continue;
                if (map.getBushClusterId(nr, nc) != GameMap.NO_CLUSTER) continue; // đã gán rồi

                map.setBushClusterId(nr, nc, clusterId);
                queue.add(new int[]{nr, nc});
            }
        }
    }
}
