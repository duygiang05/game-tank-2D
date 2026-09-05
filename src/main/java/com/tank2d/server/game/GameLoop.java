package com.tank2d.server.game;

import com.tank2d.server.model.TankEntity;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.physics.TankMovementProcessor;
import com.tank2d.common.dto.game.TankSnapshotDTO;
import com.tank2d.common.dto.game.BulletSnapshotDTO;
import com.tank2d.common.dto.game.GameSnapshot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameLoop implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName());
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<Integer, TankEntity> tanks = new ConcurrentHashMap<>();
    private final Map<Integer, BulletEntity> bullets = new ConcurrentHashMap<>(); // sẵn cho task đạn sau

    private final int tickRate;
    private final double timePerTickNs;
    private long tickCount = 0L;

    public GameLoop(int serverTickRate) {
        this.tickRate = serverTickRate;
        this.timePerTickNs = 1_000_000_000.0 / this.tickRate;
    }

    public void addTank(TankEntity tank) { tanks.put(tank.getId(), tank); }
    public TankEntity getTank(int id) { return tanks.get(id); }
    public void addBullet(BulletEntity bullet) { bullets.put(bullet.getId(), bullet); }
    public void stopLoop() { running.set(false); }

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
        for (TankEntity tank : tanks.values()) {
            TankMovementProcessor.update(tank, deltaTime);
        }
        // Bullet movement/collision xử lý ở task riêng (AABB), chưa nằm trong 2 task hiện tại
    }

    public Map<Integer, TankEntity> getTanks() { return tanks; }
    public long getTickCount() { return tickCount; }

    public GameSnapshot buildSnapshot() {
        List<TankSnapshotDTO> tankDTOs = new ArrayList<>();
        for (TankEntity tank : tanks.values()) {
            tankDTOs.add(new TankSnapshotDTO(
                    tank.getId(), tank.getX(), tank.getY(), tank.getAngle(),
                    3, true // HP/trạng thái thật lấy từ combat system (Giang) khi có
            ));
        }

        List<BulletSnapshotDTO> bulletDTOs = new ArrayList<>();
        for (BulletEntity bullet : bullets.values()) {
            bulletDTOs.add(new BulletSnapshotDTO(
                    bullet.getId(), bullet.getOwnerId(), bullet.getX(), bullet.getY(),
                    bullet.getVx(), bullet.getVy()
            ));
        }

        return new GameSnapshot(tickCount, tankDTOs, bulletDTOs);
    }

    public static void main(String[] args) throws InterruptedException {
        GameLoop loop = new GameLoop(30);
        TankEntity tank = new TankEntity(1, 100, 100, 0, 4.0, 90.0);
        tank.setMoveState(TankEntity.MoveState.FORWARD);
        loop.addTank(tank);

        for (int t = 1; t <= 60; t++) {
            if (t == 30) tank.setRotateState(TankEntity.RotateState.LEFT);
            loop.tick(1.0 / 30.0);
            System.out.printf("Tick %d: x=%.2f, y=%.2f, angle=%.2f%n",
                    t, tank.getX(), tank.getY(), tank.getAngle());
            Thread.sleep(33);
        }

        GameSnapshot snapshot = loop.buildSnapshot();
        String json = new com.google.gson.Gson().toJson(snapshot);
        System.out.println("\n=== GAME_SNAPSHOT mẫu gửi cho Tùng ===");
        System.out.println(json);
    }
}