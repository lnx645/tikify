package com.tiktoksoundalert.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.tiktoksoundalert.R;
import com.tiktoksoundalert.GiftSoundStore;
import com.tiktoksoundalert.SettingsManager;
import com.tiktoksoundalert.SettingsRepository;
import com.tiktoksoundalert.TikTokSoundApp;
import com.tiktoksoundalert.audio.GiftSoundManager;
import com.tiktoksoundalert.audio.TtsScheduler;
import com.tiktoksoundalert.db.AppDatabase;
import com.tiktoksoundalert.models.AlertEventRule;
import com.tiktoksoundalert.models.EventType;
import com.tiktoksoundalert.tiktok.models.TikTokCommentEvent;
import com.tiktoksoundalert.tiktok.models.TikTokConnectedEvent;
import com.tiktoksoundalert.tiktok.models.TikTokConnectingEvent;
import com.tiktoksoundalert.tiktok.models.TikTokDisconnectedEvent;
import com.tiktoksoundalert.tiktok.models.TikTokDebugEvent;
import com.tiktoksoundalert.tiktok.models.TikTokErrorEvent;
import com.tiktoksoundalert.tiktok.models.TikTokEvent;
import com.tiktoksoundalert.tiktok.models.TikTokGiftEvent;
import com.tiktoksoundalert.tiktok.TikTokLiveListener;
import com.tiktoksoundalert.tiktok.TikTokRoomResolver;
import com.tiktoksoundalert.tiktok.TikTokWebSocketClient;
import com.tiktoksoundalert.ui.MainActivity;

public class TikTokService extends Service {
    private static final String TAG = "TikTokService";

    public static final String ACTION_START = "com.tiktoksoundalert.START";
    public static final String ACTION_STOP = "com.tiktoksoundalert.STOP";
    public static final String ACTION_RECONNECT = "com.tiktoksoundalert.RECONNECT";
    public static final String ACTION_QUERY_STATUS = "com.tiktoksoundalert.QUERY_STATUS";

    public static final String EXTRA_HOSTNAME = "hostname";

    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_BROADCAST_PREFIX = "com.tiktoksoundalert.event.";

    public static final String BROADCAST_EVENT = "com.tiktoksoundalert.EVENT";
    public static final String EXTRA_EVENT = "event_json";

    public static final String BROADCAST_STATUS = "com.tiktoksoundalert.STATUS";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_ERROR = "error";
    public static final String STATUS_CONNECTING = "connecting";
    public static final String STATUS_CONNECTED = "connected";
    public static final String STATUS_DISCONNECTED = "disconnected";
    public static final String STATUS_ERROR = "error";

    private TikTokWebSocketClient webSocketClient;
    private TikTokRoomResolver roomResolver;
    private GiftSoundManager giftSoundManager;
    private TtsScheduler ttsScheduler;
    private SettingsManager settingsManager;

    private String hostName;
    private boolean isConnected = false;
    private LocalBroadcastManager broadcastManager;
    private GiftSoundStore giftSoundStore;

    private long settingsAccountId = 0L;

    private final java.util.ArrayDeque<String> recentCommentKeys = new java.util.ArrayDeque<>();
    private static final int MAX_RECENT_COMMENTS = 200;
    private static final java.util.regex.Pattern LETTER_SPAM_PATTERN =
            java.util.regex.Pattern.compile("(.)\\1{3,}");

    /** Last chat/gift TTS time per user, LRU-capped so long streams stay bounded. */
    private final java.util.Map<String, Long> lastChatTts =
            new java.util.LinkedHashMap<String, Long>(128, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<String, Long> eldest) {
                    return size() > 512;
                }
            };
    private final java.util.Map<String, Long> lastGiftTts =
            new java.util.LinkedHashMap<String, Long>(128, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<String, Long> eldest) {
                    return size() > 256;
                }
            };

    /** Users already read this session (for the once-per-user mode), LRU-capped. */
    private final java.util.Set<String> readUsers = java.util.Collections.newSetFromMap(
            new java.util.LinkedHashMap<String, Boolean>(1024, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > 2048;
                }
            });
    /** Host the in-memory dedup/read memory belongs to; cleared only on host change. */
    private String dedupHost;

    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long activeId = SettingsRepository.activeAccountId(context);
            if (settingsManager == null || settingsManager.accountId != activeId) {
                bindSettingsForHost(hostName);
            } else {
                settingsManager.reload();
            }
            applyTtsSettings();
            applyMixSettings();
            if (!settingsManager.isTtsEnabled() && ttsScheduler != null) {
                ttsScheduler.stop();
            }
            Log.d(TAG, "Settings changed -> reloaded for account "
                    + settingsManager.accountId);
        }
    };

    private android.os.HandlerThread debugThread;
    private android.os.Handler debugHandler;
    private java.io.PrintWriter debugWriter;

    private com.tiktoksoundalert.statistics.TopViewersReflector topViewersReflector =
            com.tiktoksoundalert.statistics.TopViewersReflector.getInstance();

    @Override
    public void onCreate() {
        super.onCreate();
        broadcastManager = LocalBroadcastManager.getInstance(this);
        giftSoundStore = new GiftSoundStore(this);
        giftSoundManager = new GiftSoundManager(this);

        broadcastManager.registerReceiver(settingsReceiver,
                new IntentFilter(SettingsRepository.ACTION_SETTINGS_CHANGED));

        initTTS();
        initSoundManager();
    }

    private void initTTS() {
        ttsScheduler = new TtsScheduler(this,
                this::applyTtsSettings,
                error -> Log.e(TAG, "TTS error: " + error));
    }

    private void initSoundManager() {
        giftSoundManager.loadBuiltinSounds();

        // Load user-uploaded custom sounds
        for (java.util.Map.Entry<String, String> entry : giftSoundStore.getCustomSounds().entrySet()) {
            giftSoundManager.loadCustomSoundFromPath(entry.getKey(), entry.getValue());
        }

        giftSoundManager.setOnSoundLoadedListener(new GiftSoundManager.OnSoundLoadedListener() {
            @Override
            public void onSoundLoaded() {
                Log.d(TAG, "Sounds loaded");
            }

            @Override
            public void onSoundError(String error) {
                Log.e(TAG, "Sound error: " + error);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTikTokConnection();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_QUERY_STATUS.equals(action)) {
            if (isConnected) {
                emitStatus(STATUS_CONNECTED, hostName);
            } else if (hostName != null) {
                emitStatus(STATUS_DISCONNECTED, "Idle");
            }
            return START_NOT_STICKY;
        }

        String newHost = intent.getStringExtra(EXTRA_HOSTNAME);
        if (newHost != null && !newHost.equals(hostName)) {
            hostName = newHost;
            bindSettingsForHost(hostName);
            applyMixSettings();
        }

        startInForeground();
        connectToRoom();
        return START_STICKY;
    }

    private void bindSettingsForHost(String host) {
        // Always bind to the ACTIVE account: every settings screen writes to the
        // active account, so the service must read from it to apply changes live.
        com.tiktoksoundalert.db.AccountDao accountDao =
                AppDatabase.get(this).accountDao();
        long accountId = 0L;
        com.tiktoksoundalert.db.Account active = accountDao.getActiveNow();
        if (active != null) {
            accountId = active.id;
        } else if (host != null && !host.isEmpty()) {
            // No active account: fall back to the connected host's account if saved.
            com.tiktoksoundalert.db.Account account =
                    accountDao.findByNickname(host);
            if (account != null) {
                accountId = account.id;
            }
        }
        settingsAccountId = accountId;
        settingsManager = new SettingsManager(this, accountId);
    }

    private void applyTtsSettings() {
        if (settingsManager == null || ttsScheduler == null || !ttsScheduler.isReady()) {
            return;
        }
        ttsScheduler.setLanguage(settingsManager.getTtsLanguage());
    }

    /** Alert effects pause in-flight TTS and resume it when the effect ends. */
    private void applyMixSettings() {
        if (ttsScheduler == null) return;
        ttsScheduler.setInterruptOnAlert(settingsManager == null
                || settingsManager.isAlertInterruptsTts());
    }

    private void startInForeground() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0);

        Intent stopIntent = new Intent(this, TikTokService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 1, stopIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification notification = new NotificationCompat.Builder(this, TikTokSoundApp.CHANNEL_ID_SERVICE)
                .setContentTitle("TikTok Sound Alerts")
                .setContentText(hostName != null ? "Connected to @" + hostName : "Not connected")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(R.drawable.ic_stop, "Stop", stopPending)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void connectToRoom() {
        if (hostName == null || hostName.trim().isEmpty()) {
            Log.w(TAG, "No hostname provided");
            return;
        }
        if (topViewersReflector != null) {
            topViewersReflector.clear();
        }
        if (ttsScheduler != null) {
            ttsScheduler.stop();
        }
        // De-dup memory survives reconnects: TikTok websocket reconnects replay
        // recent comments, so clearing on every connect made the app re-read them.
        if (hostName != null && !hostName.equals(dedupHost)) {
            recentCommentKeys.clear();
            readUsers.clear();
            lastChatTts.clear();
            lastGiftTts.clear();
            dedupHost = hostName;
        }
        if (settingsManager == null) {
            bindSettingsForHost(hostName);
        } else {
            settingsManager.reload();
        }
        applyTtsSettings();
        applyMixSettings();

        if (roomResolver == null) {
            roomResolver = new TikTokRoomResolver();
        }

        emitEvent(new TikTokConnectingEvent(hostName));
        emitStatus(STATUS_CONNECTING, hostName);

        roomResolver.resolveRoom(hostName, new TikTokRoomResolver.RoomInfoCallback() {
            @Override
            public void onSuccess(TikTokRoomResolver.RoomInfo roomInfo) {
                Log.d(TAG, "Room resolved - ID: " + roomInfo.roomId + " User: " + roomInfo.userId);
                establishWebSocket(roomInfo);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Room resolution failed: " + error);
                emitEvent(new TikTokErrorEvent(error));
                emitStatus(STATUS_ERROR, error);
            }
        });
    }

    private void establishWebSocket(TikTokRoomResolver.RoomInfo roomInfo) {
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }

        webSocketClient = new TikTokWebSocketClient(new TikTokLiveListener() {
            @Override
            public void onConnecting() {
                isConnected = false;
            }

            @Override
            public void onConnected(String roomId, String host) {
                isConnected = true;
                emitEvent(new TikTokConnectedEvent(host, roomId));
                emitStatus(STATUS_CONNECTED, host);
                updateNotification("Connected to @" + host);
            }

            @Override
            public void onDisconnected(String reason) {
                isConnected = false;
                emitEvent(new TikTokDisconnectedEvent(reason));
                emitStatus(STATUS_DISCONNECTED, reason);
                updateNotification("Disconnected");
            }

            @Override
            public void onError(Exception error) {
                isConnected = false;
                emitEvent(new TikTokErrorEvent(error.getMessage()));
                emitStatus(STATUS_ERROR, error.getMessage());
                updateNotification("Disconnected");
            }

            @Override
            public void onEvent(TikTokEvent event) {
                handleLiveEvent(event);
            }
        });

        webSocketClient.connect(hostName, roomInfo.roomId, roomInfo.userId);
    }

    private void handleLiveEvent(TikTokEvent event) {
        if (event.getType() == com.tiktoksoundalert.models.EventType.DEBUG) {
            writeDebugLog(((TikTokDebugEvent) event).getMessageText());
            return;
        }
        if (topViewersReflector != null) {
            topViewersReflector.onEvent(event);
        }

        switch (event.getType()) {
            case GIFT:
                handleGift((TikTokGiftEvent) event);
                break;
            case COMMENT:
                handleComment((TikTokCommentEvent) event);
                break;
            case FOLLOW:
                playAlertEventSound(AlertEventRule.TYPE_FOLLOW);
                break;
            case SHARE:
                playAlertEventSound(AlertEventRule.TYPE_SHARE);
                break;
            default:
                break;
        }
    }

    private void handleGift(TikTokGiftEvent event) {
        String giftName = event.getGiftName();
        AlertEventRule rule = null;
        if (giftName != null) {
            rule = settingsManager.getAlertQueueRuleForGift(giftName);
        }
        if (rule == null) {
            rule = settingsManager.getAlertQueueRule(AlertEventRule.TYPE_GIFT);
        }
        if (rule != null) {
            playAlertEventSound(rule);
        }
        speakGift(event);
    }

    /**
     * Plays the sound configured for an event type, if the global sound switch,
     * the "Queue Sound Alert (By Event)" master switch and the specific rule are
     * all enabled.
     */
    private void playAlertEventSound(String eventType) {
        if (settingsManager == null) return;
        AlertEventRule rule = settingsManager.getAlertQueueRule(eventType);
        if (rule != null) {
            playAlertEventSound(rule);
        }
    }

    private void playAlertEventSound(AlertEventRule rule) {
        if (settingsManager == null) return;
        if (!settingsManager.isGiftSoundEnabled()) return;
        if (!settingsManager.isAlertQueueEnabled()) return;
        if (rule == null || !rule.enabled || !rule.hasSound()) return;

        giftSoundManager.playSound(rule.sound, rule.volume);
    }

    private void speakGift(TikTokGiftEvent event) {
        if (!settingsManager.isGiftTtsEnabled()) return;
        if (ttsScheduler == null || !ttsScheduler.isReady()) return;

        long diamonds = event.getDiamondCount();
        int count = Math.max(1, event.getGiftCount());
        if (diamonds * count < settingsManager.getGiftTtsMinDiamonds()) return;

        String username = event.getUser() != null ? event.getUser().getName() : "";
        String lower = username.toLowerCase(java.util.Locale.ROOT);
        long now = System.currentTimeMillis();
        int cooldown = settingsManager.getGiftTtsCooldownMs();
        if (cooldown > 0) {
            Long last = lastGiftTts.get(lower);
            if (last != null && (now - last) < cooldown) return;
        }

        String text = settingsManager.getGiftTtsTemplate()
                .replace("{username}", username == null ? "" : username)
                .replace("{giftname}", event.getGiftName())
                .replace("{count}", String.valueOf(count))
                .replace("{diamonds}", String.valueOf(diamonds));

        ttsScheduler.submitGift(text, lower,
                diamonds, count,
                settingsManager.getGiftTtsSpeed(), settingsManager.getGiftTtsPitch(),
                settingsManager.getGiftTtsVolume());
        lastGiftTts.put(lower, now);
    }

    private void handleComment(TikTokCommentEvent event) {
        String username = event.getUser().getName();
        String comment = event.getText();

        String key = (username == null ? "" : username).toLowerCase(java.util.Locale.ROOT)
                + "|" + (comment == null ? "" : comment.trim()).toLowerCase(java.util.Locale.ROOT);
        if (recentCommentKeys.contains(key)) {
            Log.d(TAG, "Skipping replayed comment: " + key);
            return;
        }
        if (recentCommentKeys.size() >= MAX_RECENT_COMMENTS) {
            recentCommentKeys.pollFirst();
        }
        recentCommentKeys.addLast(key);

        if (settingsManager == null || !settingsManager.isTtsEnabled()) return;
        if (ttsScheduler == null || !ttsScheduler.isReady()) {
            Log.w(TAG, "TTS not initialized, skipping comment");
            return;
        }

        String normalized = applyCommentFilters(username, comment);
        if (normalized == null) return;

        boolean priority = containsAny(normalized, settingsManager.getPriorityWords());
        long now = System.currentTimeMillis();
        String lower = username == null ? "" : username.toLowerCase(java.util.Locale.ROOT);

        if (!priority) {
            if (settingsManager.isOncePerUserEnabled() && readUsers.contains(lower)) {
                Log.d(TAG, "Skipping repeat message from user " + lower);
                return;
            }
            int cooldown = settingsManager.getTtsCooldownMs();
            if (cooldown > 0) {
                Long last = lastChatTts.get(lower);
                if (last != null && (now - last) < cooldown) return;
            }
        }

        String text = settingsManager.getTtsTemplate()
                .replace("{username}", username == null ? "" : username)
                .replace("{comment}", normalized);
        readUsers.add(lower);
        TtsScheduler.Decision decision = ttsScheduler.submitComment(
                text, lower,
                settingsManager.getTtsSpeed(), settingsManager.getTtsPitch(),
                settingsManager.getChatTtsVolume(),
                priority);
        if (decision == TtsScheduler.Decision.BUSY) {
            Log.w(TAG, "Scheduler busy, comment dropped");
            return;
        }
        lastChatTts.put(lower, now);
    }

    private String applyCommentFilters(String username, String comment) {
        if (comment == null) return null;
        if (!passesCommand(comment)) return null;
        String text = stripCommand(comment);
        text = text.trim();
        if (text.isEmpty()) return null;

        if (!passesAllowedUser(username)) return null;

        if (settingsManager.isLetterSpamBlocked() && hasLetterSpam(text)) return null;

        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String word : settingsManager.getBlockedWords()) {
            if (lower.contains(word)) return null;
        }

        int maxLen = settingsManager.getCommentTtsMaxLength();
        if (maxLen > 0 && text.length() > maxLen) return null;
        return text;
    }

    private boolean passesCommand(String comment) {
        String cmd = settingsManager.getTtsCommand();
        if (cmd == null || cmd.isEmpty()) return true;
        if ("both".equals(cmd)) return comment.startsWith(".") || comment.startsWith("/");
        return comment.startsWith(cmd);
    }

    private String stripCommand(String comment) {
        String cmd = settingsManager.getTtsCommand();
        if (cmd == null || cmd.isEmpty()) return comment;
        if ("both".equals(cmd)) {
            if (comment.startsWith(".")) return comment.substring(1);
            if (comment.startsWith("/")) return comment.substring(1);
            return comment;
        }
        return comment.startsWith(cmd) ? comment.substring(cmd.length()) : comment;
    }

    private boolean passesAllowedUser(String username) {
        if (!"favorites".equals(settingsManager.getAllowedUsersMode())) return true;
        if (username == null) return false;
        return settingsManager.getFavoriteUsers()
                .contains(username.toLowerCase(java.util.Locale.ROOT));
    }

    private boolean hasLetterSpam(String text) {
        return LETTER_SPAM_PATTERN.matcher(text).find();
    }

    private boolean containsAny(String text, java.util.Set<String> words) {
        if (words == null || words.isEmpty()) return false;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String word : words) {
            if (lower.contains(word)) return true;
        }
        return false;
    }

    private void writeDebugLog(String text) {
        try {
            if (debugThread == null) {
                debugThread = new android.os.HandlerThread("debug-log");
                debugThread.start();
                debugHandler = new android.os.Handler(debugThread.getLooper());
            }
            if (debugHandler != null) {
                debugHandler.post(() -> appendDebug(text));
            }
        } catch (Exception ignored) {
        }
    }

    private void appendDebug(String text) {
        try {
            if (debugWriter == null) {
                debugWriter = new java.io.PrintWriter(new java.io.BufferedWriter(
                        new java.io.FileWriter(new java.io.File(getFilesDir(), "debug.log"), true)));
            }
            debugWriter.println(android.text.format.DateFormat.format("HH:mm:ss", new java.util.Date())
                    + " " + text);
            debugWriter.flush();
        } catch (Exception ignored) {
        }
    }

    private void emitEvent(TikTokEvent event) {
        Intent broadcast = new Intent(BROADCAST_EVENT);
        broadcast.putExtra(EXTRA_EVENT, event.toJsonString());
        broadcastManager.sendBroadcast(broadcast);
    }

    private void emitStatus(String status, String detail) {
        Intent broadcast = new Intent(BROADCAST_STATUS);
        broadcast.putExtra(EXTRA_STATUS, status);
        if (detail != null) {
            broadcast.putExtra(EXTRA_ERROR, detail);
        }
        broadcast.putExtra(EXTRA_HOSTNAME, hostName);
        broadcastManager.sendBroadcast(broadcast);
    }

    private void updateNotification(String text) {
        // Since the notification is ongoing, this is best-effort
        Log.d(TAG, "Notification update: " + text);
    }

    private void stopTikTokConnection() {
        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient = null;
        }
        emitStatus(STATUS_DISCONNECTED, "Stopped");
    }

    /** Top gifters by total diamonds, newest data. */
    public java.util.List<com.tiktoksoundalert.statistics.UserStats> getTopGifters(int limit) {
        return topViewersReflector != null ? topViewersReflector.topGifters(limit)
                : new java.util.ArrayList<>();
    }

    /** Top chat contributors by comment count. */
    public java.util.List<com.tiktoksoundalert.statistics.UserStats> getTopChat(int limit) {
        return topViewersReflector != null ? topViewersReflector.topChat(limit)
                : new java.util.ArrayList<>();
    }

    /** Top likers by like count. */
    public java.util.List<com.tiktoksoundalert.statistics.UserStats> getTopLikes(int limit) {
        return topViewersReflector != null ? topViewersReflector.topLikes(limit)
                : new java.util.ArrayList<>();
    }

    /** Approximate most-active viewers in the room. */
    public java.util.List<com.tiktoksoundalert.statistics.UserStats> getTopViewers(int limit) {
        return topViewersReflector != null ? topViewersReflector.topViewers(limit)
                : new java.util.ArrayList<>();
    }

    @Override
    public void onDestroy() {
        stopTikTokConnection();
        if (broadcastManager != null) {
            broadcastManager.unregisterReceiver(settingsReceiver);
        }
        if (giftSoundManager != null) giftSoundManager.release();
        if (ttsScheduler != null) ttsScheduler.shutdown();
        try {
            if (debugWriter != null) {
                debugWriter.flush();
                debugWriter.close();
                debugWriter = null;
            }
            if (debugThread != null) {
                debugThread.quitSafely();
                debugThread = null;
                debugHandler = null;
            }
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
