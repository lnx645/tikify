package com.tiktoksoundalert.scraper;

import android.util.Log;

import com.tiktoksoundalert.models.GiftInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Loads the Indonesian TikTok gift catalog (names + images + coin prices) from
 * https://streamtoearn.io/gifts?region=ID. That page server-renders every gift
 * as {@code <div class="gift" data-price="...">} blocks, so each entry is read
 * straight out of the HTML (no API). Images are hosted on TikTok's own CDN
 * (p16-webcast.tiktokcdn.com), the same host the TikTok app itself uses, so
 * they download fine on the device.
 */
public class GiftCatalogScraper {

    private static final String TAG = "GiftCatalogScraper";
    private static final String URL = "https://streamtoearn.io/gifts?region=ID";
    private static final String USER_AGENT = MyInstantsScraper.USER_AGENT;

    private static final Pattern GIFT_BLOCK = Pattern.compile(
            "<div class=\"gift\"[^>]*>\\s*<img[^>]*src=\"([^\"]+)\"[^>]*alt=\"([^\"]*)\""
                    + "[^>]*>[\\s\\S]*?<p class=\"gift-name\">([\\s\\S]*?)</p>"
                    + "\\s*<p class=\"gift-price\">\\s*([0-9\\s]*)",
            Pattern.CASE_INSENSITIVE);

    private GiftCatalogScraper() {
    }

    /** Indonesian gift catalog, deduplicated by gift name. */
    public static void fetchGifts(final MyInstantsScraper.Callback<List<GiftInfo>> callback) {
        Request request = new Request.Builder()
                .url(URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .build();
        MyInstantsScraper.CLIENT.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "GET gifts failed", e);
                MyInstantsScraper.main().post(() -> callback.onFailure(e));
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        throw new IOException("HTTP " + r.code() + " (katalog gift tidak tersedia)");
                    }
                    String html = r.body().string();
                    final List<GiftInfo> gifts = parseGifts(html);
                    Log.d(TAG, "GET gifts -> parsed=" + gifts.size());
                    MyInstantsScraper.main().post(() -> callback.onSuccess(gifts));
                } catch (Exception e) {
                    Log.e(TAG, "GET gifts failed", e);
                    MyInstantsScraper.main().post(() -> callback.onFailure(e instanceof IOException
                            ? (IOException) e : new IOException(e.getMessage())));
                }
            }
        });
    }

    private static List<GiftInfo> parseGifts(String html) {
        Map<String, GiftInfo> byName = new LinkedHashMap<>();
        Matcher m = GIFT_BLOCK.matcher(html);
        while (m.find()) {
            String img = decodeEntities(m.group(1)).trim();
            String name = decodeEntities(m.group(3)).trim();
            String priceRaw = m.group(4) == null ? "" : m.group(4).trim();
            int price = 0;
            for (char c : priceRaw.toCharArray()) {
                if (Character.isDigit(c)) price = price * 10 + (c - '0');
                else break;
            }
            if (name.isEmpty() || img.isEmpty()) continue;
            // Prefer the first (cheapest) occurrence for a given gift name.
            if (!byName.containsKey(name)) {
                byName.put(name, new GiftInfo(name, img, price));
            }
        }
        return new ArrayList<>(byName.values());
    }

    private static String decodeEntities(String input) {
        if (input == null) return "";
        return input.replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&#039;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
