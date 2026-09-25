package com.tank2d.server.game;

import com.tank2d.server.model.TankEntity;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.model.ItemEntity;
import com.tank2d.server.physics.TankMovementProcessor;
import com.tank2d.server.physics.BulletMovementProcessor;
import com.tank2d.server.physics.CollisionDetector;
import com.tank2d.server.physics.ShootingProcessor;
import com.tank2d.server.physics.ItemSpawnProcessor;
import com.tank2d.server.physics.ProtectionProcessor;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.map.TileType;
import com.tank2d.server.item.ItemType;
import com.tank2d.common.dto.game.*;
import com.tank2d.server.game.event.*;
import com.tank2d.common.config.ConfigLoader;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameLoop implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName());
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

    public GameLoop(int serverTickRate) {
        this.tickRate = serverTickRate;
        this.timePerTickNs = 1_000_000_000.0 / this.tickRate;

        var physics = ConfigLoader.getPhysicsStats();
        this.tankSize = physics.has("tank_size") ? physics.get("tank_size").getAsInt() : 36;
        this.bulletSize = physics.has("bullet_size") ? physics.get("bullet_size").getAsInt() : 8;

        var damage = ConfigLoader.getDamageStats();
        this.normalBulletDamage = damage.has("normal_bullet") ? damage.get("normal_bullet").getAsInt() : 1;
    }

    public void addTank(TankEntity tank) { tanks.put(tank.getId(), tank); }
    public TankEntity getTank(int id) { return tanks.get(id); }
    public void addBullet(BulletEntity bullet) { bullets.put(bullet.getId(), bullet); }
    public Map<Integer, BulletEntity> getBullets() { return bullets; }
    public Map<Integer, ItemEntity> getItems() { return items; }
    public void stopLoop() { running.set(false); }

    public BulletEntity spawnBullet(int ownerId, double x, double y, double vx, double vy, BulletEntity.BulletType type) {
        int newId = bulletIdSeq.getAndIncrement();
        BulletEntity bullet = new BulletEntity(newId, ownerId, x, y, vx, vy, type);
        bullets.put(newId, bullet);
        return bullet;
    }

    public boolean handleShootRequest(TankEntity tank, BulletEntity.BulletType requestedType) {
        ShootingProcessor.ShotResult result = ShootingProcessor.tryShoot(tank, requestedType, System.currentTimeMillis());
        if (!result.success) return false;
        spawnBullet(tank.getId(), tank.getX(), tank.getY(), result.vx, result.vy, result.type);
        return true;
    }

    public void setGameMap(GameMap gameMap) { this.gameMap = gameMap; }
    public void setCombatListener(CombatEventListener listener) { this.combatListener = listener; }
    public void setSnapshotListener(SnapshotListener listener) { this.snapshotListener = listener; }
    public void setMapChangeListener(MapChangeListener listener) { this.mapChangeListener = listener; }
    public void setItemEventListener(ItemEventListener listener) { this.itemEventListener = listener; }

    public void setStateManager(GameStateManager stateManager) {
        this.stateManager = stateManager;
        this.setCombatListener(stateManager);
    }
    public GameStateManager getStateManager() { return stateManager; }

    @Override
    public void run() {
        running.set(true);
        long lastTime = System.nanoTime();
        LOGGER.info("GameLoop initialized at " + tickRate + " ticks/s.");

        while (running.get()) {
            long now = System.nanoTime();
            double deltaTime = (now - lastTime) / 1_000_000_000.0;
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

    public void tick(double deltaTime) { updatePhysics(deltaTime); }

    private void updatePhysics(double deltaTime) {
        tickCount++;

        if (stateManager != null) {
            stateManager.update(deltaTime);
            if (stateManager.isGameOver()) return;
        }

        // 0. Giảm dần thời gian bảo hộ sau hồi sinh
        for (TankEntity tank : tanks.values()) {
            ProtectionProcessor.update(tank, deltaTime);
        }

        // 1. Lưu vị trí cũ
        Map<Integer, double[]> prevPositions = new HashMap<>();
        for (TankEntity tank : tanks.values()) {
            prevPositions.put(tank.getId(), new double[]{tank.getX(), tank.getY()});
        }

        // 2. Di chuyển xe (đã tự áp dụng nitro bên trong TankMovementProcessor)
        for (TankEntity tank : tanks.values()) {
            TankMovementProcessor.update(tank, deltaTime);
        }

        // 3. Chặn xuyên tường / ra biên
        for (TankEntity tank : tanks.values()) {
            double[] prev = prevPositions.get(tank.getId());
            CollisionDetector.resolveTankMapCollision(tank, gameMap, prev[0], prev[1], tankSize);
        }

        // 4. Chặn xe đè xe
        CollisionDetector.resolveTankTankCollision(tanks.values(), tankSize, prevPositions);

        // 5. Di chuyển đạn
        for (BulletEntity bullet : bullets.values()) {
            BulletMovementProcessor.update(bullet, deltaTime);
        }

        // 6. Va chạm Đạn-Xe / Đạn-Tường
        CollisionDetector.BulletCollisionResult bulletResult = CollisionDetector.resolveBulletCollisions(
                bullets.values(), tanks.values(), gameMap, bulletSize, tankSize, normalBulletDamage);

        if (combatListener != null) {
            for (CombatEvent e : bulletResult.combatEvents) combatListener.onCombatEvent(e);
        }
        if (mapChangeListener != null) {
            for (MapChangeEvent e : bulletResult.mapChangeEvents) mapChangeListener.onMapChanged(e);
        }

        bullets.values().removeIf(b -> !b.isAlive());

        // 7. Sinh item định kỳ
        itemSpawnAccumulator += deltaTime;
        double spawnInterval = ConfigLoader.getItemSpawnIntervalSeconds();
        if (itemSpawnAccumulator >= spawnInterval) {
            itemSpawnAccumulator -= spawnInterval;
            ItemEntity newItem = ItemSpawnProcessor.trySpawn(gameMap, items.values(), itemIdSeq);
            if (newItem != null) items.put(newItem.getId(), newItem);
        }

        // 8. Tự huỷ item quá hạn chưa ai nhặt
        long now = System.currentTimeMillis();
        long despawnMs = (long) (ConfigLoader.getItemDespawnSeconds() * 1000);
        items.values().removeIf(it -> (now - it.getSpawnTimeMillis()) > despawnMs);

        // 9. Va chạm Xe-Item
        int itemSize = gameMap != null ? gameMap.getTileSize() : 40;
        List<ItemPickupEvent> pickupEvents = CollisionDetector.resolveItemPickups(tanks.values(), items.values(), tankSize, itemSize);
        if (itemEventListener != null) {
            for (ItemPickupEvent e : pickupEvents) itemEventListener.onItemPickup(e);
        }
        items.values().removeIf(it -> !it.isActive());

        // 10. Gửi snapshot — theo từng người xem (bụi cỏ ẩn/hiện khác nhau)
        if (snapshotListener != null) {
            Map<Integer, GameSnapshotDTO> perViewer = new HashMap<>();
            for (Integer viewerId : tanks.keySet()) {
                perViewer.put(viewerId, buildSnapshotFor(viewerId));
            }
            snapshotListener.onSnapshotReady(perViewer);
        }
    }

    public Map<Integer, TankEntity> getTanks() { return tanks; }
    public long getTickCount() { return tickCount; }

    /** Snapshot đầy đủ, không lọc bụi cỏ — dùng cho demo/công cụ debug, KHÔNG dùng để gửi client thật. */
    public GameSnapshotDTO buildSnapshot() {
        return buildInternal(-1, false);
    }

    /** Snapshot dành riêng cho 1 người xem — xe khác đang nấp bụi (và chưa bắn gần đây) sẽ bị ẩn khỏi danh sách. */
    public GameSnapshotDTO buildSnapshotFor(int viewerTankId) {
        return buildInternal(viewerTankId, true);
    }

    private GameSnapshotDTO buildInternal(int viewerTankId, boolean applyBushStealth) {
    long now = System.currentTimeMillis();
    long revealWindowMs = (long) (ConfigLoader.getRevealDurationSeconds() * 1000);

    List<TankSnapshotDTO> tankDTOs = new ArrayList<>();
    for (TankEntity tank : tanks.values()) {
        if (applyBushStealth && tank.getId() != viewerTankId && gameMap != null) {
            boolean inBush = gameMap.getTileAt(tank.getX(), tank.getY()) == TileType.BUSH;
            boolean recentlyFired = (now - tank.getLastShotTimeMillis()) < revealWindowMs;
            if (inBush && !recentlyFired) continue; // Ẩn khỏi snapshot của viewer này
        }

        // 1. Tính toán trạng thái Buff dựa trên thời gian thực tế của Server
        boolean isGhost = tank.isProtected();
        boolean hasShield = now < tank.getShieldActiveUntilMillis();
        boolean hasNitro = now < tank.getNitroActiveUntilMillis();

        // 2. Khởi tạo DTO với đủ 9 tham số
        tankDTOs.add(new TankSnapshotDTO(
            tank.getId(),
            tank.getX(),
            tank.getY(),
            tank.getAngle(),
            tank.getHp(),
            tank.isAlive(),
            isGhost,     // true nếu đang trong thời gian bảo hộ sau khi sinh
            hasShield,   // true nếu thời gian hiện tại chưa vượt quá shieldActiveUntilMillis
            hasNitro     // true nếu thời gian hiện tại chưa vượt quá nitroActiveUntilMillis
        ));
    }

    List<BulletSnapshotDTO> bulletDTOs = new ArrayList<>();
    for (BulletEntity bullet : bullets.values()) {
        bulletDTOs.add(new BulletSnapshotDTO(
        bullet.getId(), 
        bullet.getOwnerId(), 
        bullet.getX(), 
        bullet.getY(), 
        bullet.getVx(), 
        bullet.getVy(),
        bullet.getType() != null ? bullet.getType().name() : "NORMAL" // <--- ĐÓNG GÓI LOẠI ĐẠN
    ));
    }

    List<ItemSnapshotDTO> itemDTOs = new ArrayList<>();
    for (ItemEntity item : items.values()) {
        itemDTOs.add(new ItemSnapshotDTO(item.getId(), item.getType().name(), item.getX(), item.getY()));
    }

    return new GameSnapshotDTO(tickCount, tankDTOs, bulletDTOs, itemDTOs);
    }
}