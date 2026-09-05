/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tank2d.server.physics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import com.tank2d.common.model.TankSnapshotDTO;
import com.tank2d.common.model.BulletSnapshotDTO;
import com.tank2d.common.model.GameSnapshot;

/**
 *
 * @author Admin
 */
public class GameLoop implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName());
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<Integer, TankEntity> tanks = new ConcurrentHashMap<>();

    private final int tickRate;
    private final double timePerTickNs;
    private long tickCount = 0L;

    public GameLoop(int serverTickRate) {
        this.tickRate = serverTickRate;
        this.timePerTickNs = 1_000_000_000.0 / this.tickRate;
    }

    public void addTank(TankEntity tank) {
        tanks.put(tank.getId(), tank);
    }

    public TankEntity getTank(int id) {
        return tanks.get(id);
    }

    public void stopLoop() {
        running.set(false);
    }

    @Override
    public void run() {
        running.set(true);
        long lastTime = System.nanoTime();
        LOGGER.info("Physics GameLoop initialized at " + tickRate + " ticks/s.");

        while (running.get()) {
            long now = System.nanoTime();
            long elapsedTimeNs = now - lastTime;
            lastTime = now;

            double deltaTime = elapsedTimeNs / 1_000_000_000.0;

            updatePhysics(deltaTime);

            long processTimeNs = System.nanoTime() - now;
            long sleepTimeNs = (long) (timePerTickNs - processTimeNs);

            if (sleepTimeNs > 0) {
                try {
                    // Thread.sleep nhận Millisecond và Nanosecond
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
        LOGGER.info("Physics GameLoop stopped.");
    }

    private void updatePhysics(double deltaTime) {
        tickCount++;
        for (TankEntity tank : tanks.values()) {
            tank.update(deltaTime);
        }
    }

    public Map<Integer, TankEntity> getTanks() {
        return tanks;
    }

    public long getTickCount() {
        return tickCount;
    }

    public GameSnapshot buildSnapshot() {
        List<TankSnapshotDTO> tankDTOs = new ArrayList<>();

        for (TankEntity tank : tanks.values()) {
            // Giả định HP mặc định là 3 và isAlive là true (sẽ được tách ra quản lý động sau khi có combat system)
            TankSnapshotDTO dto = new TankSnapshotDTO(
                    tank.getId(),
                    tank.getX(),
                    tank.getY(),
                    tank.getAngle(),
                    3,
                    true
            );
            tankDTOs.add(dto);
        }

        List<BulletSnapshotDTO> bulletDTOs = new ArrayList<>(); // Stub cho đạn

        return new GameSnapshot(tickCount, tankDTOs, bulletDTOs);
    }

    public static void main(String[] args) throws InterruptedException {
        TankEntity tank = new TankEntity(1, 100, 100, 0, 4.0, 90.0); // 90°/s xoay
        tank.setMoveState(TankEntity.MoveState.FORWARD);

        for (int tick = 1; tick <= 60; tick++) {
            if (tick == 30) {
                tank.setRotateState(TankEntity.RotateState.LEFT); // rẽ trái từ tick 30
            }
            tank.update(1.0 / 30.0); // deltaTime cố định 33.33ms cho test
            System.out.printf("Tick %d: x=%.2f, y=%.2f, angle=%.2f%n",
                    tick, tank.getX(), tank.getY(), tank.getAngle());
            Thread.sleep(33);
        }
    }
}
