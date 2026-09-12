package com.tank2d.server.map;

import com.google.gson.Gson;
import com.tank2d.common.config.ConfigLoader;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MapLoader {

    // DTO nội bộ chỉ để Gson parse file JSON, không dùng để gửi qua mạng nên không đặt trong common.dto
    private static class MapFileFormat {
        String map_name;
        int width;
        int height;
        int[][] matrix;
    }

    public static GameMap loadFromFile(String path) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(path, StandardCharsets.UTF_8)) {
            MapFileFormat raw = gson.fromJson(reader, MapFileFormat.class);
            if (raw == null || raw.matrix == null) {
                throw new IllegalStateException("File map rỗng hoặc sai định dạng: " + path);
            }

            int tileSize = ConfigLoader.getPhysicsStats().has("tile_size")
                    ? ConfigLoader.getPhysicsStats().get("tile_size").getAsInt() : 40;
            int brickMaxHits = ConfigLoader.getDamageStats().has("brick_wall_max_hits")
                    ? ConfigLoader.getDamageStats().get("brick_wall_max_hits").getAsInt() : 3;

            return new GameMap(raw.width, raw.height, tileSize, raw.matrix, brickMaxHits);
        } catch (IOException e) {
            throw new RuntimeException("[MapLoader] Không thể đọc file bản đồ: " + path, e);
        }
    }
}