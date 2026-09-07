package com.tiktoksoundalert;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.tiktoksoundalert.db.AccountSetting;
import com.tiktoksoundalert.db.AccountSettingsDao;
import com.tiktoksoundalert.db.AppDatabase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-account settings persisted in the Room database.
 * Reads/writes are keyed by account id (0 = guest/default, used when no account is active).
 * Every write notifies the running service through a LocalBroadcast so live
 * toggles (TTS / gift sound) take effect immediately.
 */
public final class SettingsRepository {

    public static final String ACTION_SETTINGS_CHANGED = "com.tiktoksoundalert.SETTINGS_CHANGED";
    public static final String EXTRA_ACCOUNT_ID = "account_id";

    // Canonical keys. The TTS filter keys intentionally match the preference XML.
    public static final String KEY_TTS_ENABLED = "tts_enabled";
    public static final String KEY_TTS_LANGUAGE = "tts_language";
    public static final String KEY_TTS_SPEED = "tts_speed";
    public static final String KEY_TTS_PITCH = "tts_pitch";
    public static final String KEY_TTS_FORMAT = "tts_format";
    public static final String KEY_TTS_ANNOUNCE_USERNAME = "tts_announce_username";
    public static final String KEY_TTS_MIN_LENGTH = "tts_min_length";
    public static final String KEY_TTS_MAX_LENGTH = "tts_max_length";
    public static final String KEY_TTS_BLOCKED_WORDS = "tts_blocked_words";
    public static final String KEY_TTS_USERNAME_FORMAT = "tts_username_format";

    // Advanced chat TTS
    public static final String KEY_TTS_VOLUME = "tts_chat_volume";
    public static final String KEY_TTS_COMMAND = "tts_command";
    public static final String KEY_TTS_COOLDOWN_MS = "tts_cooldown_ms";
    public static final String KEY_TTS_MAX_QUEUE = "tts_max_queue";
    public static final String KEY_TTS_LETTER_SPAM = "tts_letter_spam";
    public static final String KEY_TTS_PRIORITY_WORDS = "tts_priority_words";
    public static final String KEY_TTS_TEMPLATE = "tts_template";
    public static final String KEY_TTS_ALLOWED_USERS = "tts_allowed_users";
    public static final String KEY_TTS_FAVORITE_USERS = "tts_favorite_users";
    public static final String KEY_TTS_ONCE_PER_USER = "tts_once_per_user";

    // Gift TTS
    public static final String KEY_GIFT_TTS_ENABLED = "tts_gift_enabled";
    public static final String KEY_GIFT_TTS_SPEED = "tts_gift_speed";
    public static final String KEY_GIFT_TTS_PITCH = "tts_gift_pitch";
    public static final String KEY_GIFT_TTS_VOLUME = "tts_gift_volume";
    public static final String KEY_GIFT_TTS_TEMPLATE = "tts_gift_template";
    public static final String KEY_GIFT_TTS_MIN_DIAMONDS = "tts_gift_min_diamonds";
    public static final String KEY_GIFT_TTS_COOLDOWN_MS = "tts_gift_cooldown_ms";

    public static final String KEY_GIFT_SOUND_ENABLED = "gift_sound_enabled";
    public static final String KEY_GIFT_DEFAULT_SOUND = "gift_default_sound";
    public static final String KEY_GIFT_VOLUME = "gift_volume";
    public static final String KEY_GIFT_SOUND_ALL_GIFTS = "gift_sound_all_gifts";

    public static final String KEY_FOLLOW_SOUND_ENABLED = "follow_sound_enabled";
    public static final String KEY_SUBSCRIBE_SOUND_ENABLED = "subscribe_sound_enabled";
    public static final String KEY_JOIN_SOUND_ENABLED = "join_sound_enabled";

    // Alert sound queue (by event). Rules are stored as a JSON array of
    // AlertEventRule objects under KEY_ALERT_QUEUE_RULES.
    public static final String KEY_ALERT_QUEUE_ENABLED = "alert_queue_enabled";
    public static final String KEY_ALERT_QUEUE_RULES = "alert_queue_rules";
    public static final String KEY_ALERT_INTERRUPTS_TTS = "alert_interrupts_tts";

    // Alert queue delay: serialize alert sounds with a gap between them.
    // KEY_ALERT_DELAY_MODE is "fixed" (seconds) or "duration" (next alert starts
    // right when the previous audio finishes).
    public static final String KEY_ALERT_DELAY_ENABLED = "alert_delay_enabled";
    public static final String KEY_ALERT_DELAY_MODE = "alert_delay_mode";
    public static final String KEY_ALERT_DELAY_SECONDS = "alert_delay_seconds";

    public static final String KEY_LOG_MAX_SIZE = "log_max_size";
    public static final String KEY_SHOW_NOTIFICATIONS = "show_notifications";

    private SettingsRepository() {
    }

    public static Map<String, String> load(Context context, long accountId) {
        Map<String, String> map = new HashMap<>();
        List<AccountSetting> rows = AppDatabase.get(context)
                .accountSettingsDao().getAll(accountId);
        for (AccountSetting row : rows) {
            map.put(row.key, row.value);
        }
        return map;
    }

    public static void put(Context context, long accountId, String key, String value) {
        if (accountId == 0L) {
            return; // guest (no account) settings are not persisted
        }
        AppDatabase.get(context).accountSettingsDao()
                .put(new AccountSetting(accountId, key, value));

        Intent intent = new Intent(ACTION_SETTINGS_CHANGED);
        intent.putExtra(EXTRA_ACCOUNT_ID, accountId);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public static void clear(Context context, long accountId) {
        AppDatabase.get(context).accountSettingsDao().clearForAccount(accountId);
    }

    public static long activeAccountId(Context context) {
        com.tiktoksoundalert.db.Account active = AppDatabase.get(context)
                .accountDao().getActiveNow();
        return active != null ? active.id : 0L;
    }
}