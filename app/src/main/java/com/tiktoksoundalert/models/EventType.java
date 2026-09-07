package com.tiktoksoundalert.models;

public enum EventType {
    CONNECTING("Connecting..."),
    CONNECTED("Connected!"),
    DISCONNECTED("Disconnected"),
    ERROR("Error"),
    GIFT("Gift"),
    COMMENT("Comment"),
    LIKE("Like"),
    FOLLOW("Follow"),
    SHARE("Share"),
    JOIN("Join"),
    SUBSCRIBE("Subscribe"),
    ROOM_INFO("Room Info"),
    DEBUG("Debug");

    private final String displayName;

    EventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
