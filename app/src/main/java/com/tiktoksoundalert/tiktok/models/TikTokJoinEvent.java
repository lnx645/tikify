package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokJoinEvent extends TikTokEvent {
    private final Message message;

    public TikTokJoinEvent(Message message) {
        super(EventType.JOIN);
        this.message = message;
    }

    public TikTokJoinEvent(User user) {
        this(new Message(user, user.getName() + " joined"));
    }

    public Message getMessage() {
        return message;
    }

    public User getUser() {
        return message.getUser();
    }

    public String getText() {
        return message.getText();
    }

    @Override
    public String getDisplayText() {
        return getUser().getName() + " joined";
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("message", message.toJson());
    }

    static TikTokJoinEvent fromJson(JSONObject o) {
        return new TikTokJoinEvent(Message.fromJson(o.optJSONObject("message")));
    }
}
