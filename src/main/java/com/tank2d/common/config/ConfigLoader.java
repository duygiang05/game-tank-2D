package com.tank2d.common.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Trình nạp cấu hình hệ thống tập trung.
 * Tuyệt đối không hardcode IP, Port, DB hay các thông số Game.
 */
public class ConfigLoader {

    private static Dotenv dotenv;
    private static JsonObject statsConfig;
    private static final Gson gson = new Gson();

    static {
        // Tự động load file .env ở thư mục gốc
        try {
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            System.err.println("[ConfigLoader] Không tìm thấy file .env, sử dụng cấu hình mặc định!");
        }

        // Tự động load file stats.json trong thư mục config/
        try (FileReader reader = new FileReader("config/stats.json", StandardCharsets.UTF_8)) {
            statsConfig = gson.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            System.err.println("[ConfigLoader] Không thể đọc file config/stats.json: " + e.getMessage());
        }
    }

    // ==========================================
    // CÁC HÀM ĐỌC BIẾN TỪ FILE .ENV
    // ==========================================
    public static String getEnv(String key, String defaultValue) {
        if (dotenv == null) return defaultValue;
        String val = dotenv.get(key);
        return val != null ? val : defaultValue;
    }

    public static int getEnvInt(String key, int defaultValue) {
        String val = getEnv(key, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ==========================================
    // CÁC HÀM ĐỌC THÔNG SỐ GAME TỪ STATS.JSON
    // ==========================================
    public static JsonObject getPhysicsStats() {
        if (statsConfig != null && statsConfig.has("physics")) {
            return statsConfig.getAsJsonObject("physics");
        }
        return new JsonObject();
    }

    public static JsonObject getDamageStats() {
        if (statsConfig != null && statsConfig.has("damage")) {
            return statsConfig.getAsJsonObject("damage");
        }
        return new JsonObject();
    }
}