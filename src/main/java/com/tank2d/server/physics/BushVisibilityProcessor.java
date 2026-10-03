package com.tank2d.server.physics;

import com.tank2d.server.map.GameMap;
import com.tank2d.server.model.TankEntity;

/**
 * Xử lý cơ chế ẩn nấp và tầm nhìn (Fog of War / Stealth) của xe tăng trong các cụm bụi cỏ.
 * <p>
 * Lớp này chỉ đọc trạng thái của xe và bản đồ, không làm biến đổi dữ liệu (stateless / read-only).
 */
public final class BushVisibilityProcessor {

    private BushVisibilityProcessor() {}

    /**
     * Xác định xem người xem (viewer) có quyền quan sát thấy mục tiêu (target) hay không.
     * <p>
     * <b>Quy tắc ẩn hiện:</b>
     * <ol>
     *     <li>Người chơi luôn nhìn thấy chính mình (nếu {@code viewer.id == target.id}).</li>
     *     <li>Mục tiêu không đứng trong bất kỳ cụm bụi cỏ nào &rarr; <b>Luôn thấy</b>.</li>
     *     <li>Mục tiêu vừa nổ súng trong khoảng thời gian lộ hình ({@code now - lastShot &lt; revealWindow}) &rarr; <b>Lộ hình (thấy)</b>.</li>
     *     <li>Nếu người xem và mục tiêu cùng đứng trong <i>cùng một cụm bụi cỏ</i> ({@code clusterViewer == clusterTarget}) &rarr; <b>Thấy</b>.</li>
     *     <li>Trong tất cả các trường hợp còn lại (người xem ở ngoài hoặc ở cụm bụi khác) &rarr; <b>Bị ẩn</b> (không đưa vào snapshot).</li>
     * </ol>
     *
     * @param viewer         xe tăng của người xem (client nhận snapshot)
     * @param target         xe tăng của đối tượng cần kiểm tra khả kiến
     * @param map            bản đồ trận đấu
     * @param nowMillis      thời điểm hiện tại tính bằng mili-giây
     * @param revealWindowMs thời lượng lộ hình sau khi bắn (mili-giây)
     * @return {@code true} nếu người xem được phép thấy mục tiêu, ngược lại {@code false}
     */
    public static boolean isVisibleTo(TankEntity viewer, TankEntity target,
                                      GameMap map, long nowMillis, long revealWindowMs) {
        if (map == null || target == null) {
            return true;
        }
        if (viewer != null && viewer.getId() == target.getId()) {
            return true;
        }

        int targetCluster = map.getBushClusterIdAt(target.getX(), target.getY());
        if (targetCluster == GameMap.NO_CLUSTER) {
            return true;
        }

        boolean recentlyFired = (nowMillis - target.getLastShotTimeMillis()) < revealWindowMs;
        if (recentlyFired) {
            return true;
        }

        if (viewer == null) {
            return false;
        }

        int viewerCluster = map.getBushClusterIdAt(viewer.getX(), viewer.getY());
        return viewerCluster != GameMap.NO_CLUSTER && viewerCluster == targetCluster;
    }
}
