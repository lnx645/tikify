package com.tiktoksoundalert;

import android.content.Context;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Account-scoped settings backed by the Room database (see SettingsRepository).
 * Reads come from an in-memory snapshot taken at construction; writes persist to
 * the DB and notify the running service through a LocalBroadcast.
 */
public class SettingsManager {
    /** Account whose settings this manager reads/writes. */
    public final long accountId;

    private final Context appContext;
    private final Map<String, String> values;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    private final java.lang.reflect.Type rulesListType =
            new com.google.gson.reflect.TypeToken<java.util.List<com.tiktoksoundalert.models.AlertEventRule>>() {
            }.getType();

    /** Raw JSON snapshot backing {@link #cachedRules}; null = cache invalid. */
    private String cachedRulesRaw;
    private java.util.List<com.tiktoksoundalert.models.AlertEventRule> cachedRules;

    public SettingsManager(Context context, long accountId) {
        this.appContext = context.getApplicationContext();
        this.accountId = accountId;
        this.values = new java.util.HashMap<>(SettingsRepository.load(appContext, accountId));
    }

    public void reload() {
        values.clear();
        values.putAll(SettingsRepository.load(appContext, accountId));
        cachedRulesRaw = null;
        cachedRules = null;
    }

    private String get(String key) {
        return values.get(key);
    }

    private void set(String key, String value) {
        values.put(key, value);
        SettingsRepository.put(appContext, accountId, key, value);
    }

    private void set(String key, boolean value) {
        set(key, String.valueOf(value));
    }

    private boolean getBool(String key, boolean def) {
        String v = get(key);
        if (v == null) return def;
        try {
            return Boolean.parseBoolean(v);
        } catch (Exception e) {
            return def;
        }
    }

    public int getInt(String key, int def) {
        String v = get(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return def;
        }
    }

    private String getStr(String key, String def) {
        String v = get(key);
        return v != null ? v : def;
    }

    // TTS Settings
    public boolean isTtsEnabled() { return getBool(SettingsRepository.KEY_TTS_ENABLED, true); }
    public void setTtsEnabled(boolean enabled) { set(SettingsRepository.KEY_TTS_ENABLED, enabled); }

    public String getTtsLanguage() { return getStr(SettingsRepository.KEY_TTS_LANGUAGE, "id"); }
    public void setTtsLanguage(String lang) { set(SettingsRepository.KEY_TTS_LANGUAGE, lang); }

    public float getTtsSpeed() {
        // stored as int 1-20 (SeekBar); convert to 0.1-2.0
        return Math.max(0.1f, getInt(SettingsRepository.KEY_TTS_SPEED, 10) / 10f);
    }
    public void setTtsSpeed(float speed) {
        set(SettingsRepository.KEY_TTS_SPEED, String.valueOf(Math.round(speed * 10)));
    }

    public float getTtsPitch() {
        return Math.max(0.1f, getInt(SettingsRepository.KEY_TTS_PITCH, 10) / 10f);
    }
    public void setTtsPitch(float pitch) {
        set(SettingsRepository.KEY_TTS_PITCH, String.valueOf(Math.round(pitch * 10)));
    }

    public String getTtsFormat() { return getStr(SettingsRepository.KEY_TTS_FORMAT, "%s says %s"); }
    public void setTtsFormat(String format) { set(SettingsRepository.KEY_TTS_FORMAT, format); }

    public boolean isTtsAnnounceUsername() { return getBool(SettingsRepository.KEY_TTS_ANNOUNCE_USERNAME, true); }
    public void setTtsAnnounceUsername(boolean announce) { set(SettingsRepository.KEY_TTS_ANNOUNCE_USERNAME, announce); }

    // Gift Sound Settings
    public boolean isGiftSoundEnabled() { return getBool(SettingsRepository.KEY_GIFT_SOUND_ENABLED, true); }
    public void setGiftSoundEnabled(boolean enabled) { set(SettingsRepository.KEY_GIFT_SOUND_ENABLED, enabled); }

    public String getDefaultGiftSound() { return getStr(SettingsRepository.KEY_GIFT_DEFAULT_SOUND, "ding"); }
    public void setDefaultGiftSound(String sound) { set(SettingsRepository.KEY_GIFT_DEFAULT_SOUND, sound); }

    public int getGiftVolume() { return getInt(SettingsRepository.KEY_GIFT_VOLUME, 80); }
    public void setGiftVolume(int volume) { set(SettingsRepository.KEY_GIFT_VOLUME, String.valueOf(volume)); }

    public boolean isGiftSoundForAllGifts() { return getBool(SettingsRepository.KEY_GIFT_SOUND_ALL_GIFTS, true); }
    public void setGiftSoundForAllGifts(boolean all) { set(SettingsRepository.KEY_GIFT_SOUND_ALL_GIFTS, all); }

    // Comment TTS Settings
    public int getCommentTtsMinLength() { return getInt(SettingsRepository.KEY_TTS_MIN_LENGTH, 0); }
    public void setCommentTtsMinLength(int len) { set(SettingsRepository.KEY_TTS_MIN_LENGTH, String.valueOf(len)); }

    public int getCommentTtsMaxLength() { return getInt(SettingsRepository.KEY_TTS_MAX_LENGTH, 200); }
    public void setCommentTtsMaxLength(int len) { set(SettingsRepository.KEY_TTS_MAX_LENGTH, String.valueOf(len)); }

    public Set<String> getBlockedWords() {
        return getCsv(SettingsRepository.KEY_TTS_BLOCKED_WORDS);
    }
    public void setBlockedWords(Set<String> words) {
        setCsv(SettingsRepository.KEY_TTS_BLOCKED_WORDS, words);
    }

    private Set<String> getCsv(String key) {
        Set<String> result = new HashSet<>();
        String raw = get(key);
        if (raw != null && !raw.trim().isEmpty()) {
            String[] words = raw.split(",");
            for (String word : words) {
                if (!word.trim().isEmpty()) {
                    result.add(word.trim().toLowerCase());
                }
            }
        }
        return result;
    }

    private void setCsv(String key, Set<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            String t = v.trim().toLowerCase();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        set(key, sb.toString());
    }

    // Advanced chat TTS
    public int getChatTtsVolume() { return getInt(SettingsRepository.KEY_TTS_VOLUME, 100); }
    public void setChatTtsVolume(int volume) { set(SettingsRepository.KEY_TTS_VOLUME, String.valueOf(volume)); }

    public String getTtsCommand() { return getStr(SettingsRepository.KEY_TTS_COMMAND, ""); }
    public void setTtsCommand(String command) { set(SettingsRepository.KEY_TTS_COMMAND, command); }

    /** Custom rule triggers: comments are matched against these words per the mode. */
    public Set<String> getTriggerWords() { return getCsv(SettingsRepository.KEY_TTS_TRIGGERS); }
    public void setTriggerWords(Set<String> words) { setCsv(SettingsRepository.KEY_TTS_TRIGGERS, words); }

    // Trigger matching modes: exact / starts / contains / ends / word / regex
    public static final String TRIGGER_MODE_EXACT = "exact";
    public static final String TRIGGER_MODE_STARTS = "starts";
    public static final String TRIGGER_MODE_CONTAINS = "contains";
    public static final String TRIGGER_MODE_ENDS = "ends";
    public static final String TRIGGER_MODE_WORD = "word";
    public static final String TRIGGER_MODE_REGEX = "regex";

    public String getTriggerMatchMode() {
        return getStr(SettingsRepository.KEY_TTS_TRIGGER_MODE, TRIGGER_MODE_STARTS);
    }
    public void setTriggerMatchMode(String mode) {
        set(SettingsRepository.KEY_TTS_TRIGGER_MODE, mode);
    }

    public boolean isTriggerEnabled() { return getBool(SettingsRepository.KEY_TTS_TRIGGER_ENABLED, true); }
    public void setTriggerEnabled(boolean enabled) { set(SettingsRepository.KEY_TTS_TRIGGER_ENABLED, enabled); }

    public int getTtsCooldownMs() { return getInt(SettingsRepository.KEY_TTS_COOLDOWN_MS, 0); }
    public void setTtsCooldownMs(int ms) { set(SettingsRepository.KEY_TTS_COOLDOWN_MS, String.valueOf(ms)); }

    public int getTtsMaxQueue() { return getInt(SettingsRepository.KEY_TTS_MAX_QUEUE, 10); }
    public void setTtsMaxQueue(int max) { set(SettingsRepository.KEY_TTS_MAX_QUEUE, String.valueOf(max)); }

    public boolean isLetterSpamBlocked() { return getBool(SettingsRepository.KEY_TTS_LETTER_SPAM, false); }
    public void setLetterSpamBlocked(boolean block) { set(SettingsRepository.KEY_TTS_LETTER_SPAM, block); }

    /** Read comments that are empty or have no meaningful text (e.g. ".", emoji). */
    public boolean isAllowEmptyComments() { return getBool(SettingsRepository.KEY_TTS_ALLOW_EMPTY, false); }
    public void setAllowEmptyComments(boolean allow) { set(SettingsRepository.KEY_TTS_ALLOW_EMPTY, allow); }

    public Set<String> getPriorityWords() { return getCsv(SettingsRepository.KEY_TTS_PRIORITY_WORDS); }
    public void setPriorityWords(Set<String> words) { setCsv(SettingsRepository.KEY_TTS_PRIORITY_WORDS, words); }

    public String getTtsTemplate() { return getStr(SettingsRepository.KEY_TTS_TEMPLATE, "{username} Berkomentar {comment}"); }
    public void setTtsTemplate(String template) { set(SettingsRepository.KEY_TTS_TEMPLATE, template); }

    public String getAllowedUsersMode() { return getStr(SettingsRepository.KEY_TTS_ALLOWED_USERS, "all"); }
    public void setAllowedUsersMode(String mode) { set(SettingsRepository.KEY_TTS_ALLOWED_USERS, mode); }

    public Set<String> getFavoriteUsers() { return getCsv(SettingsRepository.KEY_TTS_FAVORITE_USERS); }
    public void setFavoriteUsers(Set<String> users) { setCsv(SettingsRepository.KEY_TTS_FAVORITE_USERS, users); }

    public boolean isOncePerUserEnabled() { return getBool(SettingsRepository.KEY_TTS_ONCE_PER_USER, false); }
    public void setOncePerUserEnabled(boolean once) { set(SettingsRepository.KEY_TTS_ONCE_PER_USER, once); }

    // Gift TTS
    public boolean isGiftTtsEnabled() { return getBool(SettingsRepository.KEY_GIFT_TTS_ENABLED, true); }
    public void setGiftTtsEnabled(boolean enabled) { set(SettingsRepository.KEY_GIFT_TTS_ENABLED, enabled); }

    public float getGiftTtsSpeed() { return Math.max(0.1f, getInt(SettingsRepository.KEY_GIFT_TTS_SPEED, 10) / 10f); }
    public void setGiftTtsSpeed(float speed) { set(SettingsRepository.KEY_GIFT_TTS_SPEED, String.valueOf(Math.round(speed * 10))); }

    public float getGiftTtsPitch() { return Math.max(0.1f, getInt(SettingsRepository.KEY_GIFT_TTS_PITCH, 10) / 10f); }
    public void setGiftTtsPitch(float pitch) { set(SettingsRepository.KEY_GIFT_TTS_PITCH, String.valueOf(Math.round(pitch * 10))); }

    public int getGiftTtsVolume() { return getInt(SettingsRepository.KEY_GIFT_TTS_VOLUME, 100); }
    public void setGiftTtsVolume(int volume) { set(SettingsRepository.KEY_GIFT_TTS_VOLUME, String.valueOf(volume)); }

    public String getGiftTtsTemplate() { return getStr(SettingsRepository.KEY_GIFT_TTS_TEMPLATE, "{username} Mengirim {giftname}"); }
    public void setGiftTtsTemplate(String template) { set(SettingsRepository.KEY_GIFT_TTS_TEMPLATE, template); }

    public int getGiftTtsMinDiamonds() { return getInt(SettingsRepository.KEY_GIFT_TTS_MIN_DIAMONDS, 1); }
    public void setGiftTtsMinDiamonds(int min) { set(SettingsRepository.KEY_GIFT_TTS_MIN_DIAMONDS, String.valueOf(min)); }

    public int getGiftTtsCooldownMs() { return getInt(SettingsRepository.KEY_GIFT_TTS_COOLDOWN_MS, 0); }
    public void setGiftTtsCooldownMs(int ms) { set(SettingsRepository.KEY_GIFT_TTS_COOLDOWN_MS, String.valueOf(ms)); }

    public String getUsernameFormat() { return getStr(SettingsRepository.KEY_TTS_USERNAME_FORMAT, "%s says: "); }
    public void setUsernameFormat(String format) { set(SettingsRepository.KEY_TTS_USERNAME_FORMAT, format); }

    // Other Alert Sounds
    public boolean isFollowSoundEnabled() { return getBool(SettingsRepository.KEY_FOLLOW_SOUND_ENABLED, true); }
    public void setFollowSoundEnabled(boolean enabled) { set(SettingsRepository.KEY_FOLLOW_SOUND_ENABLED, enabled); }

    public boolean isSubscribeSoundEnabled() { return getBool(SettingsRepository.KEY_SUBSCRIBE_SOUND_ENABLED, true); }
    public void setSubscribeSoundEnabled(boolean enabled) { set(SettingsRepository.KEY_SUBSCRIBE_SOUND_ENABLED, enabled); }

    public boolean isJoinSoundEnabled() { return getBool(SettingsRepository.KEY_JOIN_SOUND_ENABLED, false); }
    public void setJoinSoundEnabled(boolean enabled) { set(SettingsRepository.KEY_JOIN_SOUND_ENABLED, enabled); }

    // Alert sound queue (by event)
    public boolean isAlertQueueEnabled() { return getBool(SettingsRepository.KEY_ALERT_QUEUE_ENABLED, true); }
    public void setAlertQueueEnabled(boolean enabled) { set(SettingsRepository.KEY_ALERT_QUEUE_ENABLED, enabled); }

    /** All configured alert rules (empty list when none saved). */
    public java.util.List<com.tiktoksoundalert.models.AlertEventRule> getAlertQueueRules() {
        String raw = get(SettingsRepository.KEY_ALERT_QUEUE_RULES);
        if (raw == null || raw.trim().isEmpty()) {
            cachedRulesRaw = null;
            cachedRules = null;
            return new java.util.ArrayList<>();
        }
        if (cachedRules == null || cachedRulesRaw == null || !cachedRulesRaw.equals(raw)) {
            try {
                cachedRules = gson.fromJson(raw, rulesListType);
            } catch (Exception e) {
                cachedRules = new java.util.ArrayList<>();
            }
            cachedRulesRaw = raw;
        }
        return new java.util.ArrayList<>(cachedRules);
    }

    public void setAlertQueueRules(java.util.List<com.tiktoksoundalert.models.AlertEventRule> rules) {
        if (rules == null) {
            rules = new java.util.ArrayList<>();
        }
        cachedRules = new java.util.ArrayList<>(rules);
        cachedRulesRaw = gson.toJson(cachedRules);
        set(SettingsRepository.KEY_ALERT_QUEUE_RULES, cachedRulesRaw);
    }

    /** Alert effect interrupts in-flight TTS, which resumes when it ends. */
    public boolean isAlertInterruptsTts() {
        return getBool(SettingsRepository.KEY_ALERT_INTERRUPTS_TTS, true);
    }

    public void setAlertInterruptsTts(boolean interrupts) {
        set(SettingsRepository.KEY_ALERT_INTERRUPTS_TTS, interrupts);
    }

    // Alert queue delay ("fixed" seconds or "duration" = wait until the audio ends)
    public static final String DELAY_MODE_FIXED = "fixed";
    public static final String DELAY_MODE_DURATION = "duration";

    public boolean isAlertDelayEnabled() {
        return getBool(SettingsRepository.KEY_ALERT_DELAY_ENABLED, false);
    }

    public void setAlertDelayEnabled(boolean enabled) {
        set(SettingsRepository.KEY_ALERT_DELAY_ENABLED, enabled);
    }

    public String getAlertDelayMode() {
        String m = getStr(SettingsRepository.KEY_ALERT_DELAY_MODE, DELAY_MODE_FIXED);
        return DELAY_MODE_DURATION.equals(m) ? DELAY_MODE_DURATION : DELAY_MODE_FIXED;
    }

    public void setAlertDelayMode(String mode) {
        set(SettingsRepository.KEY_ALERT_DELAY_MODE,
                DELAY_MODE_DURATION.equals(mode) ? DELAY_MODE_DURATION : DELAY_MODE_FIXED);
    }

    public int getAlertDelaySeconds() {
        return Math.max(0, Math.min(30, getInt(SettingsRepository.KEY_ALERT_DELAY_SECONDS, 2)));
    }

    public void setAlertDelaySeconds(int seconds) {
        set(SettingsRepository.KEY_ALERT_DELAY_SECONDS,
                String.valueOf(Math.max(0, Math.min(30, seconds))));
    }

    /** First rule matching the given event type (FOLLOW / SHARE / GIFT), or null. */
    public com.tiktoksoundalert.models.AlertEventRule getAlertQueueRule(String type) {
        for (com.tiktoksoundalert.models.AlertEventRule rule : getAlertQueueRules()) {
            if (rule.type != null && rule.type.equals(type)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Rule for a live gift, prioritised by the specific gift first, falling
     * back to the generic "Any Gift" rule when no per-gift rule matches.
     */
    public com.tiktoksoundalert.models.AlertEventRule getAlertQueueRuleForGift(String giftName) {
        String normalized = normalize(giftName);
        com.tiktoksoundalert.models.AlertEventRule generic = null;
        for (com.tiktoksoundalert.models.AlertEventRule rule : getAlertQueueRules()) {
            if (rule.type == null) continue;
            if (com.tiktoksoundalert.models.AlertEventRule.TYPE_GIFT.equals(rule.type)) {
                generic = rule; // "Any Gift" fallback
            } else if (com.tiktoksoundalert.models.AlertEventRule.TYPE_GIFT_SPECIFIC.equals(rule.type)
                    && normalized.equals(normalize(rule.giftName))) {
                return rule;
            }
        }
        return generic;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT);
    }

    // General
    public int getMaxLogSize() { return getInt(SettingsRepository.KEY_LOG_MAX_SIZE, 200); }
    public void setMaxLogSize(int size) { set(SettingsRepository.KEY_LOG_MAX_SIZE, String.valueOf(size)); }

    public boolean isShowNotifications() { return getBool(SettingsRepository.KEY_SHOW_NOTIFICATIONS, true); }
    public void setShowNotifications(boolean show) { set(SettingsRepository.KEY_SHOW_NOTIFICATIONS, show); }

    public boolean shouldTtsComment(String comment, String username) {
        if (!isTtsEnabled()) return false;
        if (comment == null || comment.isEmpty()) return false;
        if (comment.length() < getCommentTtsMinLength()) return false;
        if (comment.length() > getCommentTtsMaxLength()) return false;

        Set<String> blocked = getBlockedWords();
        String lowerComment = comment.toLowerCase();
        for (String word : blocked) {
            if (lowerComment.contains(word.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    public String formatTtsText(String username, String comment) {
        if (isTtsAnnounceUsername()) {
            return String.format(getUsernameFormat(), username) + comment;
        }
        return comment;
    }
}