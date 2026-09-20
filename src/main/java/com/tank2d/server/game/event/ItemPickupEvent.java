package com.tank2d.server.game.event;

import com.tank2d.server.item.ItemType;

public class ItemPickupEvent {
    private final int tankId;
    private final int itemId;
    private final ItemType itemType;

    public ItemPickupEvent(int tankId, int itemId, ItemType itemType) {
        this.tankId = tankId; this.itemId = itemId; this.itemType = itemType;
    }

    public int getTankId() { return tankId; }
    public int getItemId() { return itemId; }
    public ItemType getItemType() { return itemType; }
}