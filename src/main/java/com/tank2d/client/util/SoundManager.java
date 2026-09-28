package com.tank2d.client.util;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class SoundManager {

    private static final Map<String, URL> soundResourceCache = new HashMap<>();
    private static Clip bgmClip;
    private static boolean isMuted = false;
    private static float sfxVolumeGain = 0.0f; // dB (0 = nguyên bản, -10.0f = nhỏ đi)

    public static void init() {
        loadSound("shoot_normal", "sounds/shoot_normal.wav");
        loadSound("shoot_rocket", "sounds/shoot_rocket.wav");
        loadSound("wall_break", "sounds/wall_break.wav");
        loadSound("explosion", "sounds/explosion.wav");
        loadSound("item_health", "sounds/item_health.wav");
        loadSound("item_shield", "sounds/item_shield.wav");
        loadSound("item_nitro", "sounds/item_nitro.wav");
        loadSound("item_rocket", "sounds/item_rocket.wav");
        loadSound("game_over", "sounds/game_over.wav");
        
        // Lưu URL nhạc nền (khuyến khích dùng file .wav cho BGM)
        loadSound("bgm", "sounds/bgm.wav");
    }

    private static void loadSound(String key, String path) {
        try {
            URL url = SoundManager.class.getResource("/com/tank2d/client/assets/" + path);
            if (url != null) {
                soundResourceCache.put(key, url);
            } else {
                System.err.println("[SoundManager] Không tìm thấy file âm thanh: " + path);
            }
        } catch (Exception e) {
            System.err.println("[SoundManager] Lỗi load sound " + key + ": " + e.getMessage());
        }
    }

    // Phát hiệu ứng âm thanh (SFX) đa luồng không gây giật lag
    public static void playSound(String key) {
        if (isMuted) return;
        
        URL url = soundResourceCache.get(key);
        if (url == null) return;

        // Chạy trên luồng riêng để đảm bảo Render 60 FPS không bị ảnh hưởng
        new Thread(() -> {
            try (InputStream is = url.openStream();
                 InputStream bufferedIn = new BufferedInputStream(is);
                 AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn)) {

                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);

                // Chỉnh âm lượng nếu hỗ trợ
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gainControl.setValue(sfxVolumeGain);
                }

                clip.start();
                // Tự động giải phóng tài nguyên khi phát xong
                clip.drain();

            } catch (Exception e) {
                System.err.println("[SoundManager] Lỗi phát âm thanh " + key + ": " + e.getMessage());
            }
        }).start();
    }

    // Quản lý BGM Nhạc nền
    public static void playBGM() {
        if (isMuted) return;
        URL url = soundResourceCache.get("bgm");
        if (url == null) return;

        new Thread(() -> {
            try {
                if (bgmClip != null && bgmClip.isRunning()) return;

                InputStream is = url.openStream();
                InputStream bufferedIn = new BufferedInputStream(is);
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);

                bgmClip = AudioSystem.getClip();
                bgmClip.open(audioStream);
                
                // Lặp vô tận
                bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
                bgmClip.start();
            } catch (Exception e) {
                System.err.println("[SoundManager] Lỗi phát BGM: " + e.getMessage());
            }
        }).start();
    }

    public static void stopBGM() {
        if (bgmClip != null) {
            if (bgmClip.isRunning()) {
                bgmClip.stop();
            }
            bgmClip.close();
            bgmClip = null;
        }
    }

    public static void playItemSound(String itemType) {
        if (itemType == null) return;
        switch (itemType.toUpperCase()) {
            case "HEALTH_PACK" -> playSound("item_health");
            case "SHIELD" -> playSound("item_shield");
            case "NITRO" -> playSound("item_nitro");
            case "ROCKET_AMMO" -> playSound("item_rocket");
            default -> playSound("item_health");
        }
    }

    public static void setMuted(boolean muted) {
        isMuted = muted;
        if (muted) stopBGM();
        else playBGM();
    }
}