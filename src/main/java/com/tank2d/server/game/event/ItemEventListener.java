package com.tank2d.server.game.event;

/** Giang implement để áp dụng hiệu ứng thật (cộng khiên/nitro/đạn tên lửa/hồi máu) khi xe nhặt item. */
public interface ItemEventListener {
    void onItemPickup(ItemPickupEvent event);
}