package com.tiktoksoundalert.tiktok.models;

import com.tiktoksoundalert.models.EventType;
import org.json.JSONException;
import org.json.JSONObject;

public class TikTokGiftEvent extends TikTokEvent {
    private final User user;
    private final Gift gift;
    private final int count;
    private final long repeatEnd;

    public TikTokGiftEvent(User user, Gift gift, int count, long repeatEnd) {
        super(EventType.GIFT);
        this.user = user;
        this.gift = gift;
        this.count = count;
        this.repeatEnd = repeatEnd;
    }

    public User getUser() {
        return user;
    }

    public Gift getGift() {
        return gift;
    }

    public String getGiftName() {
        return gift.getName();
    }

    public long getGiftType() {
        return gift.getType();
    }

    public long getGiftId() {
        return gift.getId();
    }

    public long getDiamondCount() {
        return gift.getDiamondCount();
    }

    public int getGiftCount() {
        return count;
    }

    public long getRepeatEnd() {
        return repeatEnd;
    }

    @Override
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(getGiftName());
        if (getGiftId() > 0) {
            sb.append(" (ID:").append(getGiftId()).append(")");
        }
        sb.append("   Repeat: x").append(count);
        long diamond = getDiamondCount();
        if (diamond > 0) {
            sb.append("   Cost: ").append(diamond).append(diamond == 1 ? " Diamond" : " Diamonds");
        }
        return sb.toString();
    }

    @Override
    protected void fillJson(JSONObject o) throws JSONException {
        o.put("user", user.toJson());
        o.put("gift", gift.toJson());
        o.put("count", count);
        o.put("repeatEnd", repeatEnd);
    }

    static TikTokGiftEvent fromJson(JSONObject o) {
        return new TikTokGiftEvent(
                User.fromJson(o.optJSONObject("user")),
                Gift.fromJson(o.optJSONObject("gift")),
                o.optInt("count", 1),
                o.optLong("repeatEnd"));
    }
}
