package ru.malfix.autobuy.cycle;

import ru.malfix.autobuy.buy.ControlledBuyClickResult;
import ru.malfix.autobuy.refresh.RefreshCycleResult;
import ru.malfix.autobuy.scanner.ScanCandidate;

public final class OneCycleResult {

    public enum Status {
        IDLE,
        STARTED,
        REFRESHING,
        REFRESH_FAILED,
        SCANNING,
        NO_MATCH,
        READY_TO_BUY,
        BUY_CLICKED,

        BUY_SUCCESS,
        NO_MONEY_STOP,
        INVENTORY_FULL_STOP,
        ALREADY_SOLD_REFRESH_NEEDED,
        PRICE_CHANGED_REFRESH_NEEDED,
        BUY_FAILED,
        BUY_TIMEOUT,
        BUY_SCREEN_CHANGED,
        BUY_AUCTION_CHANGED,
        BUY_UNKNOWN_RESULT,

        ERROR
    }

    private final Status status;
    private final boolean pending;
    private final String message;
    private final long startedAtMs;
    private final long finishedAtMs;
    private final RefreshCycleResult refreshResult;
    private final ControlledBuyClickResult buyClickResult;
    private final ScanCandidate candidate;

    private OneCycleResult(
            Status status,
            boolean pending,
            String message,
            long startedAtMs,
            long finishedAtMs,
            RefreshCycleResult refreshResult,
            ControlledBuyClickResult buyClickResult,
            ScanCandidate candidate
    ) {
        this.status = status == null ? Status.ERROR : status;
        this.pending = pending;
        this.message = message == null ? "" : message;
        this.startedAtMs = startedAtMs;
        this.finishedAtMs = finishedAtMs;
        this.refreshResult = refreshResult;
        this.buyClickResult = buyClickResult;
        this.candidate = candidate;
    }

    public static OneCycleResult idle() {
        return new OneCycleResult(Status.IDLE, false, "idle", 0L, 0L, null, null, null);
    }

    public static OneCycleResult started(long startedAtMs) {
        return new OneCycleResult(Status.STARTED, true, "one_cycle_started", startedAtMs, 0L, null, null, null);
    }

    public static OneCycleResult refreshing(long startedAtMs, RefreshCycleResult refreshResult) {
        return new OneCycleResult(Status.REFRESHING, true, "refreshing", startedAtMs, 0L, refreshResult, null, null);
    }

    public static OneCycleResult refreshFailed(long startedAtMs, RefreshCycleResult refreshResult, String message) {
        return new OneCycleResult(Status.REFRESH_FAILED, false, message, startedAtMs, System.currentTimeMillis(), refreshResult, null, null);
    }

    public static OneCycleResult noMatch(long startedAtMs, RefreshCycleResult refreshResult) {
        return new OneCycleResult(Status.NO_MATCH, false, "no_matching_lot_after_refresh", startedAtMs, System.currentTimeMillis(), refreshResult, null, null);
    }

    public static OneCycleResult readyToBuy(long startedAtMs, RefreshCycleResult refreshResult, ScanCandidate candidate) {
        return new OneCycleResult(Status.READY_TO_BUY, true, "ready_to_buy", startedAtMs, 0L, refreshResult, null, candidate);
    }

    public static OneCycleResult buyClicked(long startedAtMs, RefreshCycleResult refreshResult, ControlledBuyClickResult buyClickResult) {
        ScanCandidate candidate = buyClickResult == null ? null : buyClickResult.getCandidate();
        return new OneCycleResult(Status.BUY_CLICKED, true, "buy_clicked_waiting_result", startedAtMs, 0L, refreshResult, buyClickResult, candidate);
    }

    public static OneCycleResult buyStartFailed(long startedAtMs, RefreshCycleResult refreshResult, ControlledBuyClickResult buyClickResult) {
        ScanCandidate candidate = buyClickResult == null ? null : buyClickResult.getCandidate();
        return new OneCycleResult(Status.ERROR, false, "buy_start_failed", startedAtMs, System.currentTimeMillis(), refreshResult, buyClickResult, candidate);
    }

    public static OneCycleResult fromBuyClick(long startedAtMs, RefreshCycleResult refreshResult, ControlledBuyClickResult buyClickResult) {
        ControlledBuyClickResult safe = buyClickResult;
        Status mapped = mapBuyStatus(safe == null ? null : safe.getStatus());
        ScanCandidate candidate = safe == null ? null : safe.getCandidate();
        return new OneCycleResult(mapped, false, mapped.name().toLowerCase(), startedAtMs, System.currentTimeMillis(), refreshResult, safe, candidate);
    }

    public static OneCycleResult error(String message) {
        long now = System.currentTimeMillis();
        return new OneCycleResult(Status.ERROR, false, message, now, now, null, null, null);
    }

    private static Status mapBuyStatus(ControlledBuyClickResult.Status status) {
        if (status == null) {
            return Status.BUY_UNKNOWN_RESULT;
        }

        switch (status) {
            case BUY_SUCCESS:
                return Status.BUY_SUCCESS;
            case NO_MONEY:
                return Status.NO_MONEY_STOP;
            case INVENTORY_FULL:
                return Status.INVENTORY_FULL_STOP;
            case ALREADY_SOLD:
                return Status.ALREADY_SOLD_REFRESH_NEEDED;
            case PRICE_CHANGED:
                return Status.PRICE_CHANGED_REFRESH_NEEDED;
            case BUY_FAILED_CHAT:
            case UNKNOWN_CHAT_RESULT:
            case VALIDATION_FAILED:
            case CLICK_FAILED:
            case ERROR:
                return Status.BUY_FAILED;
            case TIMEOUT_STILL_SAME:
                return Status.BUY_TIMEOUT;
            case SCREEN_CHANGED_AFTER_CLICK:
                return Status.BUY_SCREEN_CHANGED;
            case AUCTION_CHANGED_AFTER_CLICK:
                return Status.BUY_AUCTION_CHANGED;
            case CLICKED_PENDING:
                return Status.BUY_CLICKED;
            default:
                return Status.BUY_UNKNOWN_RESULT;
        }
    }

    public Status getStatus() {
        return status;
    }

    public boolean isPending() {
        return pending;
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

    public RefreshCycleResult getRefreshResult() {
        return refreshResult;
    }

    public ControlledBuyClickResult getBuyClickResult() {
        return buyClickResult;
    }

    public ScanCandidate getCandidate() {
        return candidate;
    }

    public boolean isHardStop() {
        return status == Status.NO_MONEY_STOP || status == Status.INVENTORY_FULL_STOP;
    }

    public boolean needsRefreshNext() {
        return status == Status.ALREADY_SOLD_REFRESH_NEEDED
                || status == Status.PRICE_CHANGED_REFRESH_NEEDED
                || status == Status.BUY_AUCTION_CHANGED
                || status == Status.BUY_TIMEOUT
                || status == Status.NO_MATCH;
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
                + ", pending=" + pending
                + ", elapsedMs=" + getElapsedMs()
                + ", msg=" + message
                + ", hardStop=" + isHardStop()
                + ", needsRefreshNext=" + needsRefreshNext();
    }
}
