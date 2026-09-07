package com.tiktoksoundalert.tiktok.models;

import org.json.JSONObject;

public class Gift {
    private final long id;
    private final String name;
    private final long type;
    private final long diamondCount;
    private final String imageUrl;

    public Gift(long id, String name, long type, long diamondCount) {
        this(id, name, type, diamondCount, null);
    }

    public Gift(long id, String name, long type, long diamondCount, String imageUrl) {
        this.id = id;
        this.name = (name == null || name.isEmpty()) ? "gift_" + id : name;
        this.type = type;
        this.diamondCount = diamondCount;
        this.imageUrl = imageUrl;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getType() {
        return type;
    }

    public long getDiamondCount() {
        return diamondCount;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("type", type);
            o.put("diamondCount", diamondCount);
            o.put("imageUrl", imageUrl);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Gift fromJson(JSONObject o) {
        if (o == null) return new Gift(0, "unknown", 0, 0);
        return new Gift(o.optLong("id"), o.optString("name"),
                o.optLong("type"), o.optLong("diamondCount"),
                o.optString("imageUrl", null));
    }
}