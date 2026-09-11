package com.tank2d.server.game;

import com.tank2d.server.model.TankEntity;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.physics.TankMovementProcessor;
import com.tank2d.common.dto.game.TankSnapshotDTO;
import com.tank2d.common.dto.game.BulletSnapshotDTO;
import com.tank2d.common.dto.game.GameSnapshotDTO;
import java.util.concurrent.atomic.AtomicInteger;
import com.tank2d.server.physics.BulletMovementProcessor;
import com.tank2d.server.input.PlayerInputHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.tank2d.server.physics.BulletMovementProcessor;
import com.tank2d.server.physics.CollisionDetector;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.game.event.CombatEvent;
import com.tank2d.server.game.event.CombatEventListener;
import com.tank2d.server.game.event.SnapshotListener;
import com.tank2d.common.config.ConfigLoader;
import java.util.concurrent.atomic.AtomicInteger;

public class GameLoop implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName());
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<Integer, TankEntity> tanks = new ConcurrentHashMap<>();
    private final Map<Integer, BulletEntity> bullets = new ConcurrentHashMap<>(); // sẵn cho task đạn sau

    private final int tickRate;
    private final double timePerTickNs;
    private long tickCount = 0L;
    private final AtomicInteger bulletIdSeq = new AtomicInteger(1);
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
    public void stopLoop() { running.set(false); }
    public BulletEntity spawnBullet(int ownerId, double x, double y, double vx, double vy) {
        int newId = bulletIdSeq.getAndIncrement();
        BulletEntity bullet = new BulletEntity(newId, ownerId, x, y, vx, vy);
        bullets.put(newId, bullet);
        return bullet;
    }
    private GameMap gameMap; // gán từ bên ngoài khi phòng chơi khởi tạo map
    private CombatEventListener combatListener;
    private SnapshotListener snapshotListener;

    private final int tankSize;
    private final int bulletSize;
    private final int normalBulletDamage;

    public void setGameMap(GameMap gameMap) { this.gameMap = gameMap; }
    public void setCombatListener(CombatEventListener listener) { this.combatListener = listener; }
    public void setSnapshotListener(SnapshotListener listener) { this.snapshotListener = listener; }
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

    /** Dùng để test đơn lẻ (main test) thay vì chạy cả vòng lặp vô hạn của run(). */
    public void tick(double deltaTime) { updatePhysics(deltaTime); }

    private void updatePhysics(double deltaTime) {
        tickCount++;

        // 1. Lưu vị trí cũ để có thể revert khi va chạm tường/xe khác
        Map<Integer, double[]> prevPositions = new HashMap<>();
        for (TankEntity tank : tanks.values()) {
            prevPositions.put(tank.getId(), new double[]{tank.getX(), tank.getY()});
        }

        // 2. Di chuyển xe theo input hiện tại
        for (TankEntity tank : tanks.values()) {
            TankMovementProcessor.update(tank, deltaTime);
        }

        // 3. Chặn xe đi xuyên tường / ra biên map
        for (TankEntity tank : tanks.values()) {
            double[] prev = prevPositions.get(tank.getId());
            CollisionDetector.resolveTankMapCollision(tank, gameMap, prev[0], prev[1], tankSize);
        }

        // 4. Chặn xe đè lên xe khác
        CollisionDetector.resolveTankTankCollision(tanks.values(), tankSize, prevPositions);

        // 5. Di chuyển đạn
        for (BulletEntity bullet : bullets.values()) {
            BulletMovementProcessor.update(bullet, deltaTime);
        }

        // 6. Va chạm Đạn-Xe / Đạn-Tường, phát sự kiện cho Giang
        List<CombatEvent> events = CollisionDetector.resolveBulletCollisions(
                bullets.values(), tanks.values(), gameMap, bulletSize, tankSize, normalBulletDamage);

        if (combatListener != null) {
            for (CombatEvent e : events) {
                combatListener.onCombatEvent(e); // "lập tức" - gọi ngay trong tick va chạm, không trì hoãn
            }
        }

        // 7. Dọn đạn đã tiêu (trúng xe/tường/ra biên)
        bullets.values().removeIf(b -> !b.isAlive());

        // 8. Gửi snapshot định kỳ cho Giang broadcast
        if (snapshotListener != null) {
            snapshotListener.onSnapshotReady(buildSnapshot());
        }
    }

    public Map<Integer, TankEntity> getTanks() { return tanks; }
    public long getTickCount() { return tickCount; }

    public GameSnapshotDTO buildSnapshot() {
        List<TankSnapshotDTO> tankDTOs = new ArrayList<>();
        for (TankEntity tank : tanks.values()) {
            tankDTOs.add(new TankSnapshotDTO(
                    tank.getId(), tank.getX(), tank.getY(), tank.getAngle(),
                    tank.getHp(), tank.isAlive() 
            ));
        }

        List<BulletSnapshotDTO> bulletDTOs = new ArrayList<>();
        for (BulletEntity bullet : bullets.values()) {
            bulletDTOs.add(new BulletSnapshotDTO(
                    bullet.getId(), bullet.getOwnerId(), bullet.getX(), bullet.getY(),
                    bullet.getVx(), bullet.getVy()
            ));
        }

        return new GameSnapshotDTO(tickCount, tankDTOs, bulletDTOs);
    }
}