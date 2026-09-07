package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokDisconnectedEvent extends TikTokEvent {
    private final String reason;

    public TikTokDisconnectedEvent(String reason) {
        super(EventType.DISCONNECTED);
        this.reason = reason == null ? "" : reason;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String getDisplayText() {
        return "Disconnected: " + reason;
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("reason", reason);
    }

    static TikTokDisconnectedEvent fromJson(JSONObject o) {
        return new TikTokDisconnectedEvent(o.optString("reason"));
    }
}
