package com.tiktoksoundalert.scraper;

import android.net.Uri;
import android.util.Log;

import com.tiktoksoundalert.models.MyInstantSound;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Java port of the scraping logic from abdipr/myinstants-api
 * (https://github.com/abdipr/myinstants-api). That project scrapes the
 * same MyInstants pages with plain curl: {@code /en/index/{region}},
 * {@code /en/best_of_all_time/{region}} and {@code /en/search/?name=...},
 * mapping each {@code div.instant} to {@code {id, title, url, mp3}}.
 *
 * <p>myinstants.com rejects non-browser clients (Cloudflare), so the app
 * cannot run that curl itself. Instead it consumes the JSON served by a
 * deployed instance of that exact project, which reproduces the same
 * {@code data[]} fields. The resulting {@code mp3} URLs still point at
 * myinstants.com; downloads fall back to the Internet Archive when the
 * direct request is blocked.
 */
public class MyInstantsScraper {

    private static final String TAG = "MyInstantsScraper";

    /** Deployed instance of the abdipr/myinstants-api project. */
    private static final String API_BASE = "https://myinstants-api.vercel.app";

    private static final String ORIGIN = "https://www.myinstants.com";

    /** Wayback raw-content base ("id_" returns original bytes). */
    private static final String ARCHIVE_BASE = "https://web.archive.org/web/2026id_/";

    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final String ID_REGION = "id";

    public static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build();

    /** Merged Indonesian trending + best-of-all-time, used as the default library. */
    public static void fetchDefaultLibrary(final Callback<List<MyInstantSound>> callback) {
        fetchTrending(ID_REGION, new Callback<List<MyInstantSound>>() {
            @Override
            public void onSuccess(List<MyInstantSound> trending) {
                fetchBest(ID_REGION, new Callback<List<MyInstantSound>>() {
                    @Override
                    public void onSuccess(List<MyInstantSound> best) {
                        Map<String, MyInstantSound> merged = new LinkedHashMap<>();
                        for (MyInstantSound s : trending) merged.put(s.soundPath, s);
                        for (MyInstantSound s : best) merged.putIfAbsent(s.soundPath, s);
                        List<MyInstantSound> all = new ArrayList<>(merged.values());
                        Log.d(TAG, "default library: trending=" + trending.size()
                                + " best=" + best.size() + " merged=" + all.size());
                        main().post(() -> callback.onSuccess(all));
                    }

                    @Override
                    public void onFailure(IOException e) {
                        main().post(() -> callback.onFailure(e));
                    }
                });
            }

            @Override
            public void onFailure(IOException e) {
                main().post(() -> callback.onFailure(e));
            }
        });
    }

    /** Indonesian trending sounds ({@code /en/index/id/}). */
    public static void fetchTrending(String region,
                                     final Callback<List<MyInstantSound>> callback) {
        fetchEndpoint(API_BASE + "/trending?q=" + Uri.encode(region), callback);
    }

    /** Indonesian best-of-all-time ({@code /en/best_of_all_time/id/}). */
    public static void fetchBest(String region,
                                 final Callback<List<MyInstantSound>> callback) {
        fetchEndpoint(API_BASE + "/best?q=" + Uri.encode(region), callback);
    }

    /** Keyword search ({@code /en/search/?name=...}). */
    public static void fetchSearch(String query,
                                   final Callback<List<MyInstantSound>> callback) {
        fetchEndpoint(API_BASE + "/search?q=" + Uri.encode(query), callback);
    }

    private static void fetchEndpoint(final String url,
                                      final Callback<List<MyInstantSound>> callback) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build();
        CLIENT.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "GET " + url + " failed", e);
                main().post(() -> callback.onFailure(e));
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        throw new IOException("HTTP " + r.code() + " (API tidak tersedia)");
                    }
                    JSONObject root = new JSONObject(r.body().string());
                    final List<MyInstantSound> sounds = parseSounds(root);
                    Log.d(TAG, "GET " + url + " -> parsed=" + sounds.size());
                    main().post(() -> callback.onSuccess(sounds));
                } catch (Exception e) {
                    Log.e(TAG, "GET " + url + " failed", e);
                    main().post(() -> callback.onFailure(e instanceof IOException
                            ? (IOException) e : new IOException(e.getMessage())));
                }
            }
        });
    }

    /**
     * Maps the API's {@code data[]} array ({@code id, title, url, mp3}),
     * mirroring {@code parse_sounds()} from the original PHP project.
     */
    private static List<MyInstantSound> parseSounds(JSONObject root) throws Exception {
        List<MyInstantSound> result = new ArrayList<>();
        JSONArray data = root.optJSONArray("data");
        if (data == null) return result;
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.getJSONObject(i);
            String title = o.optString("title", "").trim();
            String mp3 = o.optString("mp3", "").trim();
            if (title.isEmpty() || mp3.isEmpty()) continue;
            if (mp3.startsWith("//")) mp3 = "https:" + mp3;
            if (!mp3.startsWith("http")) mp3 = ORIGIN + mp3;
            if (title.length() > 120) title = title.substring(0, 120);
            result.add(new MyInstantSound(title, mp3));
        }
        return result;
    }

    /** Raw archived bytes for an original mp3 URL, if it was archived. */
    public static String archivedMediaUrl(String mp3Url) {
        return ARCHIVE_BASE + mp3Url;
    }

    /** True if the response actually carries audio (rejects Cloudflare pages). */
    public static boolean isAudio(Response r) {
        String ctype = r.header("Content-Type", "");
        String lower = ctype.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("audio") || lower.contains("mpeg") || lower.contains("ogg")
                || lower.contains("octet-stream");
    }

    private static final android.os.Handler MAIN = new android.os.Handler(
            android.os.Looper.getMainLooper());

    /** Handler bound to the main (UI) looper, shared with sibling scrapers. */
    public static android.os.Handler main() {
        return MAIN;
    }

    /** Functional callback that runs on the UI thread. */
    public interface Callback<T> {
        void onSuccess(T value);

        void onFailure(IOException error);
    }
}