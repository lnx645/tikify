package com.tiktoksoundalert;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class TikTokSoundApp extends Application {

    public static final String CHANNEL_ID_SERVICE = "tiktok_service_channel";
    public static final String CHANNEL_ID_ALERTS = "tiktok_alerts_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID_SERVICE,
                    "TikTok Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Background TikTok LIVE connection");

            NotificationChannel alertChannel = new NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    "Gift Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("Gift and comment notifications");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                manager.createNotificationChannel(alertChannel);
            }
        }
    }
}
