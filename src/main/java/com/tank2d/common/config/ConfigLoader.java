package com.tank2d.common.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.cdimascio.dotenv.Dotenv;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trình nạp và quản lý cấu hình tập trung cho toàn bộ ứng dụng Tank 2D Online.
 * <p>
 * Nạp dữ liệu từ:
 * <ul>
 *     <li>Tệp môi trường (.env) cho cổng TCP, cấu hình DB.</li>
 *     <li>Tệp JSON (config/stats.json) cho các thông số vật lý và sát thương.</li>
 *     <li>Tệp YAML (config/game_rules.yml) cho quy tắc tính điểm, thời gian bảo hộ, hồi sinh.</li>
 *     <li>Tệp JSON bản đồ mặc định (config/maps/map_default.json) cho kích thước lưới và tọa độ điểm xuất hiện.</li>
 * </ul>
 */
public final class ConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(ConfigLoader.class.getName());
    private static final Gson GSON = new Gson();

    private static Dotenv dotenv;
    private static final java.util.Properties APP_PROPERTIES = new java.util.Properties();
    private static JsonObject statsConfig;
    private static Map<String, Object> gameRulesConfig;
    private static JsonObject currentMapConfig;

    private ConfigLoader() {}

    static {
        loadConfigs();
    }

    private static Reader getReader(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            try {
                return new FileReader(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "[ConfigLoader] Lỗi mở file: " + filePath, e);
            }
        }
        InputStream is = ConfigLoader.class.getResourceAsStream("/" + filePath);
        if (is != null) {
            return new InputStreamReader(is, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static InputStream getInputStream(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            try {
                return new FileInputStream(file);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "[ConfigLoader] Lỗi mở file: " + filePath, e);
            }
        }
        return ConfigLoader.class.getResourceAsStream("/" + filePath);
    }

    private static void loadConfigs() {
        try {
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[ConfigLoader] Không tìm thấy file .env, dùng fallback mặc định!");
        }

        try (Reader reader = getReader("config/stats.json")) {
            if (reader != null) {
                statsConfig = GSON.fromJson(reader, JsonObject.class);
                LOGGER.info("[ConfigLoader] Đã tải config/stats.json thành công.");
            } else {
                LOGGER.severe("[ConfigLoader] Không tìm thấy config/stats.json!");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ConfigLoader] Lỗi khi nạp config/stats.json", e);
        }

        try (InputStream input = getInputStream("config/game_rules.yml")) {
            if (input != null) {
                Yaml yaml = new Yaml();
                gameRulesConfig = yaml.load(input);
                LOGGER.info("[ConfigLoader] Đã tải config/game_rules.yml thành công.");
            } else {
                LOGGER.severe("[ConfigLoader] Không tìm thấy config/game_rules.yml!");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ConfigLoader] Lỗi khi nạp config/game_rules.yml", e);
        }

        loadMapConfig("config/maps/map_default.json");
        loadAppProperties();
    }

    private static void loadAppProperties() {
        File external = new File("config.properties");
        if (external.exists()) {
            try (InputStream in = new FileInputStream(external)) {
                APP_PROPERTIES.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                LOGGER.info("[ConfigLoader] Đã tải config.properties từ tệp bên ngoài.");
                return;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "[ConfigLoader] Lỗi đọc config.properties bên ngoài", e);
            }
        }

        try (InputStream in = ConfigLoader.class.getResourceAsStream("/config.properties")) {
            if (in != null) {
                APP_PROPERTIES.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                LOGGER.info("[ConfigLoader] Đã tải config.properties từ classpath.");
            } else {
                LOGGER.warning("[ConfigLoader] Không tìm thấy config.properties trong classpath!");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[ConfigLoader] Lỗi khi nạp config.properties từ classpath", e);
        }
    }

    /**
     * Lấy giá trị cấu hình dạng chuỗi từ file config.properties.
     *
     * @param key          tên khóa cấu hình
     * @param defaultValue giá trị mặc định nếu khóa không tồn tại
     * @return giá trị cấu hình tương ứng
     */
    public static String getProperty(String key, String defaultValue) {
        String val = APP_PROPERTIES.getProperty(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : defaultValue;
    }

    /**
     * Lấy giá trị cấu hình dạng số nguyên từ file config.properties.
     *
     * @param key          tên khóa cấu hình
     * @param defaultValue giá trị số nguyên mặc định nếu khóa không tồn tại
     * @return giá trị số nguyên tương ứng
     */
    public static int getPropertyInt(String key, int defaultValue) {
        String val = getProperty(key, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Nạp cấu hình bản đồ từ tệp tin JSON chỉ định.
     *
     * @param mapPath đường dẫn tệp JSON bản đồ
     */
    public static void loadMapConfig(String mapPath) {
        try (Reader reader = getReader(mapPath)) {
            if (reader != null) {
                currentMapConfig = GSON.fromJson(reader, JsonObject.class);
                LOGGER.info("[ConfigLoader] Đã tải cấu hình bản đồ từ: " + mapPath);
            } else {
                LOGGER.severe("[ConfigLoader] Không tìm thấy file bản đồ: " + mapPath);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ConfigLoader] Lỗi khi đọc file map: " + mapPath, e);
        }
    }

    /**
     * Lấy giá trị biến môi trường dạng chuỗi từ file .env.
     *
     * @param key          tên khóa biến môi trường
     * @param defaultValue giá trị mặc định nếu khóa không tồn tại
     * @return giá trị cấu hình tương ứng
     */
    public static String getEnv(String key, String defaultValue) {
        if (dotenv == null) return defaultValue;
        String val = dotenv.get(key);
        return val != null ? val : defaultValue;
    }

    /**
     * Lấy giá trị biến môi trường dạng số nguyên từ file .env.
     *
     * @param key          tên khóa biến môi trường
     * @param defaultValue giá trị số nguyên mặc định nếu khóa không tồn tại
     * @return giá trị số nguyên tương ứng
     */
    public static int getEnvInt(String key, int defaultValue) {
        String val = getEnv(key, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Lấy cấu hình vật lý dạng JSON object từ stats.json.
     *
     * @return {@link JsonObject} chứa các thông số vật lý
     */
    public static JsonObject getPhysicsStats() {
        if (statsConfig != null && statsConfig.has("physics")) {
            return statsConfig.getAsJsonObject("physics");
        }
        return new JsonObject();
    }

    /**
     * Kích thước cạnh ô vuông trên lưới bản đồ (pixel).
     *
     * @return kích thước ô gạch
     */
    public static int getTileSize() {
        JsonObject physics = getPhysicsStats();
        return physics.has("tile_size") ? physics.get("tile_size").getAsInt() : 30;
    }

    /**
     * Kích thước thân xe tăng (pixel).
     *
     * @return độ dài cạnh xe tăng
     */
    public static double getTankSize() {
        JsonObject physics = getPhysicsStats();
        return physics.has("tank_size") ? physics.get("tank_size").getAsDouble() : 24.0;
    }

    /**
     * Kích thước viên đạn (pixel).
     *
     * @return độ dài cạnh viên đạn
     */
    public static double getBulletSize() {
        JsonObject physics = getPhysicsStats();
        return physics.has("bullet_size") ? physics.get("bullet_size").getAsDouble() : 6.0;
    }

    /**
     * Lấy cấu hình sát thương từ stats.json.
     *
     * @return {@link JsonObject} chứa thông số sát thương
     */
    public static JsonObject getDamageStats() {
        if (statsConfig != null && statsConfig.has("damage")) {
            return statsConfig.getAsJsonObject("damage");
        }
        return new JsonObject();
    }

    /**
     * Số lần trúng đạn tối đa để một khối tường gạch bị phá hủy hoàn toàn.
     *
     * @return độ bền khối tường gạch
     */
    public static int getBrickWallMaxHits() {
        JsonObject damage = getDamageStats();
        return damage.has("brick_wall_max_hits") ? damage.get("brick_wall_max_hits").getAsInt() : 3;
    }

    /**
     * Vận tốc bay của đạn thường tính bằng pixel trên giây.
     *
     * @return vận tốc đạn thường (pixel/s)
     */
    public static double getBulletSpeedPerSecond() {
        JsonObject physics = getPhysicsStats();
        double perTick = physics.has("bullet_speed") ? physics.get("bullet_speed").getAsDouble() : 9.0;
        int tickRate = physics.has("server_tick_rate") ? physics.get("server_tick_rate").getAsInt() : 30;
        return perTick * tickRate;
    }

    /**
     * Vận tốc bay của đạn tên lửa (Rocket) tính bằng pixel trên giây.
     *
     * @return vận tốc đạn tên lửa (pixel/s)
     */
    public static double getMissileBulletSpeedPerSecond() {
        JsonObject physics = getPhysicsStats();
        double perTick = physics.has("missile_bullet_speed")
                ? physics.get("missile_bullet_speed").getAsDouble()
                : 16.0;
        int tickRate = physics.has("server_tick_rate")
                ? physics.get("server_tick_rate").getAsInt()
                : 30;
        return perTick * tickRate;
    }

    /**
     * Vận tốc di chuyển của xe tăng tính bằng pixel trên giây.
     *
     * @return vận tốc xe tăng (pixel/s)
     */
    public static double getTankSpeedPerSecond() {
        JsonObject physics = getPhysicsStats();
        double perTick = physics.has("tank_speed") ? physics.get("tank_speed").getAsDouble() : 4.0;
        int tickRate = physics.has("server_tick_rate") ? physics.get("server_tick_rate").getAsInt() : 30;
        return perTick * tickRate;
    }

    /**
     * Lấy nút cấu hình "game" từ tệp game_rules.yml.
     *
     * @return {@link Map} cấu hình game
     */
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

    /**
     * Thời gian hồi nòng súng giữa 2 phát bắn tính bằng mili-giây.
     *
     * @return thời gian cooldown bắn (ms)
     */
    public static long getFireCooldownMs() {
        Object val = getTankRules().get("fire_cooldown_ms");
        return val instanceof Number ? ((Number) val).longValue() : 1000L;
    }

    /**
     * Lượng máu tối đa của xe tăng.
     *
     * @return chỉ số HP tối đa
     */
    public static int getMaxHp() {
        Object val = getTankRules().get("max_hp");
        return val instanceof Number ? ((Number) val).intValue() : 3;
    }

    /**
     * Thời gian bất tử / bảo hộ sau khi hồi sinh (tính bằng giây).
     *
     * @return thời gian bảo hộ Ghost (giây)
     */
    public static double getGhostDurationSeconds() {
        Object val = getTankRules().get("ghost_duration_s");
        return val instanceof Number ? ((Number) val).doubleValue() : 5.0;
    }

    /**
     * Thời gian chờ hồi sinh cho trận 45 giây.
     *
     * @return thời gian hồi sinh (giây)
     */
    public static double getRespawnTime45s() {
        Object val = getRespawnRules().get("match_45s");
        return val instanceof Number ? ((Number) val).doubleValue() : 3.0;
    }

    /**
     * Thời gian chờ hồi sinh cho trận 60 giây.
     *
     * @return thời gian hồi sinh (giây)
     */
    public static double getRespawnTime60s() {
        Object val = getRespawnRules().get("match_60s");
        return val instanceof Number ? ((Number) val).doubleValue() : 5.0;
    }

    /**
     * Thời gian chờ hồi sinh cho trận 90 giây.
     *
     * @return thời gian hồi sinh (giây)
     */
    public static double getRespawnTime90s() {
        Object val = getRespawnRules().get("match_90s");
        return val instanceof Number ? ((Number) val).doubleValue() : 7.0;
    }

    /**
     * Thời gian chờ hồi sinh cho trận 180 giây.
     *
     * @return thời gian hồi sinh (giây)
     */
    public static double getRespawnTime180s() {
        Object val = getRespawnRules().get("match_180s");
        return val instanceof Number ? ((Number) val).doubleValue() : 9.0;
    }

    /**
     * Điểm số nhận được khi bắn trúng xe đối phương.
     *
     * @return điểm số
     */
    public static int getPointsPerHit() {
        Object val = getScoringRules().get("points_per_hit");
        return val instanceof Number ? ((Number) val).intValue() : 10;
    }

    /**
     * Điểm số nhận được khi hạ gục một xe tăng đối phương.
     *
     * @return điểm số
     */
    public static int getPointsPerKill() {
        Object val = getScoringRules().get("points_per_kill");
        return val instanceof Number ? ((Number) val).intValue() : 30;
    }

    /**
     * Điểm thưởng thêm cho người chiến thắng chung cuộc.
     *
     * @return điểm thưởng chiến thắng
     */
    public static int getPointsWinBonus() {
        Object val = getScoringRules().get("points_win_bonus");
        return val instanceof Number ? ((Number) val).intValue() : 50;
    }

    /**
     * Thời lượng trận đấu mặc định nếu không thiết lập khác (giây).
     *
     * @return thời lượng mặc định (60 giây)
     */
    public static double getDefaultMatchDuration() {
        return 60.0;
    }

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

    /**
     * Tọa độ xuất phát X của xe theo ID tank cấu hình trong bản đồ.
     *
     * @param tankId   mã số xe tăng
     * @param fallback giá trị dự phòng nếu không tìm thấy
     * @return tọa độ X (pixel)
     */
    public static double getSpawnX(int tankId, double fallback) {
        JsonObject sp = getSpawnPointJson(tankId);
        return (sp != null && sp.has("x")) ? sp.get("x").getAsDouble() : fallback;
    }

    /**
     * Tọa độ xuất phát Y của xe theo ID tank cấu hình trong bản đồ.
     *
     * @param tankId   mã số xe tăng
     * @param fallback giá trị dự phòng nếu không tìm thấy
     * @return tọa độ Y (pixel)
     */
    public static double getSpawnY(int tankId, double fallback) {
        JsonObject sp = getSpawnPointJson(tankId);
        return (sp != null && sp.has("y")) ? sp.get("y").getAsDouble() : fallback;
    }

    /**
     * Góc quay ban đầu của xe theo ID tank cấu hình trong bản đồ.
     *
     * @param tankId   mã số xe tăng
     * @param fallback giá trị góc quay dự phòng
     * @return góc quay ban đầu (độ)
     */
    public static double getSpawnAngle(int tankId, double fallback) {
        JsonObject sp = getSpawnPointJson(tankId);
        return (sp != null && sp.has("angle")) ? sp.get("angle").getAsDouble() : fallback;
    }

    /**
     * Khoảng thời gian lộ diện vị trí sau khi bắn trong bụi cỏ (giây).
     *
     * @return thời gian lộ diện
     */
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

    /**
     * Khoảng thời gian định kỳ tự động sinh vật phẩm mới (giây).
     *
     * @return khoảng thời gian sinh item
     */
    public static double getItemSpawnIntervalSeconds() { return numOr(getPowerUpRules(), "spawn_interval_s", 7.0); }

    /**
     * Khoảng thời gian tồn tại của vật phẩm trước khi tự biến mất (giây).
     *
     * @return thời gian tồn tại item
     */
    public static double getItemDespawnSeconds() { return numOr(getPowerUpRules(), "despawn_time_s", 7.0); }

    /**
     * Lượng máu hồi phục khi nhặt túi cứu thương.
     *
     * @return lượng HP hồi
     */
    public static int getHealAmount() { return (int) numOr(getPowerUpRules(), "heal_amount", 3.0); }

    /**
     * Thời gian hiệu lực của buff khiên bảo vệ (giây).
     *
     * @return thời gian duy trì khiên
     */
    public static double getShieldDurationSeconds() { return numOr(getPowerUpRules(), "shield_duration_s", 5.0); }

    /**
     * Thời gian hiệu lực của buff tăng tốc Nitro (giây).
     *
     * @return thời gian duy trì Nitro
     */
    public static double getNitroDurationSeconds() { return numOr(getPowerUpRules(), "nitro_duration_s", 5.0); }

    /**
     * Thời gian hiệu lực tối đa của buff đạn tên lửa (giây).
     *
     * @return thời gian duy trì tên lửa
     */
    public static double getMissileBuffDurationSeconds() { return numOr(getPowerUpRules(), "missile_buff_duration_s", 5.0); }

    /**
     * Hệ số nhân tốc độ khi kích hoạt Nitro.
     *
     * @return hệ số tăng tốc
     */
    public static double getNitroSpeedMultiplier() {
        JsonObject physics = getPhysicsStats();
        return physics.has("nitro_speed_multiplier") ? physics.get("nitro_speed_multiplier").getAsDouble() : 2.0;
    }

    /**
     * Lượng sát thương gây ra bởi đạn tên lửa Rocket.
     *
     * @return sát thương đạn tên lửa
     */
    public static int getRocketBulletDamage() {
        JsonObject damage = getDamageStats();
        return damage.has("rocket_bullet") ? damage.get("rocket_bullet").getAsInt() : 3;
    }

    /**
     * Số cột của lưới bản đồ hiện tại.
     *
     * @return số cột
     */
    public static int getMapCols() {
        return (currentMapConfig != null && currentMapConfig.has("width"))
                ? currentMapConfig.get("width").getAsInt() : 20;
    }

    /**
     * Số hàng của lưới bản đồ hiện tại.
     *
     * @return số hàng
     */
    public static int getMapRows() {
        return (currentMapConfig != null && currentMapConfig.has("height"))
                ? currentMapConfig.get("height").getAsInt() : 20;
    }

    /**
     * Ma trận 2D số nguyên biểu diễn các khối gạch, đá, bụi cỏ của bản đồ.
     *
     * @return mảng 2 chiều mã ô bản đồ
     */
    public static int[][] getMapMatrix() {
        if (currentMapConfig == null || !currentMapConfig.has("matrix")) {
            return new int[0][0];
        }
        JsonArray rows = currentMapConfig.getAsJsonArray("matrix");
        int h = rows.size();
        int w = rows.get(0).getAsJsonArray().size();
        int[][] matrix = new int[h][w];

        for (int r = 0; r < h; r++) {
            JsonArray cols = rows.get(r).getAsJsonArray();
            for (int c = 0; c < w; c++) {
                matrix[r][c] = cols.get(c).getAsInt();
            }
        }
        return matrix;
    }
}