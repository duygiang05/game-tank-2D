package com.tank2d.server.game.event;

/** Giang implement interface này để xử lý HP/điểm/kill/respawn khi có va chạm. */
public interface CombatEventListener {
    void onCombatEvent(CombatEvent event);
}