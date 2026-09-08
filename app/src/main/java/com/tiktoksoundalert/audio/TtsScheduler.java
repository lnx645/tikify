package com.tiktoksoundalert.audio;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Intelligent TTS scheduler that owns the single speech engine and decides, for
 * every chat comment and gift, WHAT may interrupt WHAT and WHAT must be dropped.
 *
 * Why not a plain engine queue?
 * - The engine's native queue is FIFO and cannot be re-ordered, evicted or
 *   preempted selectively.
 * - Chats would bury gifts, or one user's spam would drown everyone else.
 *
 * Policy (highest score first):
 *  - Gift:  2000 + value-bonus (diamond * count / 2, capped +1200). Big gifts
 *    interrupt ("preempt") the current speaker; cheap ones just line up.
 *  - Chat (priority word): 2900 - jumps the queue over normal chat + small gifts.
 *  - Chat (normal): 400..800 based on message length/quality.
 *
 * Extra safeguards:
 *  - Preemption only when the new gift out-scores the current speaker by a margin
 *    and at most once per PREEMPT_DEBOUNCE_MS (no audio chopping during raids).
 *  - Queue latency budget: once the pending speech exceeds a few seconds, the
 *    lowest-quality queued chat is evicted instead of growing the backlog.
 *  - Coalescing: a user that already has a (recent) message waiting is not read
 *    again, so chatter spam collapses to one reading per burst.
 *  - Voice switching is done only when speed/pitch actually differ from the last
 *    applied values, so we never call the engine unnecessarily.
 */
public class TtsScheduler {
    private static final String TAG = "TtsScheduler";

    public enum Decision { PLAYED, QUEUED, PREEMPTED, COALESCED, DROPPED, BUSY }

    private enum Type { CHAT, GIFT }

    private static final class Task implements Comparable<Task> {
        final long id;
        final Type type;
        final String text;
        final String user;
        final int score;
        final boolean alreadyBoosted;
        final long createdAt = System.currentTimeMillis();
        final float speed;
        final float pitch;
        final int volume;
        final float estimatedSeconds;

        Task(long id, Type type, String text, String user, int score,
             float speed, float pitch, int volume) {
            this(id, type, text, user, score, false, speed, pitch, volume);
        }

        Task(long id, Type type, String text, String user, int score,
             boolean alreadyBoosted, float speed, float pitch, int volume) {
            this.id = id;
            this.type = type;
            this.text = text;
            this.user = user;
            this.score = score;
            this.alreadyBoosted = alreadyBoosted;
            this.speed = speed;
            this.pitch = pitch;
            this.volume = volume;
            this.estimatedSeconds = estimateSeconds(text, speed);
        }

        @Override
        public int compareTo(Task o) {
            int byScore = Integer.compare(o.score, this.score);
            if (byScore != 0) return byScore;
            return Long.compare(this.createdAt, o.createdAt);
        }
    }

    private static final int NORMAL_CHAT_BASE = 400;
    private static final int PRIORITY_CHAT_SCORE = 2900;
    private static final int GIFT_BASE_SCORE = 2000;
    private static final int GIFT_MAX_BONUS = 1200;
    private static final int PREEMPT_MIN_MARGIN = 600;
    private static final long PREEMPT_DEBOUNCE_MS = 1600;
    private static final float QUEUE_BUDGET_SECONDS = 6f;
    private static final long COALESCE_WINDOW_MS = 4000;
    private static final int HARD_QUEUE_LIMIT = 32;
    private static final long UTTERANCE_TIMEOUT_MS = 30_000;
    private static final float CHARS_PER_SECOND = 17f;

    /** Score boost applied to a speech task resumed after an alert effect. */
    private static final int REQUEUE_BOOST = 600;

    /** Ceiling for how long NEW speech waits behind an alert effect. */
    private static final long MAX_ALERT_BACKOFF_MS = 4000;

    private final AudioMixCoordinator mixer = AudioMixCoordinator.get();
    private volatile boolean interruptOnAlert = true;

    private final AudioMixCoordinator.EffectListener alertEffectListener =
            new AudioMixCoordinator.EffectListener() {
                @Override
                public void onAlertEffectStarted(long expectedDurationMs) {
                    onAlertEffect(expectedDurationMs);
                }

                @Override
                public void onAlertEffectEnded() {
                    // Nothing to do: the worker re-polls the backoff gate.
                }
            };

    private final TTSManager tts;
    private final Object lock = new Object();
    private final PriorityQueue<Task> queue = new PriorityQueue<>();
    private final Map<String, CountDownLatch> pendingLatches = new HashMap<>();

    private Task current;
    private Task interruptedByAlert;
    private long lastPreemptAt = 0;
    private float lastAppliedSpeed = -1f;
    private float lastAppliedPitch = -1f;
    private long idCounter = 0;
    private volatile boolean stopped = false;
    /** Bumped on every stop(): detects tasks polled just before a clear. */
    private long generation = 0;

    public interface OnReadyListener {
        void onReady();
    }

    public interface OnErrorListener {
        void onError(String error);
    }

    public TtsScheduler(Context context, OnReadyListener ready, OnErrorListener error) {
        this.tts = new TTSManager(context.getApplicationContext());
        this.tts.setUtteranceCompletionListener(this::onUtteranceDone);
        this.tts.setOnReadyListener(new TTSManager.OnTTSReadyListener() {
            @Override
            public void onReady() {
                if (ready != null) ready.onReady();
            }

            @Override
            public void onError(String err) {
                if (error != null) error.onError(err);
            }
        });
        mixer.register(alertEffectListener);
        Thread worker = new Thread(this::workerLoop, "tts-scheduler");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Whether an alert effect should halt the current utterance (it resumes
     * right afterwards). When off, alerts simply play over ongoing speech.
     */
    public void setInterruptOnAlert(boolean interruptOnAlert) {
        this.interruptOnAlert = interruptOnAlert;
    }

    /** Called by the audio mixer when an alert effect starts ringing. */
    private void onAlertEffect(long expectedDurationMs) {
        if (!interruptOnAlert) return;
        synchronized (lock) {
            if (current != null && tts != null && tts.isSpeaking()) {
                interruptedByAlert = current;
                // onError/onStop -> latch countDown -> worker re-queues the
                // interrupted task and the hi-fi gate lets it resume.
                tts.stop();
            }
        }
    }

    // ---- public API ----

    public boolean isReady() {
        return tts != null && tts.isInitialized();
    }

    public void setLanguage(String langCode) {
        if (tts != null) {
            tts.setLanguage(langCode == null ? "id" : langCode);
        }
    }

    /**
     * Submit a chat comment to be read aloud.
     *
     * @return the decision the scheduler took (played / queued / coalesced /
     *         dropped so the caller can log it).
     */
    public Decision submitComment(String text, String user, float speed, float pitch,
                                  int volume, boolean priorityWord) {
        if (tts == null || !tts.isInitialized()) return Decision.BUSY;
        if (text == null || text.trim().isEmpty()) return Decision.DROPPED;

        int score = priorityWord
                ? PRIORITY_CHAT_SCORE
                : scoreComment(text);

        synchronized (lock) {
            // Coalesce: don't read a user twice before their earlier message has
            // even been spoken (collapses chatter bursts to one reading).
            if (!priorityWord && user != null && hasQueuedChat(user)) {
                return Decision.COALESCED;
            }

            Task task = new Task(++idCounter, Type.CHAT, text, user, score,
                    speed, pitch, volume);

            // Latency budget: keep the backlog small and always read the most
            // important messages. Evict the weakest queued chat, or drop this one.
            if (overBudget() && task.type == Type.CHAT) {
                Task weakest = weakestType(Type.CHAT);
                if (weakest != null && task.score > weakest.score) {
                    queue.remove(weakest);
                } else {
                    return Decision.DROPPED;
                }
            }
            if (queue.size() >= HARD_QUEUE_LIMIT) {
                Task weakest = weakestType(Type.CHAT);
                if (weakest != null && task.score > weakest.score) {
                    queue.remove(weakest);
                } else {
                    return Decision.DROPPED;
                }
            }

            queue.add(task);
            lock.notifyAll();
            return current == null ? Decision.PLAYED : Decision.QUEUED;
        }
    }

    /**
     * Submit a gift announcement. Gifts may preempt the current speaker when they
     * are valuable enough, but are never evicted from the queue.
     */
    public Decision submitGift(String text, String user, long diamonds, int count,
                               float speed, float pitch, int volume) {
        if (tts == null || !tts.isInitialized()) return Decision.BUSY;
        if (text == null || text.trim().isEmpty()) return Decision.DROPPED;

        int score = scoreGift(diamonds, count);

        synchronized (lock) {
            Task task = new Task(++idCounter, Type.GIFT, text, user, score,
                    speed, pitch, volume);

            // Decide the enqueue BEFORE letting the gift stop the current
            // speaker, so a gift that will be dropped never kills speech for
            // nothing. Only chat tasks are ever evicted; keep every gift. If
            // the queue is full of gifts, evict the new task itself instead of
            // a gift that was already accepted.
            if (queue.size() >= HARD_QUEUE_LIMIT) {
                Task weakestChat = weakestType(Type.CHAT);
                if (weakestChat != null && weakestChat.score < task.score) {
                    queue.remove(weakestChat);
                } else {
                    return Decision.DROPPED;
                }
            }

            queue.add(task);
            lock.notifyAll();
            if (maybePreempt(task)) return Decision.PREEMPTED;
            return current == null ? Decision.PLAYED : Decision.QUEUED;
        }
    }

    /** Stop whatever is speaking and clear the pending queue. */
    public void stop() {
        synchronized (lock) {
            queue.clear();
            interruptedByAlert = null;
            generation++;
            if (tts != null) {
                tts.stop();
            }
            current = null;
        }
    }

    public void shutdown() {
        stopped = true;
        synchronized (lock) {
            queue.clear();
            interruptedByAlert = null;
            generation++;
            lock.notifyAll();
        }
        if (tts != null) {
            tts.shutdown();
        }
        synchronized (lock) {
            pendingLatches.clear();
        }
        try {
            mixer.unregister(alertEffectListener);
        } catch (Exception ignored) {
        }
    }

    // ---- scheduling internals ----

    private void workerLoop() {
        while (!stopped) {
            Task task;
            long gen;
            synchronized (lock) {
                while (queue.isEmpty() && !stopped) {
                    try {
                        lock.wait(250);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (stopped) return;
                task = queue.poll();
                gen = generation;
            }
            speakSafely(task, gen);
        }
    }

    private void speakSafely(Task task, long gen) {
        waitForAlertBackoff();

        String uid = "tts_" + task.id;
        synchronized (lock) {
            current = task;
        }
        try {
            // Apply the voice only when it actually changed (engine calls are the
            // cheapest wins: a run of chats never calls the engine twice).
            if (Math.abs(lastAppliedSpeed - task.speed) > 0.001f
                    || Math.abs(lastAppliedPitch - task.pitch) > 0.001f) {
                tts.applyVoice(task.speed, task.pitch);
                lastAppliedSpeed = task.speed;
                lastAppliedPitch = task.pitch;
            }

            CountDownLatch latch = new CountDownLatch(1);
            synchronized (lock) {
                pendingLatches.put(uid, latch);
            }
            // Re-check: a concurrent stop()/shutdown() may have cancelled this
            // task between poll and speak() (generation), or right after
            // current=task (current). Speak only if the task is still ours.
            synchronized (lock) {
                if (stopped || generation != gen || current != task) {
                    pendingLatches.remove(uid);
                    latch.countDown();
                    return;
                }
            }
            boolean ok = tts.speak(task.text, uid, task.volume);
            if (!ok) {
                // Engine refused / not ready: don't wait out the full timeout.
                latch.countDown();
            }

            try {
                latch.await(UTTERANCE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                synchronized (lock) {
                    pendingLatches.remove(uid);
                    current = null;
                    // An alert effect interrupted THIS utterance: put it back at
                    // the front (boosted) so it resumes once the effect clears.
                    // The boost is applied once, so a stream of alerts can never
                    // inflate one task's score without bound (starvation).
                    Task resume = interruptedByAlert;
                    interruptedByAlert = null;
                    if (resume != null && resume.id == task.id) {
                        int newScore = task.alreadyBoosted
                                ? task.score
                                : task.score + REQUEUE_BOOST;
                        queue.add(new Task(++idCounter, task.type, task.text,
                                task.user, newScore, true,
                                task.speed, task.pitch, task.volume));
                    }
                    lock.notifyAll();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "speak failed", e);
            synchronized (lock) {
                pendingLatches.remove(uid);
                current = null;
                lock.notifyAll();
            }
        }
    }

    /**
     * Holds the start of a new utterance until any alert effect ringing right
     * now has finished, so speech never begins while a sound effect is audible.
     * Bounded so a stream of effects can never starve speech entirely.
     */
    private void waitForAlertBackoff() {
        long deadline = System.currentTimeMillis() + MAX_ALERT_BACKOFF_MS;
        while (!stopped) {
            long remain = mixer.speechBackoffMillis();
            if (remain <= 0) return;
            long sleep = Math.min(remain, 100L);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (System.currentTimeMillis() > deadline) return;
        }
    }

    /**
     * Called by the engine on onDone/onError. Releases the worker so it can pick
     * the next (highest-priority) task. A preempted utterance lands here too.
     */
    private void onUtteranceDone(String utteranceId) {
        if (utteranceId == null) return;
        synchronized (lock) {
            CountDownLatch latch = pendingLatches.remove(utteranceId);
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    /** Ask: is this new gift important enough to interrupt what is speaking now? */
    private boolean maybePreempt(Task candidate) {
        if (current == null || candidate.type != Type.GIFT) return false;
        long now = System.currentTimeMillis();
        if (now - lastPreemptAt < PREEMPT_DEBOUNCE_MS) return false;
        if (candidate.score - current.score < PREEMPT_MIN_MARGIN) return false;
        lastPreemptAt = now;
        if (tts != null) {
            tts.stop(); // onError -> latch countDown -> worker picks the gift next
        }
        return true;
    }

    private boolean hasQueuedChat(String user) {
        if (user == null) return false;
        long now = System.currentTimeMillis();
        for (Iterator<Task> it = queue.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.type == Type.CHAT && user.equals(t.user)
                    && now - t.createdAt < COALESCE_WINDOW_MS) {
                return true;
            }
        }
        return false;
    }

    private boolean overBudget() {
        float total = 0f;
        for (Task t : queue) {
            total += t.estimatedSeconds;
        }
        if (current != null) {
            total += current.estimatedSeconds * 0.5f;
        }
        return total > QUEUE_BUDGET_SECONDS;
    }

    private Task weakestType(Type type) {
        Task weakest = null;
        for (Task t : queue) {
            if (t.type == type && (weakest == null || t.score < weakest.score)) {
                weakest = t;
            }
        }
        return weakest;
    }

    private static int scoreComment(String text) {
        int len = text.length();
        // Longer, complete sentences are more valuable than one-word drive-bys.
        int quality = Math.min(400, Math.max(0, len - 6) * 10);
        return NORMAL_CHAT_BASE + quality;
    }

    private static int scoreGift(long diamonds, int count) {
        long d = Math.max(0, diamonds) * Math.max(1, count);
        long bonus = Math.min(GIFT_MAX_BONUS, d / 2);
        return GIFT_BASE_SCORE + (int) bonus;
    }

    private static float estimateSeconds(String text, float speed) {
        float rate = CHARS_PER_SECOND * Math.max(0.5f, speed);
        return Math.max(0.8f, text.length() / rate);
    }
}