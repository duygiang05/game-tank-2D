package com.tank2d.client.util;

import com.tank2d.common.dto.game.TankSnapshotDTO;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Xử lý cụm bụi cỏ phía client: gán nhãn cụm (BFS) và tìm cụm đang có từ 2 xe
 * trở lên.
 */
public final class BushClusterUtil {

    public static final int NO_CLUSTER = -1;
    private static final int BUSH_TILE = 3; // quy ước map: 3 = bụi cỏ

    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    private BushClusterUtil() {
    }

    /**
     * Trả về ma trận cùng kích thước map; ô bụi mang clusterId, ô khác là
     * NO_CLUSTER.
     */
    public static int[][] computeClusterIds(int[][] mapMatrix) {
        if (mapMatrix == null || mapMatrix.length == 0) {
            return new int[0][0];
        }

        int rows = mapMatrix.length;
        int cols = mapMatrix[0].length;
        int[][] ids = new int[rows][cols];
        for (int[] row : ids) {
            java.util.Arrays.fill(row, NO_CLUSTER);
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
                int nr = cur[0] + DR[i];
                int nc = cur[1] + DC[i];
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
     * Các cụm bụi đang chứa từ 2 xe còn sống trở lên (tính theo tâm xe).
     */
    /**
     * Các cụm bụi đang chứa ít nhất minTanks xe còn sống (tính theo tâm xe).
     */
    public static Set<Integer> findClustersWithTanks(int[][] clusterIds,
            Collection<TankSnapshotDTO> tanks,
            int tileSize,
            int minTanks) {
        Set<Integer> result = new HashSet<>();
        if (clusterIds == null || clusterIds.length == 0 || tileSize <= 0) {
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
