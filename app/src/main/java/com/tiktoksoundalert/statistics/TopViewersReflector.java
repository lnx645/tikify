package com.tiktoksoundalert.statistics;

import com.tiktoksoundalert.tiktok.models.TikTokCommentEvent;
import com.tiktoksoundalert.tiktok.models.TikTokEvent;
import com.tiktoksoundalert.tiktok.models.TikTokGiftEvent;
import com.tiktoksoundalert.tiktok.models.TikTokJoinEvent;
import com.tiktoksoundalert.tiktok.models.TikTokLikeEvent;
import com.tiktoksoundalert.tiktok.models.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates per-user live statistics from incoming TikTokEvents so the app can
 * show "top gifters", "top chat aura", "top likes" and approximate real-time viewers.
 *
 * The real-time viewer list is estimated from activity: a user is considered
 * "in the room" when they have been active (gift / comment / like / join) recently,
 * and "active time" is the window between first and last seen event.
 */
public final class TopViewersReflector {

    private static final TopViewersReflector INSTANCE = new TopViewersReflector();

    public static TopViewersReflector getInstance() {
        return INSTANCE;
    }

    private TopViewersReflector() {
    }

    private static final class Entry {
        long userId;
        String name;
        String avatarUrl;
        long totalDiamonds;
        int totalGiftCount;
        int commentCount;
        long likeCount;
        long firstSeen;
        long lastSeen;
    }

    private final Map<Long, Entry> users = new HashMap<>();
    private final Object lock = new Object();

    public void onEvent(TikTokEvent event) {
        if (event instanceof TikTokGiftEvent) {
            TikTokGiftEvent g = (TikTokGiftEvent) event;
            int count = g.getGiftCount();
            long diamonds = g.getDiamondCount() * (long) count;
            touch(g.getUser(), System.currentTimeMillis());
            synchronized (lock) {
                Entry e = users.get(g.getUser().getId());
                if (e != null) {
                    e.totalGiftCount += count;
                    e.totalDiamonds += diamonds;
                }
            }
        } else if (event instanceof TikTokCommentEvent) {
            TikTokCommentEvent c = (TikTokCommentEvent) event;
            touch(c.getUser(), System.currentTimeMillis());
            synchronized (lock) {
                Entry e = users.get(c.getUser().getId());
                if (e != null) e.commentCount++;
            }
        } else if (event instanceof TikTokLikeEvent) {
            TikTokLikeEvent l = (TikTokLikeEvent) event;
            touch(l.getUser(), System.currentTimeMillis());
            synchronized (lock) {
                Entry e = users.get(l.getUser().getId());
                if (e != null) e.likeCount += Math.max(1, l.getCount());
            }
        } else if (event instanceof TikTokJoinEvent) {
            TikTokJoinEvent j = (TikTokJoinEvent) event;
            touch(j.getUser(), System.currentTimeMillis());
        }
    }

    private void touch(User user, long now) {
        if (user == null) return;
        synchronized (lock) {
            if (users.size() >= 8000) {
                pruneInactive(now - 15L * 60 * 1000);
            }
            Entry e = users.get(user.getId());
            if (e == null) {
                e = new Entry();
                e.userId = user.getId();
                e.name = user.getName();
                e.avatarUrl = user.getAvatarUrl();
                e.firstSeen = now;
                users.put(e.userId, e);
            } else {
                if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                    e.avatarUrl = user.getAvatarUrl();
                }
            }
            e.lastSeen = now;
        }
    }

    /** Drop users inactive for a while so a long stream's map stays bounded. */
    private void pruneInactive(long olderThan) {
        java.util.Iterator<Map.Entry<Long, Entry>> it = users.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Entry> en = it.next();
            if (en.getValue().lastSeen < olderThan) {
                it.remove();
            }
        }
        if (users.size() >= 8000) {
            users.clear();
        }
    }

    /** Top users by total diamonds received (gifts). */
    public List<UserStats> topGifters(int limit) {
        Comparator<Entry> c = (a, b) -> Long.compare(b.totalDiamonds, a.totalDiamonds);
        return snapshot(limit, c);
    }

    /** Top users by number of comments (chat aura). */
    public List<UserStats> topChat(int limit) {
        Comparator<Entry> c = (a, b) -> Integer.compare(b.commentCount, a.commentCount);
        return snapshot(limit, c);
    }

    /** Top users by number of likes. */
    public List<UserStats> topLikes(int limit) {
        Comparator<Entry> c = (a, b) -> Long.compare(b.likeCount, a.likeCount);
        return snapshot(limit, c);
    }

    /** Users with the most active time in the room (oldest first-seen, recent last-seen). */
    public List<UserStats> topViewers(int limit) {
        Comparator<Entry> c = (a, b) -> {
            long aDur = (a.lastSeen - a.firstSeen);
            long bDur = (b.lastSeen - b.firstSeen);
            int byDur = Long.compare(bDur, aDur);
            if (byDur != 0) return byDur;
            return Long.compare(a.firstSeen, b.firstSeen);
        };
        return snapshot(limit, c);
    }

    private List<UserStats> snapshot(int limit, Comparator<Entry> ordering) {
        List<Entry> list;
        synchronized (lock) {
            list = new ArrayList<>(users.values());
        }
        list.sort(ordering);
        if (limit < 0 || limit > list.size()) limit = list.size();
        List<UserStats> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Entry e = list.get(i);
            out.add(new UserStats(e.userId, e.name, e.avatarUrl,
                    e.totalDiamonds, e.totalGiftCount, e.commentCount, e.likeCount,
                    e.firstSeen, e.lastSeen));
        }
        return out;
    }

    public void clear() {
        synchronized (lock) {
            users.clear();
        }
    }
}
