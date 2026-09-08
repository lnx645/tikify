package com.tiktoksoundalert.audio;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serialised playback queue for alert sounds.
 *
 * <p>When the user enables the alert delay, event sounds no longer overlap:
 * every alert enqueued here plays one after another on a single worker thread.
 *
 * <p>Priority policy (scored on every pick, so cards are re-sorted live):
 * each waiting alert gets
 * <pre>
 *   score(a) = base(a) + ageMs(a) / AGING_PER_POINT_MS
 * </pre>
 * where {@code base} is {@link #PRIORITY_BASE} for a priority alert and {@code 0}
 * otherwise, and {@code ageMs} is how long the alert has been waiting. Priority
 * alerts therefore always outrank normal ones (their base is far larger than any
 * realistic age bonus), while within the same class the oldest alert wins
 * (FIFO, fair under bursts) because age grows monotonically. No alert can starve:
 * age keeps rising until it is the oldest of its class.
 *
 * <p>A priority alert also shortens the inter-alert delay: the gap in the queue
 * is applied after a normal alert only if nothing of higher priority is waiting,
 * so a priority sound rings as soon as the current audio finishes.
 *
 * <p>Playback still goes through {@link GiftSoundManager} (SoundPool + the
 * AudioMixCoordinator gate), so the TTS-interrupt behaviour is unchanged.
 *
 * <p>The queue is bounded: in a burst (e.g. a gift raid with delay enabled)
 * alerts beyond {@link #MAX_QUEUED} are dropped rather than growing the backlog
 * into a minutes-long tail.
 */
public final class AlertSoundQueue {

    private static final String TAG = "AlertSoundQueue";

    /** Max alerts waiting (not yet started) before new ones are dropped. */
    private static final int MAX_QUEUED = 40;

    /** Head start a priority alert gets over normal alerts (see class javadoc). */
    private static final long PRIORITY_BASE = 1_000_000L;

    /** One age point per this many milliseconds waited (age = ms / divisor). */
    private static final long AGING_PER_POINT_MS = 1_000L;

    private final Context context;
    private final GiftSoundManager soundManager;
    private final HandlerThread thread;
    private final Handler handler;
    private final Map<String, Long> durationCache = new HashMap<>();
    private final AtomicInteger queued = new AtomicInteger(0);

    /** Re-ordered on every pick; guarded by {@link #lock}. */
    private final java.util.List<Task> pending = new java.util.ArrayList<>();
    private final Object lock = new Object();

    private long idCounter = 0;
    private volatile boolean destroyed;

    private static final class Task {
        final long id;
        final String sound;
        final int volume;
        final long gapMs;
        final boolean priority;
        final long enqueuedAt;

        Task(long id, String sound, int volume, long gapMs, boolean priority, long enqueuedAt) {
            this.id = id;
            this.sound = sound;
            this.volume = volume;
            this.gapMs = gapMs;
            this.priority = priority;
            this.enqueuedAt = enqueuedAt;
        }
    }

    public AlertSoundQueue(Context context, GiftSoundManager soundManager) {
        this.context = context.getApplicationContext();
        this.soundManager = soundManager;
        this.thread = new HandlerThread("AlertSoundQueue");
        this.thread.start();
        this.handler = new Handler(this.thread.getLooper());
    }

    /** Appends an alert to the queue (kept in priority order per the score policy). */
    public void enqueue(String soundKey, int volume, long gapMs, boolean priority) {
        if (destroyed || soundKey == null || soundKey.trim().isEmpty()) {
            return;
        }
        synchronized (lock) {
            if (queued.get() >= MAX_QUEUED) {
                Log.w(TAG, "Alert queue full (" + MAX_QUEUED + "), dropping: " + soundKey);
                return;
            }
            queued.incrementAndGet();
            pending.add(new Task(++idCounter, soundKey, volume, gapMs, priority,
                    System.currentTimeMillis()));
            handler.post(this::pump);
        }
    }

    private void pump() {
        while (true) {
            Task task;
            synchronized (lock) {
                task = pick();
            }
            if (task == null) {
                return;
            }
            play(task);
            synchronized (lock) {
                queued.updateAndGet(n -> Math.max(0, n - 1));
            }
        }
    }

    /** Highest score wins; ties break by oldest first (FIFO). Null when empty. */
    private Task pick() {
        if (pending.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Task best = null;
        long bestScore = Long.MIN_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            Task t = pending.get(i);
            long ageMs = now - t.enqueuedAt;
            long score = (t.priority ? PRIORITY_BASE : 0L) + ageMs / AGING_PER_POINT_MS;
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        pending.remove(best);
        return best;
    }

    private boolean hasPendingPriority() {
        for (Task t : pending) {
            if (t.priority) {
                return true;
            }
        }
        return false;
    }

    private void play(Task task) {
        if (destroyed) return;
        long duration = durationOf(task.sound);
        soundManager.playSound(task.sound, task.volume);

        long start = System.currentTimeMillis();
        long gapDeadline = start + duration + Math.max(0L, task.gapMs);
        while (!destroyed) {
            // If a priority alert is waiting, drop the inter-alert gap so it rings
            // right after the current audio instead of waiting the full delay.
            long deadline = hasPendingPriority()
                    ? start + duration
                    : gapDeadline;
            long remain = deadline - System.currentTimeMillis();
            if (remain <= 0) return;
            try {
                Thread.sleep(Math.min(remain, 100L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
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

    /** Drops all queued (not yet played) alerts. A sound already ringing finishes. */
    public void clear() {
        synchronized (lock) {
            pending.clear();
            queued.set(0);
        }
    }

    /** Drops queued work and stops the worker thread. Safe to call multiple times. */
    public void shutdown() {
        destroyed = true;
        synchronized (lock) {
            pending.clear();
            queued.set(0);
        }
        handler.post(this.thread::quitSafely);
    }
}