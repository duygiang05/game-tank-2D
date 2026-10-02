package com.tank2d.server.physics;

import com.tank2d.server.item.ItemType;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.map.TileType;
import com.tank2d.server.model.ItemEntity;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class ItemSpawnProcessor {
    private ItemSpawnProcessor() {}

    private static final int MAX_TRIES = 30;

    /** @return item mới nếu tìm được ô trống hợp lệ, null nếu bản đồ quá chật (hiếm khi xảy ra). */
    public static ItemEntity trySpawn(GameMap map, Collection<ItemEntity> existingItems, AtomicInteger idSeq) {
        if (map == null) return null;
        int[] tile = findRandomEmptyTile(map, existingItems);
        if (tile == null) return null;

        ItemType[] types = ItemType.values();
        ItemType chosen = types[ThreadLocalRandom.current().nextInt(types.length)];
        double x = map.tileCenterX(tile[1]);
        double y = map.tileCenterY(tile[0]);
        return new ItemEntity(idSeq.getAndIncrement(), chosen, x, y, System.currentTimeMillis());
    }

    private static int[] findRandomEmptyTile(GameMap map, Collection<ItemEntity> existingItems) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < MAX_TRIES; i++) {
            int row = rng.nextInt(map.getHeight());
            int col = rng.nextInt(map.getWidth());
            double cx = map.tileCenterX(col), cy = map.tileCenterY(row);

            if (map.getTileAt(cx, cy) != TileType.EMPTY) continue;

            boolean occupied = false;
            for (ItemEntity it : existingItems) {
                if (it.isActive() && map.worldToRow(it.getY()) == row && map.worldToCol(it.getX()) == col) {
                    occupied = true;
                    break;
                }
            }
            if (!occupied) return new int[]{row, col};
        }
        return null; // hết lượt thử, coi như tạm thời không tìm được ô trống
    }
}