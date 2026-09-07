package com.tiktoksoundalert.tiktok.models;

import org.json.JSONObject;

public class User {
    private final long id;
    private final String name;
    private final String avatarUrl;

    public User(long id, String name) {
        this(id, name, null);
    }

    public User(long id, String name, String avatarUrl) {
        this.id = id;
        this.name = (name == null || name.isEmpty()) ? "user_" + id : name;
        this.avatarUrl = avatarUrl;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("avatarUrl", avatarUrl);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static User fromJson(JSONObject o) {
        if (o == null) return new User(0, "unknown");
        return new User(o.optLong("id"), o.optString("name"),
                o.optString("avatarUrl", null));
    }
}