package com.tiktoksoundalert.tiktok;

import android.content.Context;
import android.util.Log;

import com.tiktoksoundalert.tiktok.models.TikTokEvent;

/**
 * Listener interface for TikTok LIVE events.
 */
public interface TikTokLiveListener {
    void onConnecting();
    void onConnected(String roomId, String hostName);
    void onDisconnected(String reason);
    void onError(Exception error);
    void onEvent(TikTokEvent event);
}
