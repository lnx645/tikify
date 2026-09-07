package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokErrorEvent extends TikTokEvent {
    private final String message;

    public TikTokErrorEvent(String message) {
        super(EventType.ERROR);
        this.message = message == null ? "Unknown error" : message;
    }

    public String getMessageText() {
        return message;
    }

    @Override
    public String getDisplayText() {
        return "Error: " + message;
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("message", message);
    }

    static TikTokErrorEvent fromJson(JSONObject o) {
        return new TikTokErrorEvent(o.optString("message"));
    }
}
