package ru.malfix.autobuy.buy;

public final class BuyResult {

    private final BuyResultType type;
    private final String reason;
    private final String rawMessage;
    private final long createdAtMs;

    private BuyResult(BuyResultType type, String reason, String rawMessage, long createdAtMs) {
        this.type = type == null ? BuyResultType.NONE : type;
        this.reason = reason == null ? "" : reason;
        this.rawMessage = rawMessage == null ? "" : rawMessage;
        this.createdAtMs = createdAtMs;
    }

    public static BuyResult none() {
        return new BuyResult(BuyResultType.NONE, "none", "", 0L);
    }

    public static BuyResult of(BuyResultType type, String reason, String rawMessage) {
        return new BuyResult(type, reason, rawMessage, System.currentTimeMillis());
    }

    public BuyResultType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public boolean isDetected() {
        return type != BuyResultType.NONE;
    }

    public boolean isSuccess() {
        return type == BuyResultType.BUY_SUCCESS;
    }

    public boolean isHardStop() {
        return type == BuyResultType.NO_MONEY || type == BuyResultType.INVENTORY_FULL;
    }

    public String compact() {
        return "type=" + type + ", reason=" + reason + ", message=" + rawMessage;
    }
}
