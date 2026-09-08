package com.tiktoksoundalert.tiktok;

import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Resolves a TikTok creator's live room id.
 *
 * Primary source: the anonymous TikTok web endpoint
 *   https://www.tiktok.com/api-live/user/room/?...&uniqueId=<user>&sourceType=54
 * which reliably returns user.roomId for a live creator. Falls back to scraping
 * the public live page if the API endpoint is blocked.
 */
public class TikTokRoomResolver {
    private static final String TAG = "TikTokRoomResolver";

    private static final String API_URL =
            "https://www.tiktok.com/api-live/user/room/" +
            "?aid=1988&app_language=en&app_name=tiktok_web&device_platform=web" +
            "&browser_language=en&browser_name=Mozilla&browser_online=true" +
            "&browser_platform=Win32&channel=tiktok_web&cookie_enabled=true" +
            "&region=US&priority_region=US&tz_name=America/New_York" +
            "&webcast_language=en&uniqueId=%s&sourceType=54";
    private static final String LIVE_PAGE_URL = "https://www.tiktok.com/@%s/live";

    private final OkHttpClient client;
    private final UserAgentProvider uaProvider;

    public TikTokRoomResolver() {
        this(new UserAgentProvider());
    }

    public TikTokRoomResolver(UserAgentProvider uaProvider) {
        this.uaProvider = uaProvider;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public interface RoomInfoCallback {
        void onSuccess(RoomInfo roomInfo);
        void onFailure(String error);
    }

    public static class RoomInfo {
        public String roomId;
        public String hostName;
        public String userId;
        public boolean isLive;
        public String avatarUrl;
    }

    public void resolveRoom(final String hostName, final RoomInfoCallback callback) {
        if (hostName == null || hostName.trim().isEmpty()) {
            callback.onFailure("Hostname cannot be empty");
            return;
        }

        final String normalizedHost = hostName.trim().replace("@", "");
        new Thread(() -> {
            try {
                ApiResult api = tryApi(normalizedHost);
                if (api.info != null) {
                    callback.onSuccess(api.info);
                    return;
                }
                // The API answered a valid profile without a live room: the host is
                // definitively not live. Don't fall back to the page scrape, which
                // can embed a stale roomId from a previous live.
                if (api.definitelyNotLive) {
                    callback.onFailure("@" + normalizedHost + " is not live right now");
                    return;
                }
                RoomInfo info = tryPage(normalizedHost);
                if (info != null) {
                    callback.onSuccess(info);
                    return;
                }
                callback.onFailure("Could not resolve a live room for @" + normalizedHost);
            } catch (Exception e) {
                Log.e(TAG, "Failed to resolve room", e);
                callback.onFailure("Failed to resolve room: " + e.getMessage());
            }
        }).start();
    }

    /** Result of the live API probe: either a resolved room, a definite "not live",
     *  or an unknown/blocked outcome that may fall back to the page scrape. */
    private static final class ApiResult {
        RoomInfo info;
        boolean definitelyNotLive;
    }

    /**
     * Probe the TikTok live API. Returns:
     *  - {@code info != null} when the host is live,
     *  - {@code definitelyNotLive = true} when the API answered a valid profile
     *    with no live room (host is offline),
     *  - a neutral result for blocked/rate-limited/unparseable responses.
     */
    private ApiResult tryApi(String host) {
        String url = String.format(API_URL, host);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", uaProvider.get())
                .header("Accept", "application/json")
                .header("Referer", "https://www.tiktok.com/" + host + "/live")
                .get()
                .build();

        ApiResult result = new ApiResult();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.d(TAG, "api-live returned HTTP " + response.code());
                return result;
            }
            String bodyStr = response.body().string();
            JSONObject root = new JSONObject(bodyStr);
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                Log.d(TAG, "api-live: no data: " + bodyStr.substring(0, Math.min(150, bodyStr.length())));
                return result;
            }
            JSONObject user = data.optJSONObject("user");
            if (user == null) {
                Log.d(TAG, "api-live: no user object in response");
                return result;
            }
            if (!user.has("roomId")) {
                Log.d(TAG, "api-live: @" + host + " is not live (no roomId)");
                result.definitelyNotLive = true;
                return result;
            }
            String roomId = user.optString("roomId", "");
            if (roomId.isEmpty()) {
                Log.d(TAG, "api-live: roomId present but empty for @" + host);
                return result;
            }
            String userId = user.optString("id", "");
            String avatarUrl = firstNonEmpty(
                    user.optString("avatarLarger", ""),
                    user.optString("avatarMedium", ""),
                    user.optString("avatarThumb", ""));
            RoomInfo info = new RoomInfo();
            info.roomId = roomId;
            info.userId = userId;
            info.hostName = host;
            info.isLive = true;
            info.avatarUrl = (avatarUrl != null && !avatarUrl.isEmpty()) ? avatarUrl : null;
            Log.d(TAG, "Resolved (api) userId=" + userId + " roomId=" + roomId
                    + " avatar=" + info.avatarUrl + " for @" + host);
            result.info = info;
            return result;
        } catch (Exception e) {
            Log.w(TAG, "api-live failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Fallback: scrape the live page for an embedded roomId.
     */
    private RoomInfo tryPage(String host) throws IOException {
        String url = String.format(LIVE_PAGE_URL, host);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", uaProvider.get())
                .header("Referer", "https://www.tiktok.com/live")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            String page = response.body().string();
            String roomId = firstMatch(page, "\"roomId\"\\s*:\\s*\"(\\d+)\"");
            if (roomId == null) {
                roomId = firstMatch(page, "\"roomId\"\\s*:\\s*(\\d+)");
            }
            if (roomId == null) {
                Log.d(TAG, "page fallback: no roomId embedded");
                return null;
            }
            String userId = userIdBeforeRoomId(page, roomId);
            RoomInfo info = new RoomInfo();
            info.roomId = roomId;
            info.userId = userId;
            info.hostName = host;
            info.isLive = true;
            Log.d(TAG, "Resolved (page) roomId=" + roomId + " for @" + host);
            return info;
        } catch (Exception e) {
            Log.w(TAG, "page fallback failed: " + e.getMessage());
            return null;
        }
    }

    private String firstMatch(String text, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return null;
    }

    private String userIdBeforeRoomId(String page, String roomId) {
        if (page == null || roomId == null) return null;
        int roomIdx = page.indexOf(roomId);
        if (roomIdx < 0) return null;
        String prefix = page.substring(0, roomIdx);
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"(\\d+)\"").matcher(prefix);
        String last = null;
        while (matcher.find()) last = matcher.group(1);
        return last;
    }

    /**
     * Supplies a rotating set of desktop User-Agent strings to avoid simple
     * bot filtering. Kept small and stable.
     */
    public static class UserAgentProvider {
        private static final String[] UAS = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 OPR/131.0.0.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
        };
        private int idx = 0;

        public String get() {
            String ua = UAS[idx % UAS.length];
            idx++;
            return ua;
        }
    }
}
