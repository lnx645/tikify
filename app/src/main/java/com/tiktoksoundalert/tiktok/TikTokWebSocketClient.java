package com.tiktoksoundalert.tiktok;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tiktoksoundalert.tiktok.models.TikTokEvent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Transport layer for the TikTok LIVE WebCast push protocol.
 *
 * Responsibilities, and only these:
 *  - ask the sign server for a signed WebSocket URL + wrss route,
 *  - open the WebSocket, enter the room and keep it alive with heartbeats,
 *  - decode push frames, uncompress gzipped bodies, iterate the inner messages,
 *  - hand each Webcast*Message payload to {@link MessageParser} and forward the
 *    resulting {@link TikTokEvent} to the listener.
 *
 * Wire-format encoding/parsing lives in {@link ProtobufCodec}; message decoding
 * into domain events lives in {@link MessageParser}. A watchdog disconnects when
 * the server stops sending data (stream ended).
 */
public class TikTokWebSocketClient {
    private static final String TAG = "TikTokWebSocket";

    private static final String SIGN_FETCH_URL =
            "https://api.eulerstream.com/webcast/fetch?client=ttlive-python";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final long HEARTBEAT_INTERVAL_MS = 10_000;
    private static final long STALE_MS = 45_000;
    private static final long RECONNECT_DELAY_MS = 5_000;

    private final OkHttpClient httpClient;
    private final TikTokLiveListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler hbHandler = new Handler(Looper.getMainLooper());
    private final Handler staleHandler = new Handler(Looper.getMainLooper());
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());

    private final Runnable hbRunnable = new Runnable() {
        @Override
        public void run() {
            sendHeartbeat();
            if (!stopped) {
                hbHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        }
    };
    private final Runnable staleCheck = new Runnable() {
        @Override
        public void run() {
            if (connected && lastFrameTime > 0
                    && System.currentTimeMillis() - lastFrameTime > STALE_MS) {
                Log.d(TAG, "No data for " + STALE_MS + "ms - stream ended, disconnecting");
                // Stream really ended: do NOT auto-reconnect, the live is gone.
                reconnectOnClose = false;
                notifyDisconnected("Stream ended");
                disconnect();
            } else if (!stopped) {
                staleHandler.postDelayed(this, STALE_MS / 3);
            }
        }
    };
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            reconnect();
        }
    };

    private volatile WebSocket webSocket;
    private volatile boolean stopped = false;
    private volatile boolean connected = false;
    private volatile long lastFrameTime = 0;
    /** Set false when the stream ended on purpose so we never retry a dead live. */
    private volatile boolean reconnectOnClose = true;
    private String hostName;
    private String roomId;
    private long hbSeq = 1;

    public TikTokWebSocketClient(TikTokLiveListener listener) {
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void connect(String hostName, String roomId, String userId) {
        this.hostName = hostName;
        this.roomId = roomId;
        this.stopped = false;
        this.hbSeq = 1;
        this.reconnectOnClose = true;

        notifyConnecting();

        new Thread(() -> {
            try {
                connectSigned(roomId);
            } catch (Exception e) {
                Log.e(TAG, "Connect failed", e);
                notifyError(new RuntimeException("Connect failed: " + e.getMessage()));
                scheduleReconnect();
            }
        }).start();
    }

    public void disconnect() {
        stopped = true;
        hbHandler.removeCallbacks(hbRunnable);
        staleHandler.removeCallbacks(staleCheck);
        reconnectHandler.removeCallbacks(reconnectRunnable);
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnecting");
            webSocket = null;
        }
        connected = false;
    }

    public synchronized void reconnect() {
        if (stopped || !reconnectOnClose) return;
        if (hostName != null && roomId != null) {
            disconnect();
            connect(hostName, roomId, null);
        }
    }

    private void scheduleReconnect() {
        if (stopped || !reconnectOnClose) return;
        reconnectHandler.removeCallbacks(reconnectRunnable);
        reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        Log.d(TAG, "Scheduling auto-reconnect in " + RECONNECT_DELAY_MS + "ms");
    }

    public boolean isConnected() {
        return connected;
    }

    // ---- connection setup ----

    private void connectSigned(String room) throws Exception {
        String signUrl = SIGN_FETCH_URL
                + "&room_id=" + room
                + "&user_agent=" + URLEncoder.encode(USER_AGENT, "UTF-8")
                + "&client_enter=true"
                + "&platform=web"
                + "&tt_target_idc=useast1a";
        Request fetchReq = new Request.Builder()
                .url(signUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.tiktok.com/")
                .header("Origin", "https://www.tiktok.com")
                .get()
                .build();

        String cookie = "";
        String pushServer = null;
        String wrss = null;
        try (Response resp = httpClient.newCall(fetchReq).execute()) {
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("Sign server HTTP " + resp.code());
            }
            byte[] body = resp.body() != null ? resp.body().bytes() : new byte[0];
            cookie = resp.header("x-set-tt-cookie");
            if (cookie == null) cookie = "";

            Map<Long, List<byte[]>> rf = ProtobufCodec.parse(body);
            pushServer = ProtobufCodec.str(ProtobufCodec.first(rf, 10));
            List<byte[]> routes = rf.get(7L);
            if (routes != null) {
                for (byte[] e : routes) {
                    Map<Long, List<byte[]>> ef = ProtobufCodec.parse(e);
                    String k = ProtobufCodec.str(ProtobufCodec.first(ef, 1));
                    String v = ProtobufCodec.str(ProtobufCodec.first(ef, 2));
                    if (!v.isEmpty() && "wrss".equals(k)) {
                        wrss = v;
                    }
                }
            }
            Log.d(TAG, "fetch ok pushServer=" + pushServer + " wrssPresent=" + (wrss != null));
        }

        if (pushServer == null || pushServer.isEmpty() || wrss == null || wrss.isEmpty()) {
            throw new IllegalStateException("Sign server did not return a usable route (stream offline?)");
        }

        String wsUrl = buildWsUrl(pushServer, wrss, room);
        String cookieStr = "tt-target-idc=useast1a; " + cookie + ";";
        openSocket(wsUrl, cookieStr, room);
    }

    private String buildWsUrl(String pushServer, String wrss, String room) {
        StringBuilder u = new StringBuilder(pushServer).append('?');
        append(u, "wrss", wrss);
        append(u, "aid", "1988");
        append(u, "app_language", "en");
        append(u, "app_name", "tiktok_web");
        append(u, "browser_platform", "Win32");
        append(u, "browser_language", "en-US");
        append(u, "browser_name", "Chrome");
        append(u, "browser_version", "124.0.0.0");
        append(u, "browser_online", "true");
        append(u, "cookie_enabled", "true");
        append(u, "tz_name", "Asia/Jakarta");
        append(u, "device_platform", "web");
        append(u, "identity", "audience");
        append(u, "live_id", "12");
        append(u, "sup_ws_ds_opt", "1");
        append(u, "update_version_code", "2.0.0");
        append(u, "version_code", "180800");
        append(u, "client_enter", "1");
        append(u, "ws_direct", "1");
        append(u, "did_rule", "3");
        append(u, "webcast_language", "en");
        append(u, "screen_height", "1080");
        append(u, "screen_width", "1920");
        append(u, "heartbeat_duration", "10000");
        append(u, "resp_content_type", "protobuf");
        append(u, "history_comment_count", "6");
        append(u, "last_rtt", "150");
        append(u, "room_id", room);
        append(u, "compress", "gzip");
        u.append("&version_code=270000");
        return u.toString();
    }

    private static void append(StringBuilder u, String k, String v) {
        try {
            u.append('&').append(k).append('=').append(URLEncoder.encode(v, "UTF-8"));
        } catch (Exception e) {
            u.append('&').append(k).append('=').append(v);
        }
    }

    private void openSocket(String wsUrl, String cookieStr, String room) {
        Request request = new Request.Builder()
                .url(wsUrl)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookieStr)
                .header("Origin", "https://www.tiktok.com")
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                connected = true;
                Log.d(TAG, "WebSocket opened");
                long roomIdL = safeLong(room);
                ws.send(ByteString.of(ProtobufCodec.pushFrame("im_enter_room", ProtobufCodec.imEnterRoom(roomIdL))));
                ws.send(ByteString.of(ProtobufCodec.pushFrame("hb", ProtobufCodec.heartbeat(roomIdL, hbSeq++))));
                hbHandler.postDelayed(hbRunnable, HEARTBEAT_INTERVAL_MS);
                lastFrameTime = System.currentTimeMillis();
                staleHandler.postDelayed(staleCheck, STALE_MS / 3);
                notifyConnected();
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                handleBinary(ws, bytes.toByteArray());
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "closing: " + code + " " + reason);
                ws.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                if (ws != webSocket) return; // stale socket from a previous attempt
                connected = false;
                hbHandler.removeCallbacks(hbRunnable);
                staleHandler.removeCallbacks(staleCheck);
                Log.d(TAG, "closed: " + code + " " + reason);
                notifyDisconnected("closed: " + reason);
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                if (ws != webSocket) return; // stale socket from a previous attempt
                connected = false;
                hbHandler.removeCallbacks(hbRunnable);
                staleHandler.removeCallbacks(staleCheck);
                Log.e(TAG, "failure", t);
                notifyError(new RuntimeException("WebSocket error: " + t.getMessage()));
                scheduleReconnect();
            }
        });
    }

    // ---- frame handling ----

    private void handleBinary(WebSocket ws, byte[] raw) {
        if (raw.length == 0) return;
        lastFrameTime = System.currentTimeMillis();
        try {
            Map<Long, List<byte[]>> frame = ProtobufCodec.parse(raw);
            String type = ProtobufCodec.str(ProtobufCodec.first(frame, 7));
            byte[] payload = ProtobufCodec.first(frame, 8);

            if (type == null) {
                return;
            }
            if ("hb".equals(type)) {
                return; // server keepalive; our hbRunnable drives client heartbeats
            }
            if ("im_enter_room_resp".equals(type)) {
                Log.d(TAG, "enter room acknowledged");
                return;
            }
            if (!"msg".equals(type)) {
                return;
            }

            handleMsgFrame(ws, frame, payload);
        } catch (Throwable e) {
            Log.e(TAG, "failed to parse frame", e);
        }
    }

    private void handleMsgFrame(WebSocket ws, Map<Long, List<byte[]>> frame, byte[] payload) {
        boolean gz = hasGzipHeader(frame.get(9L)) || isGzipMagic(payload);
        byte[] body = gz ? ProtobufCodec.gunzip(payload) : payload;
        Map<Long, List<byte[]>> resp = ProtobufCodec.parse(body);
        List<byte[]> msgs = resp.get(1L);
        if ((msgs == null || msgs.isEmpty()) && isGzipMagic(payload)) {
            body = ProtobufCodec.gunzip(payload);
            resp = ProtobufCodec.parse(body);
            msgs = resp.get(1L);
        }
        if (msgs == null || msgs.isEmpty()) {
            msgs = resp.get(2L);
        }

        acknowledgeIfNeeded(ws, frame, resp);

        if (msgs == null) return;
        for (byte[] m : msgs) {
            Map<Long, List<byte[]>> mf = ProtobufCodec.parse(m);
            String method = ProtobufCodec.str(ProtobufCodec.first(mf, 1));
            byte[] mp = ProtobufCodec.first(mf, 2);
            if (hasGzipHeader(mf.get(9L)) || isGzipMagic(mp)) {
                mp = ProtobufCodec.gunzip(mp);
            }
            dispatch(method, mp);
        }
    }

    private void acknowledgeIfNeeded(WebSocket ws, Map<Long, List<byte[]>> frame,
                                     Map<Long, List<byte[]>> resp) {
        if (!resp.containsKey(9L)) return;
        long needAck = ProtobufCodec.varint(ProtobufCodec.first(resp, 9));
        if (needAck == 0) return;
        long logId = ProtobufCodec.varint(ProtobufCodec.first(frame, 2));
        byte[] ie = ProtobufCodec.str(ProtobufCodec.first(resp, 5)).getBytes(StandardCharsets.UTF_8);
        if (ie.length == 0) ie = "-".getBytes(StandardCharsets.UTF_8);
        ws.send(ByteString.of(ProtobufCodec.encodeAck(logId, ie)));
    }

    private void dispatch(String method, byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }
        TikTokEvent event = MessageParser.parse(method, payload);
        if (event != null) {
            emitEvent(event);
        }
    }

    private boolean hasGzipHeader(List<byte[]> headers) {
        if (headers == null) return false;
        for (byte[] h : headers) {
            Map<Long, List<byte[]>> hf = ProtobufCodec.parse(h);
            if ("compress_type".equals(ProtobufCodec.str(ProtobufCodec.first(hf, 1)))
                    && "gzip".equals(ProtobufCodec.str(ProtobufCodec.first(hf, 2)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGzipMagic(byte[] b) {
        return b != null && b.length >= 2 && (b[0] & 0xff) == 0x1f && (b[1] & 0xff) == 0x8b;
    }

    private void sendHeartbeat() {
        try {
            if (webSocket != null && connected) {
                long room = roomId == null ? 0 : safeLong(roomId);
                webSocket.send(ByteString.of(ProtobufCodec.pushFrame("hb", ProtobufCodec.heartbeat(room, hbSeq++))));
            }
        } catch (Exception e) {
            Log.e(TAG, "hb send failed", e);
        }
    }

    private static long safeLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0L;
        }
    }

    // ---- listener notifications (all marshalled to the main thread) ----

    private void emitEvent(TikTokEvent event) {
        mainHandler.post(() -> {
            if (listener != null) listener.onEvent(event);
        });
    }

    private void notifyConnecting() {
        mainHandler.post(() -> {
            if (listener != null) listener.onConnecting();
        });
    }

    private void notifyConnected() {
        mainHandler.post(() -> {
            if (listener != null) listener.onConnected(roomId, hostName);
        });
    }

    private void notifyDisconnected(String reason) {
        mainHandler.post(() -> {
            if (listener != null) listener.onDisconnected(reason);
        });
    }

    private void notifyError(RuntimeException error) {
        mainHandler.post(() -> {
            if (listener != null) listener.onError(error);
        });
    }
}