package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokFollowEvent extends TikTokEvent {
    private final Message message;

    public TikTokFollowEvent(Message message) {
        super(EventType.FOLLOW);
        this.message = message;
    }

    public TikTokFollowEvent(User user) {
        this(new Message(user, user.getName() + " followed!"));
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
        return getUser().getName() + " followed!";
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("message", message.toJson());
    }

    static TikTokFollowEvent fromJson(JSONObject o) {
        return new TikTokFollowEvent(Message.fromJson(o.optJSONObject("message")));
    }
}
