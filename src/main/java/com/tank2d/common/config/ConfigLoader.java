package com.tank2d.common.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.cdimascio.dotenv.Dotenv;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ConfigLoader {

    private static Dotenv dotenv;
    private static JsonObject statsConfig;
    private static Map<String, Object> gameRulesConfig; // MỚI
    private static final Gson gson = new Gson();

    static {
        try {
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            System.err.println("[ConfigLoader] Không tìm thấy file .env, sử dụng cấu hình mặc định!");
        }

        try (FileReader reader = new FileReader("config/stats.json", StandardCharsets.UTF_8)) {
            statsConfig = gson.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            System.err.println("[ConfigLoader] Không thể đọc file config/stats.json: " + e.getMessage());
        }

        // MỚI: nạp config/game_rules.yml bằng SnakeYAML
        try (InputStream input = new FileInputStream("config/game_rules.yml")) {
            Yaml yaml = new Yaml();
            gameRulesConfig = yaml.load(input);
        } catch (IOException e) {
            System.err.println("[ConfigLoader] Không thể đọc file config/game_rules.yml: " + e.getMessage());
        }
    }

    // ========== CÁC HÀM CŨ — GIỮ NGUYÊN ==========
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

    // ========== HÀM MỚI: ĐỌC game_rules.yml ==========

    /** Truy cập thô toàn bộ cây cấu hình game_rules.yml, dùng khi cần đọc field ít phổ biến. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getGameRules() {
        if (gameRulesConfig == null) return Map.of();
        Object gameNode = gameRulesConfig.get("game");
        return gameNode instanceof Map ? (Map<String, Object>) gameNode : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getTankRules() {
        Object tankNode = getGameRules().get("tank");
        return tankNode instanceof Map ? (Map<String, Object>) tankNode : Map.of();
    }

    /** fire_cooldown_ms trong game_rules.yml — thời gian giãn cách giữa 2 lần bắn. */
    public static long getFireCooldownMs() {
        Object val = getTankRules().get("fire_cooldown_ms");
        return val instanceof Number ? ((Number) val).longValue() : 1000L; // fallback an toàn
    }

    /** max_hp trong game_rules.yml — máu tối đa của xe tăng. */
    public static int getMaxHp() {
        Object val = getTankRules().get("max_hp");
        return val instanceof Number ? ((Number) val).intValue() : 3; // fallback an toàn
    }

    /** ghost_duration_s — thời gian bất tử sau hồi sinh (dùng cho task combat sau). */
    public static double getGhostDurationSeconds() {
        Object val = getTankRules().get("ghost_duration_s");
        return val instanceof Number ? ((Number) val).doubleValue() : 5.0;
    }
    public static double getBulletSpeedPerSecond() {
        JsonObject physics = getPhysicsStats();
        double perTick = physics.has("bullet_speed") ? physics.get("bullet_speed").getAsDouble() : 9.0;
        int tickRate = physics.has("server_tick_rate") ? physics.get("server_tick_rate").getAsInt() : 30;
        return perTick * tickRate; // quy đổi pixel/tick -> pixel/giây
    }

    public static double getTankSpeedPerSecond() {
        JsonObject physics = getPhysicsStats();
        double perTick = physics.has("tank_speed") ? physics.get("tank_speed").getAsDouble() : 4.0;
        int tickRate = physics.has("server_tick_rate") ? physics.get("server_tick_rate").getAsInt() : 30;
        return perTick * tickRate;
    }
}