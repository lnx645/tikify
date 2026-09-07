package com.tiktoksoundalert.tiktok.models;

import org.json.JSONObject;

public class Message {
    private final User user;
    private final String text;

    public Message(User user, String text) {
        this.user = user == null ? new User(0, "unknown") : user;
        this.text = text == null ? "" : text;
    }

    public User getUser() {
        return user;
    }

    public String getText() {
        return text;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("user", user.toJson());
            o.put("text", text);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Message fromJson(JSONObject o) {
        if (o == null) return new Message(null, "");
        return new Message(User.fromJson(o.optJSONObject("user")), o.optString("text"));
    }
}