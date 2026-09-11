package com.tank2d.server.demo;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.game.GameSnapshotDTO;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.input.PlayerInputHandler;
import com.tank2d.server.map.GameMap;
import com.tank2d.server.map.MapLoader;
import com.tank2d.server.model.TankEntity;

/**
 * DEMO NGHIỆM THU — TASK 2: Collision Engine & Broadcast Map/Snapshot.
 */
public class Task2_Check_va_cham {

    public static void main(String[] args) throws InterruptedException {
        GameLoop loop = new GameLoop(30);
        loop.setGameMap(com.tank2d.server.map.MapLoader.loadFromFile("config/maps/map_default.json"));
        loop.setCombatListener(event ->
                System.out.println(">> COMBAT EVENT: bullet#" + event.getBulletId()
                        + " (shooter=" + event.getShooterId() + ") trúng tank#"
                        + event.getTargetTankId() + " -" + event.getDamage() + " HP"));

        double tankSpeedPerSecond = ConfigLoader.getTankSpeedPerSecond(); // = 4.0 * 30 = 120
        TankEntity shooter = new TankEntity(1, 100, 100, 0, tankSpeedPerSecond, 90.0);
        TankEntity target = new TankEntity(2, 140, 100, 180, tankSpeedPerSecond, 90.0);
        shooter.initHp(ConfigLoader.getMaxHp());
        target.initHp(ConfigLoader.getMaxHp());
        loop.addTank(shooter);
        loop.addTank(target);

        PlayerInputHandler inputHandler = new PlayerInputHandler();
        inputHandler.handleShootRequest(loop, shooter); // bắn ngay, đạn bay sang phải trúng target

        for (int t = 1; t <= 20; t++) {
            loop.tick(1.0 / 30.0);
            System.out.printf("Tick %d: target hp=%d alive=%b%n", t, target.getHp(), target.isAlive());
        }
    }
}