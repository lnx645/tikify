package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokConnectingEvent extends TikTokEvent {
    private final String hostName;

    public TikTokConnectingEvent(String hostName) {
        super(EventType.CONNECTING);
        this.hostName = hostName == null ? "" : hostName;
    }

    public String getHostName() {
        return hostName;
    }

    @Override
    public String getDisplayText() {
        return "Connecting to @" + hostName + "...";
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("host", hostName);
    }

    static TikTokConnectingEvent fromJson(JSONObject o) {
        return new TikTokConnectingEvent(o.optString("host"));
    }
}
