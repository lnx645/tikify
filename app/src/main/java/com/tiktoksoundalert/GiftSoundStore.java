package com.tiktoksoundalert;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persists gift-to-sound mappings and custom sound file URIs.
 */
public class GiftSoundStore {
    private static final String PREFS_NAME = "gift_sound_store";
    private static final String KEY_MAPPINGS = "gift_sound_mappings";
    private static final String KEY_CUSTOM_SOUNDS = "custom_sounds";

    private final SharedPreferences prefs;
    private final Map<String, String> mappingsCache;
    private final Map<String, String> customSoundsCache;

    public GiftSoundStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.mappingsCache = loadMappings();
        this.customSoundsCache = loadCustomSounds();
    }

    /**
     * Save a mapping: giftName -> soundKey (a builtin sound key or a custom sound name).
     */
    public void setGiftSound(String giftName, String soundKey) {
        mappingsCache.put(giftName.toLowerCase(), soundKey);
        saveMappings();
    }

    /**
     * Save a mapping keyed by the gift id (numeric, consistent across streams).
     */
    public void setGiftSoundById(long giftId, String soundKey) {
        mappingsCache.put("id:" + giftId, soundKey);
        saveMappings();
    }

    /**
     * Remove a mapping keyed by the gift id.
     */
    public void removeGiftSoundById(long giftId) {
        mappingsCache.remove("id:" + giftId);
        saveMappings();
    }

    /**
     * Remove a gift -> sound mapping.
     */
    public void removeGiftSound(String giftName) {
        mappingsCache.remove(giftName.toLowerCase());
        saveMappings();
    }

    /**
     * Get the sound key for a gift, or null if not mapped.
     */
    public String getSoundForGift(String giftName) {
        return mappingsCache.get(giftName.toLowerCase());
    }

    /**
     * Get the sound key for a gift id, or null if not mapped.
     */
    public String getSoundForGiftId(long giftId) {
        return mappingsCache.get("id:" + giftId);
    }

    public Map<String, String> getAllMappings() {
        return mappingsCache;
    }

    private Map<String, String> loadMappings() {
        Map<String, String> result = new HashMap<>();
        try {
            String raw = prefs.getString(KEY_MAPPINGS, "{}");
            JSONObject json = new JSONObject(raw);
            for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                String key = it.next();
                result.put(key, json.optString(key));
            }
        } catch (Exception e) {
            // corrupted data, start fresh
        }
        return result;
    }

    private void saveMappings() {
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : mappingsCache.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            prefs.edit().putString(KEY_MAPPINGS, json.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Register a custom sound file (copied into app storage) under a name.
     */
    public void setCustomSound(String name, String filePath) {
        customSoundsCache.put(name, filePath);
        saveCustomSounds();
    }

    public Map<String, String> getCustomSounds() {
        return customSoundsCache;
    }

    private Map<String, String> loadCustomSounds() {
        Map<String, String> result = new HashMap<>();
        Set<String> seenPaths = new HashSet<>();
        try {
            String raw = prefs.getString(KEY_CUSTOM_SOUNDS, "{}");
            JSONObject json = new JSONObject(raw);
            for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = json.optString(key);
                // Collapse legacy duplicates ("name" + "name (2)") that point to
                // the same file: keep the first name registered for that file.
                if (value == null || value.isEmpty() || !seenPaths.add(value)) {
                    continue;
                }
                result.put(key, value);
            }
        } catch (Exception e) {
            // fresh start
        }
        return result;
    }

    public void removeCustomSound(String name) {
        customSoundsCache.remove(name);
        saveCustomSounds();
    }

    private void saveCustomSounds() {
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : customSoundsCache.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            prefs.edit().putString(KEY_CUSTOM_SOUNDS, json.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}