package com.tank2d.server.game;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.game.BulletSnapshotDTO;
import com.tank2d.common.dto.game.GameSnapshotDTO;
import com.tank2d.common.dto.game.ItemSnapshotDTO;
import com.tank2d.common.dto.game.TankSnapshotDTO;
import com.tank2d.server.game.event.CombatEvent;
import com.tank2d.server.game.event.CombatEventListener;
import com.tank2d.server.game.event.ItemEventListener;
import com.tank2d.server.game.event.ItemPickupEvent;
import com.tank2d.server.game.event.MapChangeEvent;
import com.tank2d.server.game.event.MapChangeListener;
import com.tank2d.server.game.event.SnapshotListener;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.model.ItemEntity;
import com.tank2d.server.model.TankEntity;
import com.tank2d.server.physics.BulletMovementProcessor;
import com.tank2d.server.physics.BushClusterProcessor;
import com.tank2d.server.physics.BushVisibilityProcessor;
import com.tank2d.server.physics.CollisionDetector;
import com.tank2d.server.physics.ItemSpawnProcessor;
import com.tank2d.server.physics.ProtectionProcessor;
import com.tank2d.server.physics.ShootingProcessor;
import com.tank2d.server.physics.TankMovementProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Vòng lặp vật lý chính của trận đấu (Game Loop Thread).
 * <p>
 * Đảm nhiệm điều phối chu kỳ tính toán vật lý cố định theo tick rate (ví dụ 60Hz), bao gồm:
 * <ul>
 *     <li>Cập nhật chuyển động xe và đạn.</li>
 *     <li>Phát hiện và giải quyết va chạm hình học (Xe-Bản đồ, Xe-Xe, Đạn-Xe, Đạn-Tường, Xe-Item).</li>
 *     <li>Sinh và thu hồi vật phẩm định kỳ.</li>
 *     <li>Tính toán khả kiến qua cụm bụi cỏ và đóng gói snapshot gửi tới từng người chơi.</li>
 * </ul>
 */
public class GameLoop implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName());
    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000.0;
    private static final int DEFAULT_ITEM_SIZE = 40;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<Integer, TankEntity> tanks = new ConcurrentHashMap<>();
    private final Map<Integer, BulletEntity> bullets = new ConcurrentHashMap<>();
    private final Map<Integer, ItemEntity> items = new ConcurrentHashMap<>();

    private final int tickRate;
    private final double timePerTickNs;
    private long tickCount = 0L;
    private final AtomicInteger bulletIdSeq = new AtomicInteger(1);
    private final AtomicInteger itemIdSeq = new AtomicInteger(1);

    private GameMap gameMap;
    private CombatEventListener combatListener;
    private SnapshotListener snapshotListener;
    private MapChangeListener mapChangeListener;
    private ItemEventListener itemEventListener;

    private GameStateManager stateManager;

    private final int tankSize;
    private final int bulletSize;
    private final int normalBulletDamage;

    private double itemSpawnAccumulator = 0.0;

    /**
     * Khởi tạo GameLoop với tần số tick quy định.
     *
     * @param serverTickRate số lượng tick vật lý trong 1 giây (ví dụ: 60)
     */
    public GameLoop(int serverTickRate) {
        this.tickRate = serverTickRate;
        this.timePerTickNs = NANOSECONDS_PER_SECOND / this.tickRate;

        this.tankSize = (int) ConfigLoader.getTankSize();
        this.bulletSize = (int) ConfigLoader.getBulletSize();

        var damage = ConfigLoader.getDamageStats();
        this.normalBulletDamage = damage.has("normal_bullet") ? damage.get("normal_bullet").getAsInt() : 1;
    }

    /**
     * Đưa một xe tăng vào quản lý trong GameLoop.
     *
     * @param tank thực thể xe tăng
     */
    public void addTank(TankEntity tank) {
        tanks.put(tank.getId(), tank);
    }

    /**
     * Lấy thông tin thực thể xe tăng theo ID.
     *
     * @param id mã số xe tăng
     * @return thực thể {@link TankEntity} hoặc null nếu không tồn tại
     */
    public TankEntity getTank(int id) {
        return tanks.get(id);
    }

    /**
     * Đưa một viên đạn vào danh sách quản lý vật lý.
     *
     * @param bullet thực thể đạn
     */
    public void addBullet(BulletEntity bullet) {
        bullets.put(bullet.getId(), bullet);
    }

    /**
     * Lấy bản đồ các viên đạn đang hoạt động trong trận đấu.
     *
     * @return bản đồ id -> {@link BulletEntity}
     */
    public Map<Integer, BulletEntity> getBullets() {
        return bullets;
    }

    /**
     * Lấy bản đồ các vật phẩm hỗ trợ đang xuất hiện trên bản đồ.
     *
     * @return bản đồ id -> {@link ItemEntity}
     */
    public Map<Integer, ItemEntity> getItems() {
        return items;
    }

    /**
     * Dừng vòng lặp GameLoop an toàn.
     */
    public void stopLoop() {
        running.set(false);
    }

    /**
     * Tạo và đưa một viên đạn mới vào không gian trận đấu.
     *
     * @param ownerId ID xe tăng bắn ra viên đạn
     * @param x       tọa độ xuất phát X
     * @param y       tọa độ xuất phát Y
     * @param vx      vận tốc theo trục X (pixel/s)
     * @param vy      vận tốc theo trục Y (pixel/s)
     * @param type    loại đạn (NORMAL hoặc ROCKET)
     * @return thực thể {@link BulletEntity} vừa được khởi tạo
     */
    public BulletEntity spawnBullet(int ownerId, double x, double y, double vx, double vy, BulletEntity.BulletType type) {
        int newId = bulletIdSeq.getAndIncrement();
        BulletEntity bullet = new BulletEntity(newId, ownerId, x, y, vx, vy, type);
        bullets.put(newId, bullet);
        return bullet;
    }

    /**
     * Xử lý yêu cầu bắn đạn từ xe tăng.
     *
     * @param tank          xe tăng gửi lệnh bắn
     * @param requestedType loại đạn yêu cầu từ phía client
     * @return {@code true} nếu phát bắn hợp lệ và sinh đạn thành công
     */
    public boolean handleShootRequest(TankEntity tank, BulletEntity.BulletType requestedType) {
        long now = System.currentTimeMillis();
        ShootingProcessor.ShotResult result = ShootingProcessor.tryShoot(tank, now);
        if (!result.success) {
            return false;
        }

        spawnBullet(tank.getId(), tank.getX(), tank.getY(), result.vx, result.vy, result.type);
        return true;
    }

    /**
     * Gán bản đồ trận đấu và tự động phân cụm bụi cỏ bằng BFS.
     *
     * @param gameMap bản đồ trận đấu
     */
    public void setGameMap(GameMap gameMap) {
        this.gameMap = gameMap;
        if (gameMap != null) {
            int clusters = BushClusterProcessor.assignClusters(gameMap);
            LOGGER.info("Đã gán nhãn " + clusters + " cụm bụi cỏ.");
        }
    }

    /**
     * Đăng ký đối tượng lắng nghe các sự kiện giao tranh (Combat Events).
     *
     * @param listener đối tượng triển khai {@link CombatEventListener}
     */
    public void setCombatListener(CombatEventListener listener) {
        this.combatListener = listener;
    }

    /**
     * Đăng ký đối tượng lắng nghe snapshot dữ liệu gửi tới client định kỳ.
     *
     * @param listener đối tượng triển khai {@link SnapshotListener}
     */
    public void setSnapshotListener(SnapshotListener listener) {
        this.snapshotListener = listener;
    }

    /**
     * Đăng ký đối tượng lắng nghe các sự kiện thay đổi địa hình bản đồ (phá gạch).
     *
     * @param listener đối tượng triển khai {@link MapChangeListener}
     */
    public void setMapChangeListener(MapChangeListener listener) {
        this.mapChangeListener = listener;
    }

    /**
     * Đăng ký đối tượng lắng nghe các sự kiện nhặt vật phẩm bổ trợ.
     *
     * @param listener đối tượng triển khai {@link ItemEventListener}
     */
    public void setItemEventListener(ItemEventListener listener) {
        this.itemEventListener = listener;
    }

    /**
     * Gán trình quản lý trạng thái trận đấu {@link GameStateManager}.
     *
     * @param stateManager đối tượng quản lý trạng thái
     */
    public void setStateManager(GameStateManager stateManager) {
        this.stateManager = stateManager;
        this.setCombatListener(stateManager);
    }

    /**
     * Lấy trình quản lý trạng thái trận đấu hiện tại.
     *
     * @return {@link GameStateManager} hoặc null
     */
    public GameStateManager getStateManager() {
        return stateManager;
    }

    @Override
    public void run() {
        running.set(true);
        long lastTime = System.nanoTime();
        LOGGER.info("GameLoop initialized at " + tickRate + " ticks/s.");

        while (running.get()) {
            long now = System.nanoTime();
            double deltaTime = (now - lastTime) / NANOSECONDS_PER_SECOND;
            lastTime = now;

            updatePhysics(deltaTime);

            long processTimeNs = System.nanoTime() - now;
            long sleepTimeNs = (long) (timePerTickNs - processTimeNs);
            if (sleepTimeNs > 0) {
                try {
                    Thread.sleep(sleepTimeNs / 1_000_000, (int) (sleepTimeNs % 1_000_000));
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "GameLoop Thread bị gián đoạn!", e);
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                LOGGER.warning("CẢNH BÁO LAG: Server mất quá nhiều thời gian để xử lý tick hiện tại!");
            }
        }
        LOGGER.info("GameLoop stopped.");
    }

    /**
     * Cập nhật toàn bộ vật lý, va chạm và sự kiện trong một tick đơn lẻ:
     * đếm ngược trạng thái, di chuyển xe và đạn, giải quyết va chạm hình học,
     * tự động sinh/thu hồi vật phẩm và phát tán snapshot tới các người chơi.
     *
     * @param deltaTime khoảng thời gian trôi qua giữa 2 tick (giây)
     */
    private void updatePhysics(double deltaTime) {
        tickCount++;

        if (stateManager != null) {
            stateManager.update(deltaTime);
            if (stateManager.isGameOver()) {
                return;
            }
        }

        for (TankEntity tank : tanks.values()) {
            ProtectionProcessor.update(tank, deltaTime);
        }

        Map<Integer, double[]> prevPositions = new HashMap<>();
        for (TankEntity tank : tanks.values()) {
            prevPositions.put(tank.getId(), new double[]{tank.getX(), tank.getY()});
        }

        for (TankEntity tank : tanks.values()) {
            TankMovementProcessor.update(tank, deltaTime);
        }

        for (TankEntity tank : tanks.values()) {
            double[] prev = prevPositions.get(tank.getId());
            CollisionDetector.resolveTankMapCollision(tank, gameMap, prev[0], prev[1], tankSize);
        }

        CollisionDetector.resolveTankTankCollision(tanks.values(), tankSize, prevPositions);

        for (BulletEntity bullet : bullets.values()) {
            BulletMovementProcessor.update(bullet, deltaTime);
        }

        CollisionDetector.BulletCollisionResult bulletResult = CollisionDetector.resolveBulletCollisions(
                bullets.values(), tanks.values(), gameMap, bulletSize, tankSize, normalBulletDamage);

        if (combatListener != null) {
            for (CombatEvent e : bulletResult.combatEvents) {
                combatListener.onCombatEvent(e);
            }
        }
        if (mapChangeListener != null) {
            for (MapChangeEvent e : bulletResult.mapChangeEvents) {
                mapChangeListener.onMapChanged(e);
            }
        }

        bullets.values().removeIf(b -> !b.isAlive());

        itemSpawnAccumulator += deltaTime;
        double spawnInterval = ConfigLoader.getItemSpawnIntervalSeconds();
        if (itemSpawnAccumulator >= spawnInterval) {
            itemSpawnAccumulator -= spawnInterval;
            ItemEntity newItem = ItemSpawnProcessor.trySpawn(gameMap, items.values(), itemIdSeq);
            if (newItem != null) {
                items.put(newItem.getId(), newItem);
            }
        }

        long now = System.currentTimeMillis();
        long despawnMs = (long) (ConfigLoader.getItemDespawnSeconds() * 1000);
        items.values().removeIf(it -> (now - it.getSpawnTimeMillis()) > despawnMs);

        int itemSize = gameMap != null ? gameMap.getTileSize() : DEFAULT_ITEM_SIZE;
        List<ItemPickupEvent> pickupEvents = CollisionDetector.resolveItemPickups(tanks.values(), items.values(), tankSize, itemSize);
        if (itemEventListener != null) {
            for (ItemPickupEvent e : pickupEvents) {
                itemEventListener.onItemPickup(e);
            }
        }
        items.values().removeIf(it -> !it.isActive());

        if (snapshotListener != null) {
            List<BulletSnapshotDTO> bulletDTOs = buildBulletDTOs();
            List<ItemSnapshotDTO> itemDTOs = buildItemDTOs();
            Map<Integer, GameSnapshotDTO> perViewer = new HashMap<>();
            for (Integer viewerId : tanks.keySet()) {
                perViewer.put(viewerId, buildSnapshotFor(viewerId, bulletDTOs, itemDTOs));
            }
            snapshotListener.onSnapshotReady(perViewer);
        }
    }

    /**
     * Lấy toàn bộ danh sách các xe tăng trong phòng đấu.
     *
     * @return bản đồ id -> {@link TankEntity}
     */
    public Map<Integer, TankEntity> getTanks() {
        return tanks;
    }

    /**
     * Lấy số tick vật lý đã xử lý kể từ lúc bắt đầu trận đấu.
     *
     * @return số lượng tick
     */
    public long getTickCount() {
        return tickCount;
    }

    private List<BulletSnapshotDTO> buildBulletDTOs() {
        List<BulletSnapshotDTO> bulletDTOs = new ArrayList<>(bullets.size());
        for (BulletEntity bullet : bullets.values()) {
            bulletDTOs.add(new BulletSnapshotDTO(
                bullet.getId(),
                bullet.getOwnerId(),
                bullet.getX(),
                bullet.getY(),
                bullet.getVx(),
                bullet.getVy(),
                bullet.getType() != null ? bullet.getType().name() : "NORMAL"
            ));
        }
        return bulletDTOs;
    }

    private List<ItemSnapshotDTO> buildItemDTOs() {
        List<ItemSnapshotDTO> itemDTOs = new ArrayList<>(items.size());
        for (ItemEntity item : items.values()) {
            itemDTOs.add(new ItemSnapshotDTO(item.getId(), item.getType().name(), item.getX(), item.getY()));
        }
        return itemDTOs;
    }

    /**
     * Đóng gói snapshot đầy đủ không áp dụng lọc bụi cỏ (phục vụ debug/quan sát toàn cảnh).
     *
     * @return đối tượng {@link GameSnapshotDTO} chứa trạng thái đầy đủ
     */
    public GameSnapshotDTO buildSnapshot() {
        return buildInternal(-1, false, buildBulletDTOs(), buildItemDTOs());
    }

    /**
     * Đóng gói snapshot tùy biến riêng theo góc nhìn của một xe tăng cụ thể (lọc tàng hình trong bụi cỏ).
     *
     * @param viewerTankId ID xe tăng của người xem
     * @return đối tượng {@link GameSnapshotDTO} đã được lọc hiển thị
     */
    public GameSnapshotDTO buildSnapshotFor(int viewerTankId) {
        return buildSnapshotFor(viewerTankId, buildBulletDTOs(), buildItemDTOs());
    }

    /**
     * Đóng gói snapshot với danh sách đạn và item đã được tính toán sẵn.
     *
     * @param viewerTankId ID xe tăng người xem
     * @param bulletDTOs   danh sách DTO đạn
     * @param itemDTOs     danh sách DTO vật phẩm
     * @return {@link GameSnapshotDTO}
     */
    public GameSnapshotDTO buildSnapshotFor(int viewerTankId, List<BulletSnapshotDTO> bulletDTOs, List<ItemSnapshotDTO> itemDTOs) {
        return buildInternal(viewerTankId, true, bulletDTOs, itemDTOs);
    }

    private GameSnapshotDTO buildInternal(int viewerTankId, boolean applyBushStealth, List<BulletSnapshotDTO> bulletDTOs, List<ItemSnapshotDTO> itemDTOs) {
        long now = System.currentTimeMillis();
        long revealWindowMs = (long) (ConfigLoader.getRevealDurationSeconds() * 1000);
        TankEntity viewerTank = tanks.get(viewerTankId);

        List<TankSnapshotDTO> tankDTOs = new ArrayList<>();
        for (TankEntity tank : tanks.values()) {
            if (applyBushStealth
                    && !BushVisibilityProcessor.isVisibleTo(viewerTank, tank, gameMap, now, revealWindowMs)) {
                continue;
            }

            boolean isGhost = tank.isProtected();
            boolean hasShield = now < tank.getShieldActiveUntilMillis();
            boolean hasNitro = now < tank.getNitroActiveUntilMillis();
            boolean isAfk = (stateManager != null && stateManager.isPlayerDisconnected(tank.getId()));

            TankSnapshotDTO dto = new TankSnapshotDTO(
                tank.getId(),
                tank.getX(),
                tank.getY(),
                tank.getAngle(),
                tank.getHp(),
                tank.isAlive(),
                isGhost,
                hasShield,
                hasNitro
            );
            dto.setDisconnected(isAfk);
            tankDTOs.add(dto);
        }

        return new GameSnapshotDTO(tickCount, tankDTOs, bulletDTOs, itemDTOs);
    }
}
