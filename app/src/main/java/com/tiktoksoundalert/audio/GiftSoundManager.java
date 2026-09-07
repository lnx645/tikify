package com.tiktoksoundalert.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class GiftSoundManager {
    private static final String TAG = "GiftSoundManager";

    private SoundPool soundPool;
    private final Context context;
    private final Map<String, Integer> soundMap;
    private boolean isLoaded = false;

    // Built-in sounds (res/raw)
    private static final String[] BUILTIN_SOUNDS = {
            "ding", "cheer", "airhorn", "boom", "coin",
            "fanfare", "harp", "levelup", "pop", "sparkle",
            "drumroll", "trumpet", "woohoo", "wow", "superchat"
    };

    public interface OnSoundLoadedListener {
        void onSoundLoaded();
        void onSoundError(String error);
    }

    private OnSoundLoadedListener listener;

    public GiftSoundManager(Context context) {
        this.context = context;
        this.soundMap = new HashMap<>();
        initSoundPool();
    }

    private void initSoundPool() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(5)
                    .setAudioAttributes(attributes)
                    .build();
        } else {
            soundPool = new SoundPool(5, AudioManager.STREAM_NOTIFICATION, 0);
        }

        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            isLoaded = true;
            if (listener != null) {
                listener.onSoundLoaded();
            }
        });
    }

    public void setOnSoundLoadedListener(OnSoundLoadedListener listener) {
        this.listener = listener;
    }

    public void loadBuiltinSounds() {
        for (String sound : BUILTIN_SOUNDS) {
            int resId = context.getResources().getIdentifier(sound, "raw", context.getPackageName());
            if (resId != 0) {
                int soundId = soundPool.load(context, resId, 1);
                soundMap.put(sound, soundId);
                Log.d(TAG, "Loaded builtin sound: " + sound);
            }
        }
    }

    public void loadCustomSound(String name, Uri uri) {
        try {
            File tempFile = new File(context.getCacheDir(), name + ".mp3");
            try (InputStream is = context.getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            loadCustomSoundFromFile(name, tempFile);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load custom sound: " + name, e);
            if (listener != null) {
                listener.onSoundError("Failed to load: " + e.getMessage());
            }
        }
    }

    public void loadCustomSoundFromPath(String name, String path) {
        loadCustomSoundFromFile(name, new File(path));
    }

    private void loadCustomSoundFromFile(String name, File file) {
        if (!file.exists()) {
            Log.w(TAG, "Sound file does not exist: " + name);
            return;
        }
        int soundId = soundPool.load(file.getAbsolutePath(), 1);
        soundMap.put(name, soundId);
        Log.d(TAG, "Loaded custom sound: " + name);
    }

    public void playGiftSound(String giftName, int volumePercent) {
        if (!isLoaded) {
            Log.w(TAG, "Sound pool not loaded yet");
            return;
        }

        Integer soundId = soundMap.get(giftName);
        if (soundId == null) {
            soundId = soundMap.get("ding"); // default fallback
        }
        if (soundId == null) {
            Log.w(TAG, "No sound found for: " + giftName);
            return;
        }

        reallyPlay(soundId, volumePercent, giftName);
        Log.d(TAG, "Playing sound for gift: " + giftName + " at volume " + volumePercent + "%");
    }

    public void playSound(String soundName, int volumePercent) {
        if (!isLoaded) return;

        Integer soundId = soundMap.get(soundName);
        if (soundId == null) {
            Log.w(TAG, "Sound not found: " + soundName);
            return;
        }

        reallyPlay(soundId, volumePercent, soundName);
    }

    /**
     * Plays a sound through the shared coordinator so the speech lane can
     * hold off / pause while the effect rings (see AudioMixCoordinator).
     */
    private void reallyPlay(int soundId, int volumePercent, String soundName) {
        long durationMs = ISOLATED_DURATIONS.getOrDefault(soundName, 1500L);
        AudioMixCoordinator.get().onAlertEffect(durationMs);
        float vol = volumePercent / 100f;
        soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
    }

    /** Expected playback length (ms) per built-in sound, used to size the gate. */
    private static final java.util.Map<String, Long> ISOLATED_DURATIONS =
            new java.util.HashMap<String, Long>() {{
                put("ding", 700L);
                put("cheer", 1800L);
                put("airhorn", 2400L);
                put("boom", 1500L);
                put("coin", 600L);
                put("fanfare", 1800L);
                put("harp", 1800L);
                put("levelup", 1500L);
                put("pop", 500L);
                put("sparkle", 1500L);
                put("drumroll", 2000L);
                put("trumpet", 2000L);
                put("woohoo", 1200L);
                put("wow", 900L);
                put("superchat", 1600L);
            }};

    /**
     * Expected playback length (ms) for a sound key. Built-in keys use the
     * known rough duration; custom sounds are measured with a MediaPlayer
     * (blocking — call from a background thread). Falls back to the default
     * effect window when the duration cannot be read.
     */
    public static long durationOf(String soundKey, Context context) {
        Long builtin = ISOLATED_DURATIONS.get(soundKey);
        if (builtin != null) {
            return builtin;
        }
        if (soundKey == null || context == null) {
            return AudioMixCoordinator.defaultEffectMillis();
        }
        try {
            com.tiktoksoundalert.GiftSoundStore store =
                    new com.tiktoksoundalert.GiftSoundStore(context);
            String path = store.getCustomSounds().get(soundKey);
            if (path == null) {
                return AudioMixCoordinator.defaultEffectMillis();
            }
            MediaPlayer player = new MediaPlayer();
            try {
                player.setDataSource(path);
                player.prepare();
                int ms = player.getDuration();
                return ms > 0 ? ms : AudioMixCoordinator.defaultEffectMillis();
            } finally {
                try {
                    player.release();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "durationOf failed for " + soundKey + ": " + e.getMessage());
            return AudioMixCoordinator.defaultEffectMillis();
        }
    }

    public String[] getAvailableBuiltinSounds() {
        return BUILTIN_SOUNDS;
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundMap.clear();
    }
}
