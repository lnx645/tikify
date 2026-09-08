package com.tiktoksoundalert.models;

/**
 * One alert-queue rule: when a LIVE event of {@link #type} happens, play
 * {@link #sound} at {@link #volume} if {@link #enabled} (and the master
 * "Queue Sound Alert (By Event)" switch is on).
 */
public class AlertEventRule {

    public static final String TYPE_FOLLOW = "FOLLOW";
    public static final String TYPE_SHARE = "SHARE";
    public static final String TYPE_JOIN = "JOIN";
    public static final String TYPE_GIFT = "GIFT";
    /** A rule scoped to one specific gift (instead of "Any Gift"). */
    public static final String TYPE_GIFT_SPECIFIC = "GIFT_SPECIFIC";

    /** Stable unique id so the UI can diff and edit items. */
    public String id;
    /** FOLLOW / SHARE / GIFT / GIFT_SPECIFIC (see TYPE_* constants). */
    public String type;
    /** Sound key: a built-in library key ("ding",...) or a custom sound name. */
    public String sound;
    public boolean enabled;
    /** 0..100 */
    public int volume;
    /** When true, this event always beats non-priority alerts in the queue. */
    public boolean priority;
    /** Canonical gift name for TYPE_GIFT_SPECIFIC rules (ignored otherwise). */
    public String giftName;
    /** Display image URL for TYPE_GIFT_SPECIFIC rules (ignored otherwise). */
    public String giftImageUrl;

    public AlertEventRule() {
    }

    public AlertEventRule(String id, String type, String sound, boolean enabled, int volume) {
        this.id = id;
        this.type = type;
        this.sound = sound;
        this.enabled = enabled;
        this.volume = volume;
    }

    public AlertEventRule(String id, String type, String giftName, String sound,
                          boolean enabled, int volume) {
        this.id = id;
        this.type = type;
        this.giftName = giftName;
        this.sound = sound;
        this.enabled = enabled;
        this.volume = volume;
    }

    /** Whether a sound has been selected yet for this rule. */
    public boolean hasSound() {
        return sound != null && !sound.trim().isEmpty();
    }

    public static String displayType(String type) {
        if (TYPE_FOLLOW.equals(type)) return "Follow";
        if (TYPE_SHARE.equals(type)) return "Share";
        if (TYPE_JOIN.equals(type)) return "Join";
        if (TYPE_GIFT.equals(type)) return "Any Gift";
        if (TYPE_GIFT_SPECIFIC.equals(type)) return "Gift Name";
        return type == null ? "" : type;
    }

    public static String labelFor(String type) {
        if (TYPE_FOLLOW.equals(type)) return "Follow";
        if (TYPE_SHARE.equals(type)) return "Share";
        if (TYPE_JOIN.equals(type)) return "Join";
        return "Gift";
    }
}