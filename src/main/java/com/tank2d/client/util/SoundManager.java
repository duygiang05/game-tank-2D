package com.tank2d.client.util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trình quản lý âm thanh (Sound Manager).
 * Xử lý phát nhạc nền (BGM) lặp vô tận và hiệu ứng âm thanh (SFX) đa luồng.
 */
public class SoundManager {

    private static final Logger LOGGER = Logger.getLogger(SoundManager.class.getName());
    private static final Map<String, URL> soundResourceCache = new HashMap<>();
    private static Clip bgmClip;
    private static boolean isMuted = false;
    private static float sfxVolumeGain = 0.0f;

    /**
     * Nạp sẵn tất cả file âm thanh hiệu ứng và nhạc nền vào bộ nhớ đệm URL.
     */
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
        loadSound("bgm", "sounds/bgm.wav");
    }

    /**
     * Nạp URL tài nguyên âm thanh theo khóa định danh.
     *
     * @param key khóa âm thanh
     * @param path đường dẫn tài nguyên
     */
    private static void loadSound(String key, String path) {
        try {
            URL url = SoundManager.class.getResource("/com/tank2d/client/assets/" + path);
            if (url != null) {
                soundResourceCache.put(key, url);
            } else {
                LOGGER.warning("[SoundManager] Không tìm thấy file âm thanh: " + path);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[SoundManager] Lỗi load sound " + key, e);
        }
    }

    /**
     * Chuyển đổi định dạng audio sang PCM_SIGNED 16-bit nếu cần thiết để Java Sound phát được.
     *
     * @param audioStream luồng audio nguồn
     * @return luồng audio đã chuyển đổi hoặc nguyên bản
     */
    private static AudioInputStream convertToSupportedFormat(AudioInputStream audioStream) {
        AudioFormat baseFormat = audioStream.getFormat();
        if (baseFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false
            );
            if (AudioSystem.isConversionSupported(targetFormat, baseFormat)) {
                return AudioSystem.getAudioInputStream(targetFormat, audioStream);
            }
        }
        return audioStream;
    }

    /**
     * Phát hiệu ứng âm thanh (SFX) trên một luồng nền riêng biệt để không ảnh hưởng FPS hiển thị.
     *
     * @param key khóa định danh âm thanh cần phát
     */
    public static void playSound(String key) {
        if (isMuted) return;
        
        URL url = soundResourceCache.get(key);
        if (url == null) return;

        new Thread(() -> {
            try (InputStream is = url.openStream();
                 InputStream bufferedIn = new BufferedInputStream(is);
                 AudioInputStream rawStream = AudioSystem.getAudioInputStream(bufferedIn);
                 AudioInputStream audioStream = convertToSupportedFormat(rawStream)) {

                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);

                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gainControl.setValue(sfxVolumeGain);
                }

                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });

                clip.start();

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[SoundManager] Lỗi phát âm thanh " + key, e);
            }
        }).start();
    }

    /**
     * Phát nhạc nền (BGM) ở chế độ lặp vô tận.
     */
    public static void playBGM() {
        if (isMuted) return;
        URL url = soundResourceCache.get("bgm");
        if (url == null) return;

        new Thread(() -> {
            try {
                if (bgmClip != null && bgmClip.isRunning()) return;

                InputStream is = url.openStream();
                InputStream bufferedIn = new BufferedInputStream(is);
                AudioInputStream rawStream = AudioSystem.getAudioInputStream(bufferedIn);
                AudioInputStream audioStream = convertToSupportedFormat(rawStream);

                bgmClip = AudioSystem.getClip();
                bgmClip.open(audioStream);
                bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
                bgmClip.start();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[SoundManager] Lỗi phát BGM", e);
            }
        }).start();
    }

    /**
     * Dừng phát nhạc nền và giải phóng Clip.
     */
    public static void stopBGM() {
        if (bgmClip != null) {
            if (bgmClip.isRunning()) {
                bgmClip.stop();
            }
            bgmClip.close();
            bgmClip = null;
        }
    }

    /**
     * Phát âm thanh nhặt vật phẩm tương ứng theo loại vật phẩm.
     *
     * @param itemType tên loại vật phẩm
     */
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

    /**
     * Bật hoặc tắt âm thanh toàn bộ trò chơi.
     *
     * @param muted true nếu tắt tiếng, false nếu bật tiếng
     */
    public static void setMuted(boolean muted) {
        isMuted = muted;
        if (muted) stopBGM();
        else playBGM();
    }
}