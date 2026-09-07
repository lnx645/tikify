package com.tiktoksoundalert.audio;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tiny audio battleground between the alert-sound lane (SoundPool effects) and
 * the speech lane (scheduler-owned TTS engine).
 *
 * <p>Alert effects are short and punchy and always play immediately. The mixer
 * gives them a fixed "effect window" (start + expected duration); new speech is
 * gated to start only once the window clears, so a gift sound and its own
 * spoken announcement never attack the speakers at the same instant.
 *
 * <p>When an effect fires while the engine is already mid-sentence, each
 * registered listener (the TTS scheduler) is told so it can pause and resume
 * instead of overlapping.
 *
 * <p>Pure Java + wall-clock on purpose: no AudioFocus dependency, deterministic
 * and testable, and it works regardless of which audio stream the engine uses.
 */
public final class AudioMixCoordinator {

    /** How long an effect is assumed to ring if nobody overrides it. */
    private static final long DEFAULT_EFFECT_MS = 1500;
    private static final long MIN_EFFECT_MS = 150;

    private final List<EffectListener> listeners = new CopyOnWriteArrayList<>();

    private final Object lock = new Object();

    /** Wall-clock instant before which new speech must not start. */
    private long effectEndAt = 0L;

    private AudioMixCoordinator() {
    }

    public static AudioMixCoordinator get() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        static final AudioMixCoordinator INSTANCE = new AudioMixCoordinator();
    }

    public interface EffectListener {
        void onAlertEffectStarted(long expectedDurationMs);

        void onAlertEffectEnded();
    }

    public void register(EffectListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregister(EffectListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify that an alert effect is about to play.
     *
     * <p>Widens the effect window (effects only extend, never shrink it) and
     * tells each listener so in-flight speech can pause.
     */
    public void onAlertEffect(long expectedDurationMs) {
        long window = Math.max(MIN_EFFECT_MS, expectedDurationMs);
        synchronized (lock) {
            long end = System.currentTimeMillis() + window;
            if (end > effectEndAt) {
                effectEndAt = end;
            }
        }
        for (EffectListener l : listeners) {
            try {
                l.onAlertEffectStarted(window);
            } catch (Exception ignored) {
            }
        }
    }

    /** Notify listeners that the currently playing effect has finished. */
    public void onAlertEffectEnded() {
        for (EffectListener l : listeners) {
            try {
                l.onAlertEffectEnded();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Milliseconds a NEW utterance must hold before speaking (0 when clear).
     * Polled by the speech worker before each engine call.
     */
    public long speechBackoffMillis() {
        synchronized (lock) {
            long remain = effectEndAt - System.currentTimeMillis();
            return Math.max(0L, remain);
        }
    }

    /** Effect-window used when a caller has no duration info. */
    public static long defaultEffectMillis() {
        return DEFAULT_EFFECT_MS;
    }

    /** Testable clock hook (internal). */
    long effectRemaining(long now) {
        return Math.max(0L, effectEndAt - now);
    }
}