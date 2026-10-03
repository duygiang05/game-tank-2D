package com.tank2d.client.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.scene.image.Image;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tiện ích tải và lưu bộ nhớ đệm (cache) tài nguyên hình ảnh và cấu hình giao diện.
 */
public class AssetLoader {

    private static final Logger LOGGER = Logger.getLogger(AssetLoader.class.getName());
    private static final Map<String, Image> imageCache = new ConcurrentHashMap<>();
    private static JsonObject colorConfig;

    /**
     * Tải hình ảnh từ tài nguyên classpath với bộ nhớ đệm.
     *
     * @param path đường dẫn tương đối trong thư mục images
     * @return đối tượng {@link Image} hoặc null nếu không tìm thấy
     */
    public static Image getImage(String path) {
        if (!imageCache.containsKey(path)) {
            try (InputStream is = AssetLoader.class.getResourceAsStream("/com/tank2d/client/assets/images/" + path)) {
                if (is != null) {
                    imageCache.put(path, new Image(is));
                } else {
                    LOGGER.warning("[AssetLoader] Không tìm thấy ảnh: " + path);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[AssetLoader] Lỗi tải ảnh: " + path, e);
            }
        }
        return imageCache.get(path);
    }

    /**
     * Lấy hình ảnh đại diện của vật phẩm bổ trợ (Power-up) theo loại.
     *
     * @param type tên loại vật phẩm (HEALTH_PACK, SHIELD, NITRO, ROCKET_AMMO)
     * @return đối tượng {@link Image} tương ứng hoặc null
     */
    public static Image getPowerUpImage(String type) {
        if (type == null) return null;
        switch (type.toUpperCase()) {
            case "HEALTH_PACK": return getImage("tiles/health_pack.png");
            case "SHIELD": return getImage("tiles/shield.png");
            case "NITRO": return getImage("tiles/nitro.png");
            case "ROCKET_AMMO": return getImage("bullets/rocket.png");
            default: return null;
        }
    }
    
    /**
     * Nạp cấu hình chủ đề màu sắc từ tài nguyên theme_colors.json.
     *
     * @return đối tượng {@link JsonObject} chứa bảng màu
     */
    public static JsonObject getColorTheme() {
        if (colorConfig == null) {
            try (InputStream is = AssetLoader.class.getResourceAsStream("/com/tank2d/client/assets/config/theme_colors.json")) {
                if (is != null) {
                    colorConfig = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
                }
            } catch (Exception ignored) {}
        }
        return colorConfig;
    }

    /**
     * Nạp cấu hình bản đồ từ file JSON tại thư mục config/maps/.
     *
     * @param mapName tên định danh bản đồ
     * @return đối tượng {@link JsonObject} chứa dữ liệu bản đồ hoặc null
     */
    public static JsonObject loadMapConfig(String mapName) {
        try {
            String path = "config/maps/" + mapName + ".json";
            String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            return new Gson().fromJson(content, JsonObject.class);
        } catch (Exception e) {
            LOGGER.warning("[AssetLoader] Không thể nạp file bản đồ: " + mapName);
            return null;
        }
    }
}