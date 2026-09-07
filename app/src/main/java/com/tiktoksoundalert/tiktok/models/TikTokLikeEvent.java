package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokLikeEvent extends TikTokEvent {
    private final Message message;
    private final long count;

    public TikTokLikeEvent(Message message, long count) {
        super(EventType.LIKE);
        this.message = message;
        this.count = count;
    }

    public TikTokLikeEvent(User user, long count) {
        this(new Message(user, user.getName() + " sent likes"), count);
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

    public long getCount() {
        return count;
    }

    @Override
    public String getDisplayText() {
        return getUser().getName() + " sent " + count + " likes";
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("message", message.toJson());
        o.put("count", count);
    }

    static TikTokLikeEvent fromJson(JSONObject o) {
        return new TikTokLikeEvent(Message.fromJson(o.optJSONObject("message")), o.optLong("count"));
    }
}
