package com.tank2d.client.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.scene.image.Image;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class AssetLoader {

    private static final Map<String, Image> imageCache = new HashMap<>();
    private static JsonObject colorConfig;

    // Tải ảnh từ thư mục src/main/resources/com/tank2d/client/assets/images/
    public static Image getImage(String path) {
        if (!imageCache.containsKey(path)) {
            try (InputStream is = AssetLoader.class.getResourceAsStream("/com/tank2d/client/assets/images/" + path)) {
                if (is != null) {
                    imageCache.put(path, new Image(is));
                } else {
                    System.err.println("[AssetLoader] Không tìm thấy ảnh: " + path);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return imageCache.get(path);
    }

    // Nạp cấu hình màu sắc từ file JSON cấu hình
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

    // Nạp bản đồ JSON động từ thư mục config/maps/
    public static JsonObject loadMapConfig(String mapName) {
        try {
            String path = "config/maps/" + mapName + ".json";
            String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            return new Gson().fromJson(content, JsonObject.class);
        } catch (Exception e) {
            System.err.println("[AssetLoader] Không thể nạp file bản đồ: " + mapName);
            return null;
        }
    }
}