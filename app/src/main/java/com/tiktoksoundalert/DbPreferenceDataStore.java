package com.tiktoksoundalert;

import android.content.Context;

import androidx.preference.PreferenceDataStore;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bridges androidx.preference widgets to the per-account Room settings store,
 * so every preference save is persisted with the active account id and mirrored
 * to the running service.
 */
public class DbPreferenceDataStore extends PreferenceDataStore {

    private final Context appContext;
    private final long accountId;
    private final Map<String, String> cache;

    public DbPreferenceDataStore(Context context, long accountId) {
        this.appContext = context.getApplicationContext();
        this.accountId = accountId;
        this.cache = new java.util.HashMap<>(SettingsRepository.load(appContext, accountId));
    }

    private String get(String key) {
        return cache.get(key);
    }

    private void put(String key, String value) {
        cache.put(key, value);
        SettingsRepository.put(appContext, accountId, key, value);
    }

    @Override
    public String getString(String key, String defValue) {
        return cache.containsKey(key) ? cache.get(key) : defValue;
    }

    @Override
    public void putString(String key, String value) {
        put(key, value == null ? "" : value);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        if (!cache.containsKey(key)) return defValues;
        String raw = cache.get(key);
        if (raw == null || raw.isEmpty()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(raw.split("\u0001")));
    }

    @Override
    public void putStringSet(String key, Set<String> values) {
        put(key, String.join("\u0001", values));
    }

    @Override
    public int getInt(String key, int defValue) {
        String v = get(key);
        if (v == null) return defValue;
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return defValue;
        }
    }

    @Override
    public void putInt(String key, int value) {
        put(key, String.valueOf(value));
    }

    @Override
    public long getLong(String key, long defValue) {
        String v = get(key);
        if (v == null) return defValue;
        try {
            return Long.parseLong(v);
        } catch (Exception e) {
            return defValue;
        }
    }

    @Override
    public void putLong(String key, long value) {
        put(key, String.valueOf(value));
    }

    @Override
    public float getFloat(String key, float defValue) {
        String v = get(key);
        if (v == null) return defValue;
        try {
            return Float.parseFloat(v);
        } catch (Exception e) {
            return defValue;
        }
    }

    @Override
    public void putFloat(String key, float value) {
        put(key, String.valueOf(value));
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        String v = get(key);
        if (v == null) return defValue;
        return Boolean.parseBoolean(v);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        put(key, value ? "true" : "false");
    }
}