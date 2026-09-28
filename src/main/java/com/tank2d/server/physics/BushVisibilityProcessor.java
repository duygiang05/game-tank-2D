package com.tank2d.server.physics;

import com.tank2d.server.map.GameMap;
import com.tank2d.server.model.TankEntity;

/** Quy tắc nhìn thấy nhau khi có bụi cỏ. Chỉ đọc dữ liệu, không sửa entity hay map. */
public final class BushVisibilityProcessor {

    private BushVisibilityProcessor() {}

    /**
     * @return true nếu viewer được phép thấy target trong snapshot.
     * Target bị ẩn khi ĐỒNG THỜI: đang trong bụi, chưa bắn trong thời gian lộ hình,
     * và viewer không đứng chung cụm bụi với target.
     */
    public static boolean isVisibleTo(TankEntity viewer, TankEntity target,
                                      GameMap map, long nowMillis, long revealWindowMs) {
        if (map == null) return true;
        if (viewer != null && viewer.getId() == target.getId()) return true; // luôn thấy chính mình

        int targetCluster = map.getBushClusterIdAt(target.getX(), target.getY());
        if (targetCluster == GameMap.NO_CLUSTER) return true; // target không ở trong bụi

        boolean recentlyFired = (nowMillis - target.getLastShotTimeMillis()) < revealWindowMs;
        if (recentlyFired) return true; // vừa bắn -> lộ hình

        if (viewer == null) return false;
        int viewerCluster = map.getBushClusterIdAt(viewer.getX(), viewer.getY());

        // Cùng một cụm bụi -> nhìn thấy nhau 100%
        return viewerCluster != GameMap.NO_CLUSTER && viewerCluster == targetCluster;
    }
}
