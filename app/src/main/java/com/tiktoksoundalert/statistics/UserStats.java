package com.tiktoksoundalert.statistics;

/** Immutable snapshot of a single user's accumulated live statistics. */
public final class UserStats {
    private final long userId;
    private final String name;
    private final String avatarUrl;

    private final long totalDiamonds;
    private final int totalGiftCount;
    private final int commentCount;
    private final long likeCount;
    private final long firstSeenTime;
    private final long lastSeenTime;

    public UserStats(long userId, String name, String avatarUrl,
                     long totalDiamonds, int totalGiftCount, int commentCount,
                     long likeCount, long firstSeenTime, long lastSeenTime) {
        this.userId = userId;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.totalDiamonds = totalDiamonds;
        this.totalGiftCount = totalGiftCount;
        this.commentCount = commentCount;
        this.likeCount = likeCount;
        this.firstSeenTime = firstSeenTime;
        this.lastSeenTime = lastSeenTime;
    }

    public long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public long getTotalDiamonds() {
        return totalDiamonds;
    }

    public int getTotalGiftCount() {
        return totalGiftCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getFirstSeenTime() {
        return firstSeenTime;
    }

    public long getLastSeenTime() {
        return lastSeenTime;
    }

    /** Approximate minutes spent in the room based on active window. */
    public long getActiveMinutes() {
        if (firstSeenTime <= 0 || lastSeenTime < firstSeenTime) return 0;
        return Math.max(1, (lastSeenTime - firstSeenTime) / 60000L);
    }
}
