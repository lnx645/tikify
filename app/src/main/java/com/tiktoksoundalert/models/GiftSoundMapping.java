package com.tiktoksoundalert.models;

public class GiftSoundMapping {
    private final String giftName;
    private final String soundFileName;
    private final String soundPath;
    private final boolean enabled;
    private final int volume;

    public GiftSoundMapping(String giftName, String soundFileName, String soundPath, boolean enabled, int volume) {
        this.giftName = giftName;
        this.soundFileName = soundFileName;
        this.soundPath = soundPath;
        this.enabled = enabled;
        this.volume = volume;
    }

    public String getGiftName() { return giftName; }
    public String getSoundFileName() { return soundFileName; }
    public String getSoundPath() { return soundPath; }
    public boolean isEnabled() { return enabled; }
    public int getVolume() { return volume; }
}
