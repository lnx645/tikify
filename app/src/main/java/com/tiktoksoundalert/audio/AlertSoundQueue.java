package com.tiktoksoundalert.audio;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Serialised playback queue for alert sounds.
 *
 * <p>When the user enables the alert delay, event sounds no longer overlap:
 * every alert enqueued here plays one after another on a single worker thread.
 * Between two alerts the worker waits the gap the user configured:
 * <ul>
 *   <li><b>fixed</b> — a constant number of seconds AFTER the current audio
 *   ends, before the next alert starts;</li>
 *   <li><b>duration</b> — the next alert starts as soon as the current audio's
 *   expected duration has elapsed (no extra gap).</li>
 * </ul>
 *
 * <p>Playback still goes through {@link GiftSoundManager} (SoundPool + the
 * AudioMixCoordinator gate), so the TTS-interrupt behaviour is unchanged.
 */
public final class AlertSoundQueue {

    private static final String TAG = "AlertSoundQueue";

    private final Context context;
    private final GiftSoundManager soundManager;
    private final HandlerThread thread;
    private final Handler handler;
    private final Map<String, Long> durationCache = new HashMap<>();

    private volatile boolean destroyed;

    public AlertSoundQueue(Context context, GiftSoundManager soundManager) {
        this.context = context.getApplicationContext();
        this.soundManager = soundManager;
        this.thread = new HandlerThread("AlertSoundQueue");
        this.thread.start();
        this.handler = new Handler(this.thread.getLooper());
    }

    /**
     * Appends an alert to the queue. {@code gapMs} is applied AFTER the sound's
     * expected duration, so the next queued alert starts at
     * {@code duration + gapMs} later.
     */
    public void enqueue(String soundKey, int volume, long gapMs) {
        if (destroyed || soundKey == null || soundKey.trim().isEmpty()) {
            return;
        }
        handler.post(() -> play(soundKey, volume, gapMs));
    }

    private void play(String soundKey, int volume, long gapMs) {
        if (destroyed) return;
        long duration = durationOf(soundKey);
        soundManager.playSound(soundKey, volume);
        long wait = duration + Math.max(0L, gapMs);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private long durationOf(String soundKey) {
        Long cached = durationCache.get(soundKey);
        if (cached != null) {
            return cached;
        }
        long d = GiftSoundManager.durationOf(soundKey, context);
        durationCache.put(soundKey, d);
        return d;
    }

    /** Drops queued work and stops the worker thread. Safe to call multiple times. */
    public void shutdown() {
        destroyed = true;
        handler.post(this.thread::quitSafely);
    }
}