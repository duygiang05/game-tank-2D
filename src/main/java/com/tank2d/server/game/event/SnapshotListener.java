package com.tank2d.server.game.event;

import com.tank2d.common.dto.game.GameSnapshotDTO;

/** Giang implement interface này để broadcast GameSnapshotDTO qua NetworkUtil mỗi tick. */
public interface SnapshotListener {
    void onSnapshotReady(GameSnapshotDTO snapshot);
}