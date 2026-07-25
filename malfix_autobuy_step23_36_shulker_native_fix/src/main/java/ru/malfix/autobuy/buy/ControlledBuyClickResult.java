package ru.malfix.autobuy.buy;

import ru.malfix.autobuy.scanner.ScanCandidate;

public final class ControlledBuyClickResult {

    public enum Status {
        IDLE,
        VALIDATION_FAILED,
        CLICK_FAILED,
        CLICKED_PENDING,

        BUY_SUCCESS,
        NO_MONEY,
        ALREADY_SOLD,
        PRICE_CHANGED,
        INVENTORY_FULL,
        BUY_FAILED_CHAT,
        UNKNOWN_CHAT_RESULT,

        SCREEN_CHANGED_AFTER_CLICK,
        AUCTION_CHANGED_AFTER_CLICK,
        TIMEOUT_STILL_SAME,
        ERROR
    }

    private final Status status;
    private final String message;
    private final long startedAtMs;
    private final long finishedAtMs;
    private final int beforeFingerprint;
    private final int afterFingerprint;
    private final int checkedSlots;
    private final ScanCandidate candidate;
    private final BuyResult buyResult;

    private ControlledBuyClickResult(
            Status status,
            String message,
            long startedAtMs,
            long finishedAtMs,
            int beforeFingerprint,
            int afterFingerprint,
            int checkedSlots,
            ScanCandidate candidate,
            BuyResult buyResult
    ) {
        this.status = status == null ? Status.ERROR : status;
        this.message = message == null ? "" : message;
        this.startedAtMs = startedAtMs;
        this.finishedAtMs = finishedAtMs;
        this.beforeFingerprint = beforeFingerprint;
        this.afterFingerprint = afterFingerprint;
        this.checkedSlots = checkedSlots;
        this.candidate = candidate;
        this.buyResult = buyResult == null ? BuyResult.none() : buyResult;
    }

    public static ControlledBuyClickResult idle() {
        return new ControlledBuyClickResult(Status.IDLE, "idle", 0L, 0L, 0, 0, 0, null, BuyResult.none());
    }

    public static ControlledBuyClickResult validationFailed(String message, BuyDryRunResult dryRun) {
        ScanCandidate candidate = dryRun == null ? null : dryRun.getCandidate();
        int before = dryRun == null ? 0 : dryRun.getBeforeFingerprint();
        int after = dryRun == null ? 0 : dryRun.getAfterFingerprint();
        int checked = dryRun == null ? 0 : dryRun.getCheckedSlots();
        long now = System.currentTimeMillis();
        return new ControlledBuyClickResult(Status.VALIDATION_FAILED, message, now, now, before, after, checked, candidate, BuyResult.none());
    }

    public static ControlledBuyClickResult clickFailed(String message, BuyDryRunResult dryRun) {
        ScanCandidate candidate = dryRun == null ? null : dryRun.getCandidate();
        int fingerprint = dryRun == null ? 0 : dryRun.getBeforeFingerprint();
        int checked = dryRun == null ? 0 : dryRun.getCheckedSlots();
        long now = System.currentTimeMillis();
        return new ControlledBuyClickResult(Status.CLICK_FAILED, message, now, now, fingerprint, fingerprint, checked, candidate, BuyResult.none());
    }

    public static ControlledBuyClickResult clickedPending(long startedAtMs, int beforeFingerprint, int checkedSlots, ScanCandidate candidate) {
        return new ControlledBuyClickResult(Status.CLICKED_PENDING, "clicked_waiting_result", startedAtMs, 0L, beforeFingerprint, beforeFingerprint, checkedSlots, candidate, BuyResult.none());
    }

    public static ControlledBuyClickResult fromBuyResult(
            long startedAtMs,
            int beforeFingerprint,
            int afterFingerprint,
            int checkedSlots,
            ScanCandidate candidate,
            BuyResult result
    ) {
        BuyResult safe = result == null ? BuyResult.none() : result;
        Status status = mapStatus(safe.getType());
        return new ControlledBuyClickResult(status, safe.getReason(), startedAtMs, System.currentTimeMillis(), beforeFingerprint, afterFingerprint, checkedSlots, candidate, safe);
    }

    public static ControlledBuyClickResult screenChanged(long startedAtMs, int beforeFingerprint, int checkedSlots, ScanCandidate candidate) {
        return new ControlledBuyClickResult(Status.SCREEN_CHANGED_AFTER_CLICK, "screen_changed_after_click", startedAtMs, System.currentTimeMillis(), beforeFingerprint, beforeFingerprint, checkedSlots, candidate, BuyResult.none());
    }

    public static ControlledBuyClickResult auctionChanged(long startedAtMs, int beforeFingerprint, int afterFingerprint, int checkedSlots, ScanCandidate candidate) {
        return new ControlledBuyClickResult(Status.AUCTION_CHANGED_AFTER_CLICK, "auction_changed_after_click", startedAtMs, System.currentTimeMillis(), beforeFingerprint, afterFingerprint, checkedSlots, candidate, BuyResult.none());
    }

    public static ControlledBuyClickResult timeout(long startedAtMs, int beforeFingerprint, int afterFingerprint, int checkedSlots, ScanCandidate candidate) {
        return new ControlledBuyClickResult(Status.TIMEOUT_STILL_SAME, "timeout_still_same", startedAtMs, System.currentTimeMillis(), beforeFingerprint, afterFingerprint, checkedSlots, candidate, BuyResult.none());
    }

    public static ControlledBuyClickResult error(String message) {
        long now = System.currentTimeMillis();
        return new ControlledBuyClickResult(Status.ERROR, message, now, now, 0, 0, 0, null, BuyResult.none());
    }

    private static Status mapStatus(BuyResultType type) {
        if (type == BuyResultType.BUY_SUCCESS) {
            return Status.BUY_SUCCESS;
        }
        if (type == BuyResultType.NO_MONEY) {
            return Status.NO_MONEY;
        }
        if (type == BuyResultType.ALREADY_SOLD) {
            return Status.ALREADY_SOLD;
        }
        if (type == BuyResultType.PRICE_CHANGED) {
            return Status.PRICE_CHANGED;
        }
        if (type == BuyResultType.INVENTORY_FULL) {
            return Status.INVENTORY_FULL;
        }
        if (type == BuyResultType.BUY_FAILED) {
            return Status.BUY_FAILED_CHAT;
        }
        if (type == BuyResultType.UNKNOWN_FAIL) {
            return Status.UNKNOWN_CHAT_RESULT;
        }
        return Status.UNKNOWN_CHAT_RESULT;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public long getStartedAtMs() {
        return startedAtMs;
    }

    public long getFinishedAtMs() {
        return finishedAtMs;
    }

    public int getBeforeFingerprint() {
        return beforeFingerprint;
    }

    public int getAfterFingerprint() {
        return afterFingerprint;
    }

    public int getCheckedSlots() {
        return checkedSlots;
    }

    public ScanCandidate getCandidate() {
        return candidate;
    }

    public BuyResult getBuyResult() {
        return buyResult;
    }

    public boolean isPending() {
        return status == Status.CLICKED_PENDING;
    }

    public boolean isChanged() {
        return beforeFingerprint != afterFingerprint;
    }

    public boolean isFinalBuyResult() {
        return status == Status.BUY_SUCCESS
                || status == Status.NO_MONEY
                || status == Status.ALREADY_SOLD
                || status == Status.PRICE_CHANGED
                || status == Status.INVENTORY_FULL
                || status == Status.BUY_FAILED_CHAT
                || status == Status.UNKNOWN_CHAT_RESULT;
    }

    public long getElapsedMs() {
        long end = finishedAtMs <= 0L ? System.currentTimeMillis() : finishedAtMs;
        if (startedAtMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, end - startedAtMs);
    }

    public String compact() {
        return "status=" + status
                + ", message=" + message
                + ", elapsedMs=" + getElapsedMs()
                + ", beforeFp=" + beforeFingerprint
                + ", afterFp=" + afterFingerprint
                + ", changed=" + isChanged()
                + ", checked=" + checkedSlots
                + ", buyResult=" + buyResult.getType();
    }
}
