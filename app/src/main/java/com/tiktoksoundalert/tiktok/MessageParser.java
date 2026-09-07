package com.tiktoksoundalert.tiktok;

import android.util.Log;

import com.tiktoksoundalert.tiktok.models.Gift;
import com.tiktoksoundalert.tiktok.models.TikTokCommentEvent;
import com.tiktoksoundalert.tiktok.models.TikTokEvent;
import com.tiktoksoundalert.tiktok.models.TikTokFollowEvent;
import com.tiktoksoundalert.tiktok.models.TikTokGiftEvent;
import com.tiktoksoundalert.tiktok.models.TikTokJoinEvent;
import com.tiktoksoundalert.tiktok.models.TikTokLikeEvent;
import com.tiktoksoundalert.tiktok.models.TikTokShareEvent;
import com.tiktoksoundalert.tiktok.models.User;

import java.util.List;
import java.util.Map;

import static com.tiktoksoundalert.tiktok.ProtobufCodec.first;
import static com.tiktoksoundalert.tiktok.ProtobufCodec.str;
import static com.tiktoksoundalert.tiktok.ProtobufCodec.varint;

/**
 * Decodes a single Webcast*Message protobuf into a typed TikTokEvent.
 * Field numbers follow the TikTok Live proto (jwdeveloper/TikTokLiveJava).
 * Returns null when the message is not one we surface to the user.
 */
public final class MessageParser {
    private static final String TAG = "MessageParser";

    private MessageParser() {
    }

    /**
     * @return a user-facing TikTokEvent, or null if the method has no surfaced event.
     */
    public static TikTokEvent parse(String method, byte[] payload) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        Map<Long, List<byte[]>> f = ProtobufCodec.parse(payload);
        switch (method) {
            case "WebcastGiftMessage":
                return gift(f);
            case "WebcastChatMessage":
                return comment(f);
            case "WebcastMemberMessage":
                return join(f);
            case "WebcastLikeMessage":
                return like(f);
            case "WebcastSocialMessage":
                return social(f);
            default:
                return null;
        }
    }

    private static TikTokEvent gift(Map<Long, List<byte[]>> f) {
        User user = user(first(f, 7));
        Map<Long, List<byte[]>> gift = ProtobufCodec.parse(first(f, 15));
        long repeat = varint(first(f, 5));
        if (repeat < 1) repeat = 1;
        long repeatEnd = varint(first(f, 9));
        long id = varint(first(gift, 5));
        String name = str(first(gift, 16));
        if (name.isEmpty()) name = str(first(gift, 2));
        String imageUrl = imageUrl(first(gift, 1));
        if (imageUrl.isEmpty()) imageUrl = imageUrl(first(gift, 21));
        Gift giftInfo = new Gift(id, name, varint(first(gift, 11)), varint(first(gift, 12)), imageUrl);
        Log.d(TAG, "GIFT user=" + user.getName() + " gift=" + giftInfo.getName()
                + " count=" + repeat + " diamonds=" + giftInfo.getDiamondCount());
        return new TikTokGiftEvent(user, giftInfo, (int) repeat, repeatEnd);
    }

    private static TikTokEvent comment(Map<Long, List<byte[]>> f) {
        User user = user(first(f, 2));
        String content = str(first(f, 3));
        Log.d(TAG, "COMMENT user=" + user.getName() + " content=" + content);
        return new TikTokCommentEvent(user, content);
    }

    private static TikTokEvent join(Map<Long, List<byte[]>> f) {
        return new TikTokJoinEvent(user(first(f, 2)));
    }

    private static TikTokEvent like(Map<Long, List<byte[]>> f) {
        return new TikTokLikeEvent(user(first(f, 5)), varint(first(f, 2)));
    }

    private static TikTokEvent social(Map<Long, List<byte[]>> f) {
        User user = user(first(f, 2));
        String key = socialKey(first(f, 1));
        long action = varint(first(f, 4));
        Log.d(TAG, "SOCIAL user=" + user.getName() + " key=" + key + " action=" + action);
        if (key.contains("follow") || keyContains(key, "follow")) {
            return new TikTokFollowEvent(user);
        } else if (key.contains("share")) {
            return new TikTokShareEvent(user);
        }
        if (action == 1) {
            return new TikTokFollowEvent(user);
        } else if (action == 2) {
            return new TikTokShareEvent(user);
        }
        return null;
    }

    private static boolean keyContains(String key, String token) {
        return key != null && key.toLowerCase().contains(token);
    }

    /**
     * Extract the social message key from CommonMessageData.displayText.key
     * (common=1, displayText=8, key=1).
     */
    private static String socialKey(byte[] raw) {
        if (raw == null) return "";
        try {
            Map<Long, List<byte[]>> common = ProtobufCodec.parse(raw);
            Map<Long, List<byte[]>> displayText = ProtobufCodec.parse(first(common, 8));
            String key = str(first(displayText, 1));
            return key == null ? "" : key;
        } catch (Exception e) {
            return "";
        }
    }

    private static User user(byte[] raw) {
        if (raw == null) return new User(0, "?");
        Map<Long, List<byte[]>> uf = ProtobufCodec.parse(raw);
        long id = varint(first(uf, 1));
        String n = str(first(uf, 3));
        if (n.isEmpty()) n = str(first(uf, 38));
        if (n.isEmpty()) n = "uid_" + id;
        String avatarUrl = imageUrl(first(uf, 9));
        if (avatarUrl.isEmpty()) avatarUrl = imageUrl(first(uf, 10));
        return new User(id, n, avatarUrl);
    }

    /**
     * Extract the first URL from an Image protobuf (repeated string url = 1).
     * Returns an empty string when there is no usable URL.
     */
    private static String imageUrl(byte[] raw) {
        if (raw == null) return "";
        try {
            Map<Long, List<byte[]>> img = ProtobufCodec.parse(raw);
            byte[] firstUrl = first(img, 1);
            if (firstUrl == null) return "";
            String url = str(firstUrl);
            return url == null ? "" : url;
        } catch (Exception e) {
            return "";
        }
    }
}