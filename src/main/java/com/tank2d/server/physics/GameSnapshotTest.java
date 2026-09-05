/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tank2d.server.physics;

import com.tank2d.server.game.GameLoop;
import com.tank2d.server.model.TankEntity;
import com.google.gson.Gson;
import com.tank2d.common.dto.game.GameSnapshot;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;
/**
 *
 * @author Admin
 */
public class GameSnapshotTest {
    public static void main(String[] args) {
        GameLoop gameLoop = new GameLoop(30);

        TankEntity tank1 = new TankEntity(101, 150.5, 200.0, 45.5, 4.0, 90.0);
        TankEntity tank2 = new TankEntity(102, 300.0, 100.0, 180.0, 4.0, 90.0);

        gameLoop.addTank(tank1);
        gameLoop.addTank(tank2);

        GameSnapshot snapshot = gameLoop.buildSnapshot();

        Gson gson = new Gson();
        String jsonPayload = gson.toJson(snapshot);

        System.out.println("=== PAYLOAD (Gửi cho Client parse LERP - Tùng) ===");
        System.out.println(jsonPayload);
        System.out.println();

        // Đóng gói vào Packet Envelope (Mở comment 2 dòng dưới để chạy thật trên project)
        // Packet packet = new Packet(PacketType.GAME_SNAPSHOT, jsonPayload);
        // String finalNetworkData = gson.toJson(packet);
        
        // System.out.println("=== 2. GÓI TIN HOÀN CHỈNH (Qua NetworkUtil) ===");
        // System.out.println(finalNetworkData);
    }
}
