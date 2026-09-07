package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class TikTokEvent {
    private final EventType type;
    private final long timestamp;

    protected TikTokEvent(EventType type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public EventType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public abstract String getDisplayText();

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type.name());
            o.put("timestamp", timestamp);
            fillJson(o);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public String toJsonString() {
        return toJson().toString();
    }

    protected abstract void fillJson(JSONObject o) throws JSONException;

    public static TikTokEvent fromJson(String json) {
        try {
            JSONObject o = new JSONObject(json);
            switch (EventType.valueOf(o.optString("type", "ERROR"))) {
                case COMMENT:
                    return TikTokCommentEvent.fromJson(o);
                case GIFT:
                    return TikTokGiftEvent.fromJson(o);
                case JOIN:
                    return TikTokJoinEvent.fromJson(o);
                case LIKE:
                    return TikTokLikeEvent.fromJson(o);
                case FOLLOW:
                    return TikTokFollowEvent.fromJson(o);
                case SHARE:
                    return TikTokShareEvent.fromJson(o);
                case CONNECTING:
                    return TikTokConnectingEvent.fromJson(o);
                case CONNECTED:
                    return TikTokConnectedEvent.fromJson(o);
                case DISCONNECTED:
                    return TikTokDisconnectedEvent.fromJson(o);
                case ERROR:
                    return TikTokErrorEvent.fromJson(o);
                case DEBUG:
                    return TikTokDebugEvent.fromJson(o);
                default:
                    return new TikTokErrorEvent("Unsupported event");
            }
        } catch (Exception e) {
            return new TikTokErrorEvent("Failed to parse event");
        }
    }
}
