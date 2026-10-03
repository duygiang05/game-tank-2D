package com.tank2d.client.util;

import com.tank2d.common.dto.game.TankSnapshotDTO;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tiện ích xử lý cụm bụi cỏ phía Client.
 * <p>
 * Phục vụ việc gán nhãn cụm (BFS) và xác định cụm bụi cỏ nào đang có từ 2 xe trở lên
 * để hiển thị hiệu ứng mờ/trong suốt cho người chơi.
 */
public final class BushClusterUtil {

    public static final int NO_CLUSTER = -1;
    private static final int BUSH_TILE = 3;

    private static final int[] ROW_OFFSETS = {-1, 1, 0, 0};
    private static final int[] COL_OFFSETS = {0, 0, -1, 1};

    private BushClusterUtil() {}

    /**
     * Tính toán ma trận định danh cụm bụi cỏ từ ma trận bản đồ gốc bằng thuật toán BFS.
     *
     * @param mapMatrix ma trận mã ô bản đồ 2D
     * @return ma trận số nguyên cùng kích thước; ô bụi cỏ mang clusterId (>=0), ô khác mang NO_CLUSTER (-1)
     */
    public static int[][] computeClusterIds(int[][] mapMatrix) {
        if (mapMatrix == null || mapMatrix.length == 0) {
            return new int[0][0];
        }

        int rows = mapMatrix.length;
        int cols = mapMatrix[0].length;
        int[][] ids = new int[rows][cols];
        for (int[] row : ids) {
            Arrays.fill(row, NO_CLUSTER);
        }

        int nextId = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (mapMatrix[r][c] == BUSH_TILE && ids[r][c] == NO_CLUSTER) {
                    floodFill(mapMatrix, ids, r, c, nextId);
                    nextId++;
                }
            }
        }
        return ids;
    }

    private static void floodFill(int[][] map, int[][] ids, int startR, int startC, int clusterId) {
        Deque<int[]> queue = new ArrayDeque<>();
        ids[startR][startC] = clusterId;
        queue.add(new int[]{startR, startC});

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nr = cur[0] + ROW_OFFSETS[i];
                int nc = cur[1] + COL_OFFSETS[i];
                if (nr < 0 || nr >= map.length || nc < 0 || nc >= map[0].length) {
                    continue;
                }
                if (map[nr][nc] != BUSH_TILE || ids[nr][nc] != NO_CLUSTER) {
                    continue;
                }

                ids[nr][nc] = clusterId;
                queue.add(new int[]{nr, nc});
            }
        }
    }

    /**
     * Xác định tập hợp các ID cụm bụi cỏ đang chứa ít nhất {@code minTanks} xe tăng còn sống.
     *
     * @param clusterIds ma trận nhãn cụm bụi cỏ
     * @param tanks      tập hợp snapshot xe tăng trong frame
     * @param tileSize   kích thước mỗi ô vuông bản đồ
     * @param minTanks   ngưỡng số lượng xe tối thiểu trong cụm
     * @return tập hợp các mã cụm bụi cỏ thỏa mãn điều kiện
     */
    public static Set<Integer> findClustersWithTanks(int[][] clusterIds,
                                                     Collection<TankSnapshotDTO> tanks,
                                                     int tileSize,
                                                     int minTanks) {
        Set<Integer> result = new HashSet<>();
        if (clusterIds == null || clusterIds.length == 0 || tileSize <= 0 || tanks == null) {
            return result;
        }

        Map<Integer, Integer> tanksPerCluster = new HashMap<>();
        for (TankSnapshotDTO tank : tanks) {
            if (!tank.isAlive()) {
                continue;
            }

            int c = (int) (tank.getX() / tileSize);
            int r = (int) (tank.getY() / tileSize);
            if (r < 0 || r >= clusterIds.length || c < 0 || c >= clusterIds[0].length) {
                continue;
            }

            int clusterId = clusterIds[r][c];
            if (clusterId == NO_CLUSTER) {
                continue;
            }
            tanksPerCluster.merge(clusterId, 1, Integer::sum);
        }

        for (Map.Entry<Integer, Integer> e : tanksPerCluster.entrySet()) {
            if (e.getValue() >= minTanks) {
                result.add(e.getKey());
            }
        }
        return result;
    }
}
