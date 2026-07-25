package ru.malfix.autobuy.loop;

import ru.malfix.autobuy.buy.ControlledBuyClickResult;
import ru.malfix.autobuy.refresh.RefreshCycleResult;
import ru.malfix.autobuy.scanner.ScanCandidate;

public final class AutoLoopResult {

    public enum Status {
        IDLE,
        STARTED,
        WAIT_DELAY,
        REFRESHING,
        REFRESH_FAILED_CONTINUE,
        NO_MATCH_CONTINUE,
        BUY_CLICKED,

        BUY_SUCCESS_CONTINUE,
        NO_MONEY_STOP,
        INVENTORY_FULL_STOP,
        ALREADY_SOLD_CONTINUE,
        PRICE_CHANGED_CONTINUE,
        BUY_FAILED_CONTINUE,
        BUY_TIMEOUT_CONTINUE,
        BUY_AUCTION_CHANGED_CONTINUE,
        BUY_SCREEN_CHANGED_CONTINUE,

        LIMIT_REACHED_STOP,
        MANUAL_STOP,
        ERROR_STOP
    }

    private final Status status;
    private final boolean running;
    private final String message;
    private final long loopStartedAtMs;
    private final long eventAtMs;
    private final long nextCycleAtMs;

    private final int cyclesStarted;
    private final int maxCycles;
    private final int buysDone;
    private final int maxBuys;

    private final RefreshCycleResult refreshResult;
    private final ControlledBuyClickResult buyClickResult;
    private final ScanCandidate candidate;

    private AutoLoopResult(
            Status status,
            boolean running,
            String message,
            long loopStartedAtMs,
            long eventAtMs,
            long nextCycleAtMs,
            int cyclesStarted,
            int maxCycles,
            int buysDone,
            int maxBuys,
            RefreshCycleResult refreshResult,
            ControlledBuyClickResult buyClickResult,
            ScanCandidate candidate
    ) {
        this.status = status == null ? Status.ERROR_STOP : status;
        this.running = running;
        this.message = message == null ? "" : message;
        this.loopStartedAtMs = loopStartedAtMs;
        this.eventAtMs = eventAtMs;
        this.nextCycleAtMs = nextCycleAtMs;
        this.cyclesStarted = cyclesStarted;
        this.maxCycles = maxCycles;
        this.buysDone = buysDone;
        this.maxBuys = maxBuys;
        this.refreshResult = refreshResult;
        this.buyClickResult = buyClickResult;
        this.candidate = candidate;
    }

    public static AutoLoopResult idle() {
        return new AutoLoopResult(Status.IDLE, false, "idle", 0L, 0L, 0L, 0, 0, 0, 0, null, null, null);
    }

    public static AutoLoopResult of(
            Status status,
            boolean running,
            String message,
            long loopStartedAtMs,
            long nextCycleAtMs,
            int cyclesStarted,
            int maxCycles,
            int buysDone,
            int maxBuys,
            RefreshCycleResult refreshResult,
            ControlledBuyClickResult buyClickResult,
            ScanCandidate candidate
    ) {
        return new AutoLoopResult(
                status,
                running,
                message,
                loopStartedAtMs,
                System.currentTimeMillis(),
                nextCycleAtMs,
                cyclesStarted,
                maxCycles,
                buysDone,
                maxBuys,
                refreshResult,
                buyClickResult,
                candidate
        );
    }

    public Status getStatus() {
        return status;
    }

    public boolean isRunning() {
        return running;
    }

    public String getMessage() {
        return message;
    }

    public long getLoopStartedAtMs() {
        return loopStartedAtMs;
    }

    public long getEventAtMs() {
        return eventAtMs;
    }

    public long getNextCycleAtMs() {
        return nextCycleAtMs;
    }

    public int getCyclesStarted() {
        return cyclesStarted;
    }

    public int getMaxCycles() {
        return maxCycles;
    }

    public int getBuysDone() {
        return buysDone;
    }

    public int getMaxBuys() {
        return maxBuys;
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
        return status == Status.NO_MONEY_STOP
                || status == Status.INVENTORY_FULL_STOP
                || status == Status.ERROR_STOP;
    }

    public boolean isLimitStop() {
        return status == Status.LIMIT_REACHED_STOP;
    }

    public boolean isContinueStatus() {
        return status == Status.WAIT_DELAY
                || status == Status.REFRESH_FAILED_CONTINUE
                || status == Status.NO_MATCH_CONTINUE
                || status == Status.BUY_SUCCESS_CONTINUE
                || status == Status.ALREADY_SOLD_CONTINUE
                || status == Status.PRICE_CHANGED_CONTINUE
                || status == Status.BUY_FAILED_CONTINUE
                || status == Status.BUY_TIMEOUT_CONTINUE
                || status == Status.BUY_AUCTION_CHANGED_CONTINUE
                || status == Status.BUY_SCREEN_CHANGED_CONTINUE;
    }

    public long getElapsedMs() {
        if (loopStartedAtMs <= 0L) {
            return 0L;
        }
        long end = eventAtMs <= 0L ? System.currentTimeMillis() : eventAtMs;
        return Math.max(0L, end - loopStartedAtMs);
    }

    public long getDelayLeftMs() {
        if (nextCycleAtMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, nextCycleAtMs - System.currentTimeMillis());
    }

    public String compact() {
        return "status=" + status
                + ", running=" + running
                + ", cycles=" + cyclesStarted + "/" + maxCycles
                + ", buys=" + buysDone + "/" + maxBuys
                + ", elapsedMs=" + getElapsedMs()
                + ", delayLeftMs=" + getDelayLeftMs()
                + ", hardStop=" + isHardStop()
                + ", msg=" + message;
    }
}
