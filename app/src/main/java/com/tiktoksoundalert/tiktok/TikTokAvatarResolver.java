package com.tiktoksoundalert.tiktok;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Resolves a TikTok creator's profile avatar URL so the app can show the active
 * host's picture in the drawer and on the account list.
 *
 * <p>Primary source: the anonymous TikTok web profile API
 *   https://www.tiktok.com/api/user/detail/?...&uniqueId=<user>&aid=1988
 * which returns userInfo.user.avatarLarger even when the creator is not live.
 * Falls back to the live-room API used by {@link TikTokRoomResolver}.
 */
public class TikTokAvatarResolver {
    private static final String TAG = "TikTokAvatarResolver";

    private static final String DETAIL_URL =
            "https://www.tiktok.com/api/user/detail/" +
            "?uniqueId=%s&aid=1988&app_name=tiktok_web&device_platform=web" +
            "&browser_language=en&browser_platform=Win32&browser_name=Mozilla" +
            "&os=windows&region=ID&priority_region=ID&language=en&appType=normal";
    private static final String ROOM_URL =
            "https://www.tiktok.com/api-live/user/room/" +
            "?aid=1988&app_language=en&app_name=tiktok_web&device_platform=web" +
            "&browser_language=en&browser_name=Mozilla&browser_online=true" +
            "&browser_platform=Win32&channel=tiktok_web&cookie_enabled=true" +
            "&region=US&priority_region=US&tz_name=America/New_York" +
            "&webcast_language=en&uniqueId=%s&sourceType=54";

    private static final int CACHE_MAX_ENTRIES = 24;

    private static final Map<String, String> URL_CACHE = new LinkedHashMap<String, String>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };

    public interface Callback {
        void onUrl(String avatarUrl);
        void onError();
    }

    private final OkHttpClient client;
    private final TikTokRoomResolver.UserAgentProvider uaProvider;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public TikTokAvatarResolver() {
        this(new TikTokRoomResolver.UserAgentProvider());
    }

    public TikTokAvatarResolver(TikTokRoomResolver.UserAgentProvider uaProvider) {
        this.uaProvider = uaProvider;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /** Returns the cached avatar URL for a nickname, or null if not fetched yet. */
    public synchronized static String cached(String nickname) {
        return nickname == null ? null : URL_CACHE.get(normalize(nickname));
    }

    /**
     * Fetch an avatar URL for the nickname. If a URL is already cached it is
     * returned immediately via the callback; otherwise the network is queried in
     * the background and the callback fires on the main thread.
     */
    public void resolve(String nickname, Callback callback) {
        final String key = normalize(nickname);
        if (key == null) {
            callback.onError();
            return;
        }
        String hit = cached(key);
        if (hit != null) {
            deliver(callback, hit);
            return;
        }

        new Thread(() -> {
            String url = fetch(key);
            if (url == null) {
                mainHandler.post(() -> callback.onError());
            } else {
                synchronized (TikTokAvatarResolver.class) {
                    URL_CACHE.put(key, url);
                }
                deliver(callback, url);
            }
        }).start();
    }

    private void deliver(Callback callback, String url) {
        mainHandler.post(() -> callback.onUrl(url));
    }

    private String fetch(String nickname) {
        try {
            String detail = tryUserDetail(nickname);
            if (detail != null) return detail;
            return tryLiveRoom(nickname);
        } catch (Exception e) {
            Log.w(TAG, "resolve failed for @" + nickname + ": " + e.getMessage());
            return null;
        }
    }

    private String tryUserDetail(String nickname) {
        String url = String.format(DETAIL_URL, nickname);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", uaProvider.get())
                .header("Accept", "application/json")
                .header("Referer", "https://www.tiktok.com/@" + nickname)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            String body = response.body().string();
            JSONObject root = new JSONObject(body);
            JSONObject userInfo = root.optJSONObject("userInfo");
            if (userInfo == null) return null;
            JSONObject user = userInfo.optJSONObject("user");
            if (user == null) return null;
            String avatar = firstNonEmpty(user.optString("avatarLarger"),
                    user.optString("avatarMedium"), user.optString("avatarThumb"));
            if (avatar == null || avatar.isEmpty()) return null;
            Log.d(TAG, "detail avatar for @" + nickname + ": " + avatar);
            return avatar;
        } catch (Exception e) {
            Log.w(TAG, "user/detail failed: " + e.getMessage());
            return null;
        }
    }

    private String tryLiveRoom(String nickname) {
        String url = String.format(ROOM_URL, nickname);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", uaProvider.get())
                .header("Accept", "application/json")
                .header("Referer", "https://www.tiktok.com/" + nickname + "/live")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            String body = response.body().string();
            JSONObject root = new JSONObject(body);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return null;
            JSONObject user = data.optJSONObject("user");
            if (user == null) return null;
            String avatar = firstNonEmpty(user.optString("avatarThumb"),
                    user.optString("avatarMedium"), user.optString("avatarLarger"));
            if (avatar == null || avatar.isEmpty()) return null;
            Log.d(TAG, "room avatar for @" + nickname + ": " + avatar);
            return avatar;
        } catch (Exception e) {
            Log.w(TAG, "live/user/room failed: " + e.getMessage());
            return null;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return null;
    }

    private static String normalize(String nickname) {
        if (nickname == null) return null;
        String trimmed = nickname.trim().replace("@", "").toLowerCase(java.util.Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}