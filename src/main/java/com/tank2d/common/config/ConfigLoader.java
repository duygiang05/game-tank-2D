package com.tank2d.common.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
    private static Map<String, Object> gameRulesConfig;
    private static JsonObject currentMapConfig;
    private static final Gson gson = new Gson();

    static {
        // 1. Nạp .env
        try {
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            System.err.println("[ConfigLoader] Không tìm thấy file .env, dùng fallback mặc định!");
        }

        // 2. Nạp config/stats.json
        try (FileReader reader = new FileReader("config/stats.json", StandardCharsets.UTF_8)) {
            statsConfig = gson.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            System.err.println("[ConfigLoader] Không thể đọc config/stats.json: " + e.getMessage());
        }

        // 3. Nạp config/game_rules.yml bằng SnakeYAML
        try (InputStream input = new FileInputStream("config/game_rules.yml")) {
            Yaml yaml = new Yaml();
            gameRulesConfig = yaml.load(input);
        } catch (IOException e) {
            System.err.println("[ConfigLoader] Không thể đọc config/game_rules.yml: " + e.getMessage());
        }

        // 4. Nạp map JSON mặc định (map_snow.json)
        loadMapConfig("config/maps/map_snow.json");
    }

    public static void loadMapConfig(String mapPath) {
        try (FileReader reader = new FileReader(mapPath, StandardCharsets.UTF_8)) {
            currentMapConfig = gson.fromJson(reader, JsonObject.class);
            System.out.println("[ConfigLoader] Đã tải cấu hình bản đồ từ: " + mapPath);
        } catch (IOException e) {
            System.err.println("[ConfigLoader] Không thể đọc file map: " + mapPath + " - " + e.getMessage());
        }
    }

    // =========================================================================
    // ĐỌC BIẾN MÔI TRƯỜNG (.ENV)
    // =========================================================================
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

    // =========================================================================
    // ĐỌC THÔNG SỐ VẬT LÝ & DAMAGE (stats.json)
    // =========================================================================
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

    public static double getBulletSpeedPerSecond() {
        JsonObject physics = getPhysicsStats();
        double perTick = physics.has("bullet_speed") ? physics.get("bullet_speed").getAsDouble() : 9.0;
        int tickRate = physics.has("server_tick_rate") ? physics.get("server_tick_rate").getAsInt() : 30;
        return perTick * tickRate;
    }

    public static double getTankSpeedPerSecond() {
        JsonObject physics = getPhysicsStats();
        double perTick = physics.has("tank_speed") ? physics.get("tank_speed").getAsDouble() : 4.0;
        int tickRate = physics.has("server_tick_rate") ? physics.get("server_tick_rate").getAsInt() : 30;
        return perTick * tickRate;
    }

    // =========================================================================
    // ĐỌC LUẬT CHƠI (game_rules.yml)
    // =========================================================================
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getRespawnRules() {
        Object node = getGameRules().get("respawn_times");
        return node instanceof Map ? (Map<String, Object>) node : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getScoringRules() {
        Object node = getGameRules().get("scoring");
        return node instanceof Map ? (Map<String, Object>) node : Map.of();
    }

    public static long getFireCooldownMs() {
        Object val = getTankRules().get("fire_cooldown_ms");
        return val instanceof Number ? ((Number) val).longValue() : 1000L;
    }

    public static int getMaxHp() {
        Object val = getTankRules().get("max_hp");
        return val instanceof Number ? ((Number) val).intValue() : 3;
    }

    public static double getGhostDurationSeconds() {
        Object val = getTankRules().get("ghost_duration_s");
        return val instanceof Number ? ((Number) val).doubleValue() : 5.0;
    }

    public static double getRespawnTime45s() {
        Object val = getRespawnRules().get("match_45s");
        return val instanceof Number ? ((Number) val).doubleValue() : 3.0;
    }

    public static double getRespawnTime60s() {
        Object val = getRespawnRules().get("match_60s");
        return val instanceof Number ? ((Number) val).doubleValue() : 5.0;
    }

    public static double getRespawnTime90s() {
        Object val = getRespawnRules().get("match_90s");
        return val instanceof Number ? ((Number) val).doubleValue() : 7.0;
    }

    public static int getPointsPerHit() {
        Object val = getScoringRules().get("points_per_hit");
        return val instanceof Number ? ((Number) val).intValue() : 10;
    }

    public static int getPointsPerKill() {
        Object val = getScoringRules().get("points_per_kill");
        return val instanceof Number ? ((Number) val).intValue() : 30;
    }

    public static int getPointsWinBonus() {
        Object val = getScoringRules().get("points_win_bonus");
        return val instanceof Number ? ((Number) val).intValue() : 50;
    }

    public static double getDefaultMatchDuration() {
        return 60.0;
    }

    // =========================================================================
    // ĐỌC TỌA ĐỘ VÀ GÓC XOAY ĐỘNG TỪ MẢNG spawn_points TRONG MAP JSON
    // =========================================================================
    private static JsonObject getSpawnPointJson(int tankId) {
        if (currentMapConfig != null && currentMapConfig.has("spawn_points")) {
            JsonArray spawns = currentMapConfig.getAsJsonArray("spawn_points");
            for (JsonElement elem : spawns) {
                if (elem.isJsonObject()) {
                    JsonObject obj = elem.getAsJsonObject();
                    if (obj.has("tank_id") && obj.get("tank_id").getAsInt() == tankId) {
                        return obj;
                    }
                }
            }
        }
        return null;
    }

    public static double getSpawnX(int tankId, double fallback) {
        JsonObject sp = getSpawnPointJson(tankId);
        return (sp != null && sp.has("x")) ? sp.get("x").getAsDouble() : fallback;
    }

    public static double getSpawnY(int tankId, double fallback) {
        JsonObject sp = getSpawnPointJson(tankId);
        return (sp != null && sp.has("y")) ? sp.get("y").getAsDouble() : fallback;
    }

    public static double getSpawnAngle(int tankId, double fallback) {
        JsonObject sp = getSpawnPointJson(tankId);
        return (sp != null && sp.has("angle")) ? sp.get("angle").getAsDouble() : fallback;
    }
    public static double getRevealDurationSeconds() {
        Object val = getTankRules().get("reveal_duration_s");
        return val instanceof Number ? ((Number) val).doubleValue() : 2.0;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getPowerUpRules() {
        Object node = getGameRules().get("power_up");
        return node instanceof Map ? (Map<String, Object>) node : Map.of();
    }

    private static double numOr(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    public static double getItemSpawnIntervalSeconds() { return numOr(getPowerUpRules(), "spawn_interval_s", 7.0); }
    public static double getItemDespawnSeconds() { return numOr(getPowerUpRules(), "despawn_time_s", 7.0); }
    public static int getHealAmount() { return (int) numOr(getPowerUpRules(), "heal_amount", 3.0); }
    public static double getShieldDurationSeconds() { return numOr(getPowerUpRules(), "shield_duration_s", 5.0); }
    public static double getNitroDurationSeconds() { return numOr(getPowerUpRules(), "nitro_duration_s", 5.0); }
    public static double getMissileBuffDurationSeconds() { return numOr(getPowerUpRules(), "missile_buff_duration_s", 5.0); }

    public static double getNitroSpeedMultiplier() {
        JsonObject physics = getPhysicsStats();
        return physics.has("nitro_speed_multiplier") ? physics.get("nitro_speed_multiplier").getAsDouble() : 2.0;
    }

    public static int getRocketBulletDamage() {
        JsonObject damage = getDamageStats();
        return damage.has("rocket_bullet") ? damage.get("rocket_bullet").getAsInt() : 3;
    }
}