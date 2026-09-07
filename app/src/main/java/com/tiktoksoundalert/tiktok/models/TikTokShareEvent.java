package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokShareEvent extends TikTokEvent {
    private final Message message;

    public TikTokShareEvent(Message message) {
        super(EventType.SHARE);
        this.message = message;
    }

    public TikTokShareEvent(User user) {
        this(new Message(user, user.getName() + " shared!"));
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
        return getUser().getName() + " shared the stream!";
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("message", message.toJson());
    }

    static TikTokShareEvent fromJson(JSONObject o) {
        return new TikTokShareEvent(Message.fromJson(o.optJSONObject("message")));
    }
}
