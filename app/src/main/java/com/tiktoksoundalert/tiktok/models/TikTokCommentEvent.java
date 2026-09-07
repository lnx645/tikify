package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokCommentEvent extends TikTokEvent {
    private final Message message;

    public TikTokCommentEvent(Message message) {
        super(EventType.COMMENT);
        this.message = message;
    }

    public TikTokCommentEvent(User user, String text) {
        this(new Message(user, text));
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
        return getText();
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("message", message.toJson());
    }

    static TikTokCommentEvent fromJson(JSONObject o) {
        return new TikTokCommentEvent(Message.fromJson(o.optJSONObject("message")));
    }
}
