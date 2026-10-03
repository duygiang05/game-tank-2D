package com.tank2d.server.physics;

import com.tank2d.server.item.ItemType;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.map.TileType;
import com.tank2d.server.model.ItemEntity;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Xử lý cơ chế sinh ngẫu nhiên các vật phẩm hỗ trợ (Power-up Items) trên bản đồ.
 */
public final class ItemSpawnProcessor {

    private static final int MAX_TRIES = 30;

    private ItemSpawnProcessor() {}

    /**
     * Thử nghiệm sinh một vật phẩm ngẫu nhiên trên một ô đất trống chưa có vật phẩm khác.
     *
     * @param map           bản đồ trận đấu
     * @param existingItems tập hợp các vật phẩm hiện có trên bản đồ
     * @param idSeq         bộ đếm tăng dần cấp phát ID duy nhất cho vật phẩm
     * @return thực thể {@link ItemEntity} mới được tạo, hoặc {@code null} nếu không tìm thấy ô trống sau số lần thử tối đa
     */
    public static ItemEntity trySpawn(GameMap map, Collection<ItemEntity> existingItems, AtomicInteger idSeq) {
        if (map == null) {
            return null;
        }
        int[] tile = findRandomEmptyTile(map, existingItems);
        if (tile == null) {
            return null;
        }

        ItemType[] types = ItemType.values();
        ItemType chosen = types[ThreadLocalRandom.current().nextInt(types.length)];
        double x = map.tileCenterX(tile[1]);
        double y = map.tileCenterY(tile[0]);
        return new ItemEntity(idSeq.getAndIncrement(), chosen, x, y, System.currentTimeMillis());
    }

    /**
     * Tìm ngẫu nhiên một ô đất trống (loại {@link TileType#EMPTY}) trên bản đồ và chưa bị chiếm bởi vật phẩm đang active.
     */
    private static int[] findRandomEmptyTile(GameMap map, Collection<ItemEntity> existingItems) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < MAX_TRIES; i++) {
            int row = rng.nextInt(map.getHeight());
            int col = rng.nextInt(map.getWidth());
            double cx = map.tileCenterX(col);
            double cy = map.tileCenterY(row);

            if (map.getTileAt(cx, cy) != TileType.EMPTY) {
                continue;
            }

            boolean occupied = false;
            if (existingItems != null) {
                for (ItemEntity it : existingItems) {
                    if (it.isActive() && map.worldToRow(it.getY()) == row && map.worldToCol(it.getX()) == col) {
                        occupied = true;
                        break;
                    }
                }
            }
            if (!occupied) {
                return new int[]{row, col};
            }
        }
        return null;
    }
}