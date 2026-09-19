package com.tank2d.server.game.event;

import com.tank2d.common.dto.game.GameSnapshotDTO;
import java.util.Map;

/** key = tankId của người xem, value = snapshot đã lọc bụi cỏ dành riêng cho người đó. */
public interface SnapshotListener {
    void onSnapshotReady(Map<Integer, GameSnapshotDTO> perViewerSnapshots);
}