package com.tiktoksoundalert.audio;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thin wrapper over a single, process-wide {@link TextToSpeech} engine.
 *
 * <p>The chat/gift service and the in-app preview both use this one engine. On
 * many devices (notably Google TTS) creating a second {@code TextToSpeech} client
 * while the engine is talking makes it drop the active utterance, and calling
 * {@code shutdown()} on it permanently kills the live stream. Sharing one engine
 * means the UI can never disrupt audio that the service is already reading.
 */
public class TTSManager {
    private static final String TAG = "TTSManager";

    private static TextToSpeech engine;
    private static boolean engineReady = false;
    private static final Map<String, Locale> LANGUAGE_MAP = new HashMap<>();

    static {
        LANGUAGE_MAP.put("id", new Locale("id", "ID"));   // Indonesian
        LANGUAGE_MAP.put("en", Locale.ENGLISH);            // English
        LANGUAGE_MAP.put("ms", new Locale("ms", "MY"));   // Malay
        LANGUAGE_MAP.put("ja", Locale.JAPANESE);           // Japanese
        LANGUAGE_MAP.put("ko", Locale.KOREAN);             // Korean
        LANGUAGE_MAP.put("zh", Locale.CHINESE);            // Chinese
        LANGUAGE_MAP.put("es", new Locale("es", "ES"));   // Spanish
        LANGUAGE_MAP.put("pt", new Locale("pt", "BR"));   // Portuguese
        LANGUAGE_MAP.put("th", new Locale("th", "TH"));   // Thai
        LANGUAGE_MAP.put("vi", new Locale("vi", "VN"));   // Vietnamese
    }

    private static final CopyOnWriteArrayList<TTSManager> instances = new CopyOnWriteArrayList<>();

    private static synchronized void ensureEngine(Context context) {
        if (engine != null) return;
        engine = new TextToSpeech(context.getApplicationContext(), status -> {
            boolean ok = status == TextToSpeech.SUCCESS;
            synchronized (TTSManager.class) {
                engineReady = ok;
            }
            if (ok) {
                Log.d(TAG, "TTS initialized successfully");
            } else {
                Log.e(TAG, "TTS initialization failed: " + status);
            }
            for (TTSManager m : instances) {
                m.markReady(ok, ok ? null : "TTS initialization failed: " + status);
            }
        });
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                if (utteranceId != null && utteranceId.startsWith("utterance_")) {
                    Log.d(TAG, "utterance started: " + utteranceId);
                }
            }

            @Override
            public void onDone(String utteranceId) {
                for (TTSManager m : instances) {
                    m.onUtteranceEnded(utteranceId);
                }
            }

            @Override
            public void onError(String utteranceId) {
                for (TTSManager m : instances) {
                    m.onUtteranceEnded(utteranceId);
                }
            }
        });
    }

    public interface OnTTSReadyListener {
        void onReady();
        void onError(String error);
    }

    /** Fired when an utterance reaches onDone/onError or is stopped by the engine. */
    public interface UtteranceCompletionListener {
        void onUtteranceDone(String utteranceId);
    }

    private final Context context;
    private final String name;
    private OnTTSReadyListener readyListener;
    private UtteranceCompletionListener utteranceCompletion;
    private boolean readinessNotified = false;
    private int pendingCount = 0;

    /** Utterance ids handed to the engine but not yet completed (onDone/onError). */
    private final Set<String> outstanding = new java.util.HashSet<>();

    public TTSManager(Context context) {
        this(context, "shared");
    }

    public TTSManager(Context context, String name) {
        this.context = context.getApplicationContext();
        this.name = name;
        synchronized (TTSManager.class) {
            ensureEngine(this.context);
            if (engine != null && !instances.contains(this)) {
                instances.add(this);
            }
        }
    }

    private void markReady(boolean ok, String error) {
        OnTTSReadyListener l;
        synchronized (this) {
            if (readinessNotified) return;
            readinessNotified = true;
            l = readyListener;
        }
        if (l != null) {
            if (ok) {
                l.onReady();
            } else {
                l.onError(error);
            }
        }
    }

    private void onUtteranceEnded(String utteranceId) {
        if (pendingCount > 0) {
            pendingCount--;
        }
        if (utteranceId != null) {
            synchronized (this) {
                outstanding.remove(utteranceId);
            }
        }
        final UtteranceCompletionListener l = utteranceCompletion;
        if (l != null) {
            l.onUtteranceDone(utteranceId);
        }
    }

    public void setUtteranceCompletionListener(UtteranceCompletionListener listener) {
        this.utteranceCompletion = listener;
    }

    public void setOnReadyListener(OnTTSReadyListener listener) {
        synchronized (this) {
            this.readyListener = listener;
        }
        if (engineReady) {
            markReady(true, null);
        }
    }

    public boolean setLanguage(String langCode) {
        TextToSpeech t = engine;
        if (!engineReady || t == null) return false;

        Locale locale = LANGUAGE_MAP.get(langCode);
        if (locale == null) {
            locale = new Locale(langCode);
        }

        int result = t.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language not supported: " + langCode);
            return false;
        }
        return true;
    }

    public void setSpeed(float speed) {
        TextToSpeech t = engine;
        if (t != null) {
            t.setSpeechRate(Math.max(0.1f, Math.min(2.0f, speed)));
        }
    }

    public void setPitch(float pitch) {
        TextToSpeech t = engine;
        if (t != null) {
            t.setPitch(Math.max(0.1f, Math.min(2.0f, pitch)));
        }
    }

    public void applyVoice(float speed, float pitch) {
        setSpeed(speed);
        setPitch(pitch);
    }

    @SuppressWarnings("deprecation")
    public boolean speak(String text, String utteranceId, int volumePercent) {
        TextToSpeech t = engine;
        if (!engineReady || t == null) {
            Log.w(TAG, "[" + name + "] TTS not initialized, cannot speak");
            return false;
        }

        if (text == null || text.trim().isEmpty()) return false;

        if (pendingCount > 200) {
            pendingCount = 0;
        }

        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        float volume = Math.max(0f, Math.min(1f, volumePercent / 100f));
        params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, String.valueOf(volume));

        int result = t.speak(text, TextToSpeech.QUEUE_ADD, params);
        if (result == TextToSpeech.ERROR) {
            Log.w(TAG, "[" + name + "] Engine refused utterance: " + text);
            return false;
        } else {
            pendingCount++;
            if (utteranceId != null) {
                synchronized (this) {
                    outstanding.add(utteranceId);
                }
            }
        }
        Log.d(TAG, "[" + name + "] Speaking: " + text);
        return true;
    }

    /** Stop the current utterance and drop everything queued. */
    public void flush() {
        TextToSpeech t = engine;
        if (t != null) {
            t.stop();
        }
        pendingCount = 0;
        // The engine does NOT deliver onError/onDone for utterances killed by
        // stop() — and because the engine is shared, one stop() kills the audio
        // of EVERY manager. Complete every manager's outstanding utterances
        // ourselves so any awaiting worker unblocks at once instead of hanging
        // for its full utterance timeout.
        for (TTSManager m : instances) {
            m.completeOutstanding();
        }
    }

    /** Completes all utterances this manager handed to the engine. */
    private void completeOutstanding() {
        java.util.List<String> killed;
        synchronized (this) {
            killed = new java.util.ArrayList<>(outstanding);
            outstanding.clear();
        }
        final UtteranceCompletionListener l = utteranceCompletion;
        if (l != null) {
            for (String uid : killed) {
                l.onUtteranceDone(uid);
            }
        }
    }

    public void stop() {
        flush();
    }

    public boolean isSpeaking() {
        TextToSpeech t = engine;
        return t != null && t.isSpeaking();
    }

    public boolean isInitialized() {
        return engineReady;
    }

    /**
     * No-op lifecycle hook: the single engine is shared and must NEVER be torn
     * down while the app runs (destroying it permanently kills live TTS).
     * Used on full service teardown; also flushes whatever is playing.
     */
    public void shutdown() {
        stop();
        synchronized (TTSManager.class) {
            instances.remove(this);
        }
    }

    /** Drop this manager's references without disturbing the shared engine. */
    public void release() {
        readyListener = null;
        utteranceCompletion = null;
        synchronized (TTSManager.class) {
            instances.remove(this);
        }
    }
}
