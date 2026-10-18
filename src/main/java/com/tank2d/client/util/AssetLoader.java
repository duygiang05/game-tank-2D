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
        if (path == null || path.isEmpty()) return null;
        if (!imageCache.containsKey(path)) {
            String[] tryPaths = {
                path.startsWith("/") ? path : "/com/tank2d/client/assets/images/" + path,
                "/com/tank2d/client/assets/" + path,
                path.startsWith("/") ? path : "/com/tank2d/client/assets/ui/" + path
            };

            Image loaded = null;
            for (String testPath : tryPaths) {
                try (InputStream is = AssetLoader.class.getResourceAsStream(testPath)) {
                    if (is != null) {
                        loaded = new Image(is);
                        break;
                    }
                } catch (Exception ignored) {}
            }

            if (loaded != null) {
                imageCache.put(path, loaded);
            } else {
                LOGGER.warning("[AssetLoader] Không tìm thấy ảnh: " + path);
            }
        }
        return imageCache.get(path);
    }

    /**
     * Lấy hình ảnh nền chiến trường chất lượng cao theo chủ đề.
     *
     * @param theme tên chủ đề (desert, snow, default)
     */
    public static Image getBattlefieldBackground(String theme) {
        if ("desert".equalsIgnoreCase(theme)) {
            return getImage("battlefield/backgrounds/03_vivid_red_desert_variant.png");
        } else if ("snow".equalsIgnoreCase(theme)) {
            return getImage("battlefield/backgrounds/04_icy_snowfield_variant.png");
        }
        return getImage("battlefield/backgrounds/01_barren_field_variant.png");
    }

    /**
     * Lấy hình ảnh nền chiến trường mặc định.
     */
    public static Image getBattlefieldBackground() {
        return getBattlefieldBackground("default");
    }

    /**
     * Lấy hình ảnh bụi cỏ độ nét cao theo chủ đề bản đồ.
     *
     * @param theme tên chủ đề (desert, snow, default)
     */
    public static Image getBushImage(String theme) {
        if ("desert".equalsIgnoreCase(theme)) {
            return getImage("tiles/bush_desert.png");
        } else if ("snow".equalsIgnoreCase(theme)) {
            return getImage("tiles/bush_snow.png");
        }
        return getImage("tiles/bush_tropical.png");
    }

    /**
     * Lấy banner Chiến thắng hoàng kim.
     */
    public static Image getVictoryBanner() {
        return getImage("banners/banner_victory.png");
    }

    /**
     * Lấy banner Thất bại rực lửa.
     */
    public static Image getDefeatBanner() {
        return getImage("banners/banner_defeat.png");
    }

    /**
     * Lấy banner Hòa trận.
     */
    public static Image getDrawBanner() {
        return getImage("banners/banner_draw.png");
    }

    /**
     * Lấy huy chương hạng 1 (Vàng), 2 (Bạc), 3 (Đồng).
     */
    public static Image getMedalImage(int rank) {
        switch (rank) {
            case 1: return getImage("medals/medal_gold.png");
            case 2: return getImage("medals/medal_silver.png");
            case 3: return getImage("medals/medal_bronze.png");
            default: return null;
        }
    }

    /**
     * Lấy huy hiệu hạng Top 1, 2, 3.
     */
    public static Image getBadgeImage(int rank) {
        switch (rank) {
            case 1: return getImage("medals/badge_gold.png");
            case 2: return getImage("medals/badge_silver.png");
            case 3: return getImage("medals/badge_bronze.png");
            default: return null;
        }
    }

    /**
     * Lấy logo Chiến thắng / Thất bại / Hòa trận chất lượng cao cho màn hình sau trận.
     */
    public static Image getVictoryLogo() {
        return getImage("banners/logo_victory.png");
    }

    public static Image getDefeatLogo() {
        return getImage("banners/logo_defeat.png");
    }

    public static Image getDrawLogo() {
        return getImage("banners/logo_draw.png");
    }

    private static final Image[] EXPLOSION_FRAMES = new Image[5];

    /**
     * Lấy khung hình vụ nổ (0-4) từ asset bộ hiệu ứng neon/arcade.
     */
    public static Image getExplosionFrame(int frameIdx) {
        if (frameIdx < 0) frameIdx = 0;
        int idx = Math.min(frameIdx, 4);
        if (EXPLOSION_FRAMES[idx] == null) {
            EXPLOSION_FRAMES[idx] = getImage("effects/explosion_" + (idx + 1) + ".png");
        }
        return EXPLOSION_FRAMES[idx];
    }

    /**
     * Lấy hiệu ứng nổi khi nhặt vật phẩm.
     */
    public static Image getItemEffectImage(String type) {
        if (type == null) return null;
        switch (type.toUpperCase()) {
            case "HEALTH_PACK": return getImage("effects/heal_pickup_effect.png");
            case "SHIELD": return getImage("effects/shield_overlay_final.png");
            case "NITRO": return getImage("effects/speed_nitro_trail_final.png");
            case "ROCKET_AMMO": return getImage("effects/rocket_pickup_effect.png");
            default: return null;
        }
    }

    /**
     * Lấy hình ảnh tường đá kiên cố theo chủ đề bản đồ (desert, snow, default).
     */
    public static Image getStoneWallImage(String theme) {
        if ("desert".equalsIgnoreCase(theme)) {
            Image img = getImage("battlefield/walls/wall_stone_desert.png");
            if (img != null) return img;
        } else if ("snow".equalsIgnoreCase(theme)) {
            Image img = getImage("battlefield/walls/wall_stone_snow.png");
            if (img != null) return img;
        }
        return getImage("battlefield/walls/wall_stone_square.png");
    }

    /**
     * Lấy hình ảnh tường đá kiên cố mặc định từ bộ Kenney battlefield.
     */
    public static Image getStoneWallImage() {
        return getStoneWallImage("default");
    }

    /**
     * Lấy hình ảnh tường gạch theo mức độ hư hại và chủ đề bản đồ (desert, snow, default).
     */
    public static Image getBrickWallImage(int hp, int maxHp, String theme) {
        String suffix = "";
        if ("desert".equalsIgnoreCase(theme)) {
            suffix = "_desert";
        } else if ("snow".equalsIgnoreCase(theme)) {
            suffix = "_snow";
        }

        if (hp >= 3 || (maxHp <= 2 && hp >= 2)) {
            Image img = !suffix.isEmpty() ? getImage("battlefield/walls/wall_brick_intact" + suffix + ".png") : null;
            return img != null ? img : getImage("battlefield/walls/wall_brick_intact_square.png");
        } else if (hp == 2) {
            Image img = !suffix.isEmpty() ? getImage("battlefield/walls/wall_brick_damaged" + suffix + ".png") : null;
            return img != null ? img : getImage("battlefield/walls/wall_brick_damaged_square.png");
        } else {
            Image img = !suffix.isEmpty() ? getImage("battlefield/walls/wall_brick_ruined" + suffix + ".png") : null;
            return img != null ? img : getImage("battlefield/walls/wall_brick_ruined_square.png");
        }
    }

    /**
     * Lấy hình ảnh tường gạch theo mức độ hư hại mặc định.
     */
    public static Image getBrickWallImage(int hp, int maxHp) {
        return getBrickWallImage(hp, maxHp, "default");
    }

    /**
     * Lấy sprite xe tank chính thức từ Kenney asset pack theo màu.
     */
    public static Image getKenneyTankImage(int colorIdx) {
        String[] tanks = {
            "battlefield/tanks/tank_green.png",
            "battlefield/tanks/tank_red.png",
            "battlefield/tanks/tank_blue.png",
            "battlefield/tanks/tank_sand.png"
        };
        int idx = Math.abs(colorIdx) % tanks.length;
        return getImage(tanks[idx]);
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
            case "ROCKET_AMMO": return getImage("tiles/rocket_ammo.png");
            default: return null;
        }
    }

    /**
     * Lấy icon cài đặt (Settings / Gear icon) từ asset.
     */
    public static Image getSettingsIcon() {
        return getImage("ui/icon_setting.png");
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