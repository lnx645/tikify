package com.tiktoksoundalert.models;

/**
 * One TikTok live gift from the StreamToEarn catalog: its canonical name
 * (also used by TikTok's gift events), a display image URL and its coin price.
 */
public class GiftInfo {
    public final String name;
    public final String imageUrl;
    public final int price;

    public GiftInfo(String name, String imageUrl, int price) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.price = price;
    }
}
