package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokDebugEvent extends TikTokEvent {
    private final String message;

    public TikTokDebugEvent(String message) {
        super(EventType.DEBUG);
        this.message = message == null ? "" : message;
    }

    public String getMessageText() {
        return message;
    }

    @Override
    public String getDisplayText() {
        return message;
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("message", message);
    }

    static TikTokDebugEvent fromJson(JSONObject o) {
        return new TikTokDebugEvent(o.optString("message"));
    }
}
