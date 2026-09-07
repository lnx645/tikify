package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokConnectedEvent extends TikTokEvent {
    private final String hostName;
    private final String roomId;

    public TikTokConnectedEvent(String hostName, String roomId) {
        super(EventType.CONNECTED);
        this.hostName = hostName == null ? "" : hostName;
        this.roomId = roomId == null ? "" : roomId;
    }

    public String getHostName() {
        return hostName;
    }

    public String getRoomId() {
        return roomId;
    }

    @Override
    public String getDisplayText() {
        return "Connected to live stream of @" + hostName;
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("host", hostName);
        o.put("roomId", roomId);
    }

    static TikTokConnectedEvent fromJson(JSONObject o) {
        return new TikTokConnectedEvent(o.optString("host"), o.optString("roomId"));
    }
}
