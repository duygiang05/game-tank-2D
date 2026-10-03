package com.tank2d.server.map;

import com.google.gson.Gson;
import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.exception.ConfigException;
import com.tank2d.common.exception.ErrorCode;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Tiện ích nạp bản đồ trận đấu từ tệp cấu hình JSON.
 */
public class MapLoader {

    private MapLoader() {}

    /**
     * Cấu trúc dữ liệu nội bộ phục vụ Gson giải mã tệp JSON bản đồ.
     */
    private static class MapFileFormat {
        int width;
        int height;
        int[][] matrix;
    }

    /**
     * Nạp đối tượng {@link GameMap} từ đường dẫn tệp JSON.
     *
     * @param path đường dẫn tới tệp bản đồ JSON
     * @return thực thể {@link GameMap} đã được khởi tạo
     * @throws ConfigException nếu tệp không tồn tại hoặc sai định dạng
     */
    public static GameMap loadFromFile(String path) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(path, StandardCharsets.UTF_8)) {
            MapFileFormat raw = gson.fromJson(reader, MapFileFormat.class);
            if (raw == null || raw.matrix == null) {
                throw new ConfigException(ErrorCode.MAP_LOAD_ERROR, "File map rỗng hoặc sai định dạng: " + path);
            }

            int tileSize = ConfigLoader.getTileSize();
            int brickMaxHits = ConfigLoader.getDamageStats().has("brick_wall_max_hits")
                    ? ConfigLoader.getDamageStats().get("brick_wall_max_hits").getAsInt() : 3;

            return new GameMap(raw.width, raw.height, tileSize, raw.matrix, brickMaxHits);
        } catch (IOException e) {
            throw new ConfigException(ErrorCode.MAP_LOAD_ERROR, "[MapLoader] Không thể đọc file bản đồ: " + path, e);
        }
    }
}