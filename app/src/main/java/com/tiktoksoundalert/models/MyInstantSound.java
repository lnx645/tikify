package com.tiktoksoundalert.models;

/**
 * A sound scraped from MyInstants (name + media path).
 */
public class MyInstantSound {
    public final String name;
    public final String soundPath;

    public MyInstantSound(String name, String soundPath) {
        this.name = name;
        this.soundPath = soundPath;
    }

    public String soundUrl() {
        return "https://www.myinstants.com" + soundPath;
    }
}