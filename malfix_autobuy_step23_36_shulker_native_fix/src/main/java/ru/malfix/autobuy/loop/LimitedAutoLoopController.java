package ru.malfix.autobuy.loop;

import ru.malfix.autobuy.auction.AuctionView;
import ru.malfix.autobuy.buy.BuyResult;
import ru.malfix.autobuy.buy.ControlledBuyClickExecutor;
import ru.malfix.autobuy.buy.ControlledBuyClickResult;
import ru.malfix.autobuy.refresh.RefreshCycleController;
import ru.malfix.autobuy.refresh.RefreshCycleResult;
import ru.malfix.autobuy.scanner.AuctionScanner;
import ru.malfix.autobuy.scanner.ScanCandidate;
import ru.malfix.autobuy.config.MalfixTimings;

/**
 * Step 10: limited safe-loop.
 *
 * This is still NOT a final full-auto autobuyer.
 * It runs several controlled cycles with strict limits:
 *
 * refresh -> scan -> controlled buy click -> result -> delay -> next cycle
 *
 * Step 22.61: delayBetweenCyclesMs is treated as refresh-click cadence. The next
 * refresh is scheduled from the previous refresh click timestamp, not from the end of
 * fingerprint polling/scanning. With AB_UPDATE_MS=300 this keeps refresh clicks around
 * one per 300ms without returning to the unreliable one-shot scan.
 *
 * Step 22.72/22.73: buy-timeout anti-freeze. If a real buy click times out while
 * the page is still unchanged, treat the auction GUI as stale and close/reopen /ah
 * instead of clicking the frozen profitable-looking lot again.
 */
public final class LimitedAutoLoopController {

    private enum Phase {
        IDLE,
        WAIT_DELAY,
        WAIT_REFRESH,
        WAIT_BUY_RESULT,
        WAIT_REOPEN_AUCTION,
        STOPPED
    }

    private final AuctionView auctionView;
    private final RefreshCycleController refreshCycle;
    private final ControlledBuyClickExecutor buyClick;

    private Phase phase = Phase.IDLE;

    private long loopStartedAtMs = 0L;
    private long nextCycleAtMs = 0L;
    private long lastRefreshStartedAtMs = 0L;

    private int cyclesStarted = 0;
    private int maxCycles = 10;
    private int buysDone = 0;
    private int maxBuys = 3;
    private boolean timeLimitMode = false;
    private long maxRuntimeMs = 0L;

    private long delayBetweenCyclesMs = MalfixTimings.AB_UPDATE_MS;
    private long successCooldownMs = MalfixTimings.AB_BUY_MS;
    private int refreshFailStreak = 0;
    private int maxRefreshFailStreak = MalfixTimings.DEFAULT_MAX_REFRESH_FAIL_STREAK;

    private int noChangeRefreshStreak = 0;
    private long lastBuyClickAtMs = 0L;
    private long lastBuyFinalAtMs = 0L;
    private long lastSmartReopenAtMs = 0L;
    private boolean smartReopenCommandSent = false;
    private int smartReopenAttempts = 0;
    private String lastSmartReopenReason = "none";

    private int buyTimeoutSamePageStreak = 0;
    private long lastBuyTimeoutAtMs = 0L;
    private String lastBuyTimeoutSignature = "";

    private RefreshCycleResult lastRefreshResult = RefreshCycleResult.idle();
    private AutoLoopResult lastResult = AutoLoopResult.idle();

    public LimitedAutoLoopController(AuctionView auctionView, AuctionScanner scanner) {
        this.auctionView = auctionView;
        this.refreshCycle = new RefreshCycleController(auctionView, scanner);
        this.buyClick = new ControlledBuyClickExecutor(auctionView, scanner);
    }

    public boolean start(int requestedMaxCycles, int requestedMaxBuys) {
        if (isRunning()) {
            lastResult = AutoLoopResult.of(
                    AutoLoopResult.Status.ERROR_STOP,
                    true,
                    "loop_already_running",
                    loopStartedAtMs,
                    nextCycleAtMs,
                    cyclesStarted,
                    maxCycles,
                    buysDone,
                    maxBuys,
                    lastRefreshResult,
                    buyClick.getLastResult(),
                    null
            );
            return false;
        }

        if (auctionView == null || !auctionView.isAuctionOpen()) {
            lastResult = AutoLoopResult.of(
                    AutoLoopResult.Status.ERROR_STOP,
                    false,
                    "auction_not_open",
                    System.currentTimeMillis(),
                    0L,
                    0,
                    clampCycles(requestedMaxCycles),
                    0,
                    clampBuys(requestedMaxBuys),
                    null,
                    null,
                    null
            );
            phase = Phase.STOPPED;
            return false;
        }

        this.timeLimitMode = false;
        this.maxRuntimeMs = 0L;
        this.maxCycles = clampCycles(requestedMaxCycles);
        this.maxBuys = clampBuys(requestedMaxBuys);
        this.cyclesStarted = 0;
        this.buysDone = 0;
        this.refreshFailStreak = 0;
        this.noChangeRefreshStreak = 0;
        this.lastBuyClickAtMs = 0L;
        this.lastBuyFinalAtMs = 0L;
        this.lastSmartReopenReason = "none";
        resetBuyTimeoutGuard();
        this.smartReopenCommandSent = false;
        this.smartReopenAttempts = 0;
        this.loopStartedAtMs = System.currentTimeMillis();
        this.nextCycleAtMs = loopStartedAtMs;
        this.lastRefreshStartedAtMs = 0L;
        this.lastRefreshResult = RefreshCycleResult.idle();
        this.phase = Phase.WAIT_DELAY;

        lastResult = AutoLoopResult.of(
                AutoLoopResult.Status.STARTED,
                true,
                "limited_loop_started",
                loopStartedAtMs,
                nextCycleAtMs,
                cyclesStarted,
                maxCycles,
                buysDone,
                maxBuys,
                null,
                null,
                null
        );

        return true;
    }

    /**
     * Starts the same refresh -> scan -> buy loop, but stops by time instead of cycle/buy counters.
     * Used by FullAuto timed mode: 90 seconds of auction refresh+buy, then storage/seller phase.
     */
    public boolean startTimed(long requestedRuntimeMs) {
        if (isRunning()) {
            lastResult = AutoLoopResult.of(
                    AutoLoopResult.Status.ERROR_STOP,
                    true,
                    "loop_already_running",
                    loopStartedAtMs,
                    nextCycleAtMs,
                    cyclesStarted,
                    maxCycles,
                    buysDone,
                    maxBuys,
                    lastRefreshResult,
                    buyClick.getLastResult(),
                    null
            );
            return false;
        }

        if (auctionView == null || !auctionView.isAuctionOpen()) {
            lastResult = AutoLoopResult.of(
                    AutoLoopResult.Status.ERROR_STOP,
                    false,
                    "auction_not_open",
                    System.currentTimeMillis(),
                    0L,
                    0,
                    Integer.MAX_VALUE,
                    0,
                    Integer.MAX_VALUE,
                    null,
                    null,
                    null
            );
            phase = Phase.STOPPED;
            return false;
        }

        if (requestedRuntimeMs < 5_000L) {
            requestedRuntimeMs = 5_000L;
        } else if (requestedRuntimeMs > 600_000L) {
            requestedRuntimeMs = 600_000L;
        }

        this.timeLimitMode = true;
        this.maxRuntimeMs = requestedRuntimeMs;
        this.maxCycles = Integer.MAX_VALUE;
        this.maxBuys = Integer.MAX_VALUE;
        this.cyclesStarted = 0;
        this.buysDone = 0;
        this.refreshFailStreak = 0;
        this.noChangeRefreshStreak = 0;
        this.lastBuyClickAtMs = 0L;
        this.lastBuyFinalAtMs = 0L;
        this.lastSmartReopenReason = "none";
        resetBuyTimeoutGuard();
        this.smartReopenCommandSent = false;
        this.smartReopenAttempts = 0;
        this.loopStartedAtMs = System.currentTimeMillis();
        this.nextCycleAtMs = loopStartedAtMs;
        this.lastRefreshStartedAtMs = 0L;
        this.lastRefreshResult = RefreshCycleResult.idle();
        this.phase = Phase.WAIT_DELAY;

        lastResult = AutoLoopResult.of(
                AutoLoopResult.Status.STARTED,
                true,
                "timed_loop_started: runtimeMs=" + maxRuntimeMs,
                loopStartedAtMs,
                nextCycleAtMs,
                cyclesStarted,
                maxCycles,
                buysDone,
                maxBuys,
                null,
                null,
                null
        );

        return true;
    }

    /**
     * @return a new event/status when something important happens; otherwise null.
     */
    public AutoLoopResult tick() {
        if (!isRunning()) {
            return null;
        }

        long now = System.currentTimeMillis();

        if (phase == Phase.WAIT_REOPEN_AUCTION) {
            return tickSmartReopen(now);
        }

        if (phase == Phase.WAIT_DELAY) {
            if (timeLimitMode) {
                long elapsed = now - loopStartedAtMs;
                if (elapsed >= maxRuntimeMs) {
                    return stopWith(AutoLoopResult.Status.LIMIT_REACHED_STOP, "time_limit_reached:" + maxRuntimeMs + "ms", lastRefreshResult, buyClick.getLastResult(), null);
                }
            } else {
                if (cyclesStarted >= maxCycles) {
                    return stopWith(AutoLoopResult.Status.LIMIT_REACHED_STOP, "max_cycles_reached", null, buyClick.getLastResult(), null);
                }

                if (buysDone >= maxBuys) {
                    return stopWith(AutoLoopResult.Status.LIMIT_REACHED_STOP, "max_buys_reached", lastRefreshResult, buyClick.getLastResult(), null);
                }
            }

            if (now < nextCycleAtMs) {
                lastResult = AutoLoopResult.of(
                        AutoLoopResult.Status.WAIT_DELAY,
                        true,
                        "waiting_before_next_cycle",
                        loopStartedAtMs,
                        nextCycleAtMs,
                        cyclesStarted,
                        maxCycles,
                        buysDone,
                        maxBuys,
                        lastRefreshResult,
                        buyClick.getLastResult(),
                        null
                );
                return null;
            }

            return startNextRefresh();
        }

        if (phase == Phase.WAIT_REFRESH) {
            RefreshCycleResult refreshResult = refreshCycle.tick();
            if (refreshResult == null) {
                lastResult = AutoLoopResult.of(
                        AutoLoopResult.Status.REFRESHING,
                        true,
                        "refreshing",
                        loopStartedAtMs,
                        nextCycleAtMs,
                        cyclesStarted,
                        maxCycles,
                        buysDone,
                        maxBuys,
                        refreshCycle.getLastResult(),
                        buyClick.getLastResult(),
                        null
                );
                return null;
            }

            lastRefreshResult = refreshResult;

            boolean refreshChanged = RefreshCycleResult.STATUS_SUCCESS_CHANGED.equals(refreshResult.getStatus());
            boolean refreshNoChange = RefreshCycleResult.STATUS_TIMEOUT_NO_CHANGE.equals(refreshResult.getStatus());

            if (!refreshChanged && !refreshNoChange) {
                refreshFailStreak++;

                if (refreshFailStreak >= maxRefreshFailStreak) {
                    return stopWith(
                            AutoLoopResult.Status.ERROR_STOP,
                            "max_refresh_fail_streak_reached: " + refreshFailStreak + "/" + maxRefreshFailStreak,
                            refreshResult,
                            buyClick.getLastResult(),
                            null
                    );
                }

                scheduleNextCycle();
                return AutoLoopResult.of(
                        AutoLoopResult.Status.REFRESH_FAILED_CONTINUE,
                        true,
                        "refresh_failed: " + refreshResult.getStatus()
                                + ", streak=" + refreshFailStreak + "/" + maxRefreshFailStreak,
                        loopStartedAtMs,
                        nextCycleAtMs,
                        cyclesStarted,
                        maxCycles,
                        buysDone,
                        maxBuys,
                        refreshResult,
                        buyClick.getLastResult(),
                        null
                );
            }

            if (refreshChanged) {
                noChangeRefreshStreak = 0;
            } else if (refreshNoChange) {
                noChangeRefreshStreak++;

                if (shouldSmartReopenAfterNoChange(now)) {
                    return triggerSmartReopen(buildSmartReopenReason(now), refreshResult);
                }
            }

            // No-change after a refresh click is not immediately a hard failure on this server:
            // sometimes the visible page stays identical. Scan the current page anyway.
            // But if the same fingerprint repeats for several refreshes, especially after a
            // buy click, the server-side auction window is likely frozen and must be reopened.
            refreshFailStreak = 0;

            ScanCandidate best = refreshResult.getBestCandidate();
            if (best == null) {
                scheduleNextCycle();
                return AutoLoopResult.of(
                        AutoLoopResult.Status.NO_MATCH_CONTINUE,
                        true,
                        "no_matching_lot",
                        loopStartedAtMs,
                        nextCycleAtMs,
                        cyclesStarted,
                        maxCycles,
                        buysDone,
                        maxBuys,
                        refreshResult,
                        buyClick.getLastResult(),
                        null
                );
            }

            boolean buyStarted = buyClick.start();
            ControlledBuyClickResult buyResult = buyClick.getLastResult();

            if (buyStarted) {
                lastBuyClickAtMs = System.currentTimeMillis();
            }

            if (!buyStarted) {
                scheduleNextCycle();
                return AutoLoopResult.of(
                        AutoLoopResult.Status.BUY_FAILED_CONTINUE,
                        true,
                        "buy_start_failed: " + buyResult.getStatus(),
                        loopStartedAtMs,
                        nextCycleAtMs,
                        cyclesStarted,
                        maxCycles,
                        buysDone,
                        maxBuys,
                        refreshResult,
                        buyResult,
                        buyResult.getCandidate() == null ? best : buyResult.getCandidate()
                );
            }

            phase = Phase.WAIT_BUY_RESULT;
            lastResult = AutoLoopResult.of(
                    AutoLoopResult.Status.BUY_CLICKED,
                    true,
                    "buy_clicked_waiting_result",
                    loopStartedAtMs,
                    nextCycleAtMs,
                    cyclesStarted,
                    maxCycles,
                    buysDone,
                    maxBuys,
                    refreshResult,
                    buyResult,
                    buyResult.getCandidate()
            );
            return lastResult;
        }

        if (phase == Phase.WAIT_BUY_RESULT) {
            ControlledBuyClickResult buyResult = buyClick.tick();
            if (buyResult == null) {
                return null;
            }

            return handleFinalBuyResult(buyResult);
        }

        return null;
    }

    public AutoLoopResult onBuyResult(BuyResult buyResult) {
        if (phase != Phase.WAIT_BUY_RESULT || buyResult == null || !buyResult.isDetected()) {
            return null;
        }

        ControlledBuyClickResult clickResult = buyClick.onBuyResult(buyResult);
        if (clickResult == null) {
            return null;
        }

        return handleFinalBuyResult(clickResult);
    }

    public void stop(String reason) {
        if (!isRunning()) {
            lastResult = AutoLoopResult.of(
                    AutoLoopResult.Status.MANUAL_STOP,
                    false,
                    reason == null ? "manual_stop" : reason,
                    loopStartedAtMs,
                    0L,
                    cyclesStarted,
                    maxCycles,
                    buysDone,
                    maxBuys,
                    lastRefreshResult,
                    buyClick.getLastResult(),
                    null
            );
            timeLimitMode = false;
            maxRuntimeMs = 0L;
            phase = Phase.STOPPED;
            return;
        }

        refreshCycle.cancel(reason);
        buyClick.cancel(reason);
        timeLimitMode = false;
        maxRuntimeMs = 0L;

        lastResult = AutoLoopResult.of(
                AutoLoopResult.Status.MANUAL_STOP,
                false,
                reason == null ? "manual_stop" : reason,
                loopStartedAtMs,
                0L,
                cyclesStarted,
                maxCycles,
                buysDone,
                maxBuys,
                lastRefreshResult,
                buyClick.getLastResult(),
                null
        );
        phase = Phase.STOPPED;
    }

    public boolean isRunning() {
        return phase == Phase.WAIT_DELAY
                || phase == Phase.WAIT_REFRESH
                || phase == Phase.WAIT_BUY_RESULT
                || phase == Phase.WAIT_REOPEN_AUCTION;
    }

    public boolean isWaitingBuyResult() {
        return phase == Phase.WAIT_BUY_RESULT;
    }

    public boolean isWaitingRefresh() {
        return phase == Phase.WAIT_REFRESH;
    }

    public AutoLoopResult getLastResult() {
        return lastResult;
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

    public boolean isTimeLimitMode() {
        return timeLimitMode;
    }

    public long getMaxRuntimeMs() {
        return maxRuntimeMs;
    }

    public long getElapsedRuntimeMs() {
        return loopStartedAtMs <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - loopStartedAtMs);
    }

    public long getRemainingRuntimeMs() {
        if (!timeLimitMode || maxRuntimeMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, maxRuntimeMs - getElapsedRuntimeMs());
    }

    public long getLoopStartedAtMs() {
        return loopStartedAtMs;
    }

    public long getLastRefreshStartedAtMs() {
        return lastRefreshStartedAtMs;
    }

    public long getDelayBetweenCyclesMs() {
        return delayBetweenCyclesMs;
    }

    public void setDelayBetweenCyclesMs(long delayBetweenCyclesMs) {
        if (delayBetweenCyclesMs < MalfixTimings.AB_UPDATE_MS) {
            this.delayBetweenCyclesMs = MalfixTimings.AB_UPDATE_MS;
        } else if (delayBetweenCyclesMs > 30000L) {
            this.delayBetweenCyclesMs = 30000L;
        } else {
            this.delayBetweenCyclesMs = delayBetweenCyclesMs;
        }
    }

    public void setRefreshTimeoutMs(long refreshTimeoutMs) {
        refreshCycle.setTimeoutMs(refreshTimeoutMs);
    }

    public long getRefreshTimeoutMs() {
        return refreshCycle.getTimeoutMs();
    }

    public void setMaxRefreshFailStreak(int maxRefreshFailStreak) {
        if (maxRefreshFailStreak < 1) {
            this.maxRefreshFailStreak = 1;
        } else if (maxRefreshFailStreak > 20) {
            this.maxRefreshFailStreak = 20;
        } else {
            this.maxRefreshFailStreak = maxRefreshFailStreak;
        }
    }

    public int getMaxRefreshFailStreak() {
        return maxRefreshFailStreak;
    }

    public int getRefreshFailStreak() {
        return refreshFailStreak;
    }

    public int getNoChangeRefreshStreak() {
        return noChangeRefreshStreak;
    }

    public String getLastSmartReopenReason() {
        return lastSmartReopenReason;
    }

    public long getLastSmartReopenAtMs() {
        return lastSmartReopenAtMs;
    }

    public void setSuccessCooldownMs(long successCooldownMs) {
        if (successCooldownMs < 1L) {
            this.successCooldownMs = 1L;
        } else if (successCooldownMs > 5000L) {
            this.successCooldownMs = 5000L;
        } else {
            this.successCooldownMs = successCooldownMs;
        }
    }

    public long getSuccessCooldownMs() {
        return successCooldownMs;
    }

    private AutoLoopResult startNextRefresh() {
        if (auctionView == null || !auctionView.isAuctionOpen()) {
            return stopWith(AutoLoopResult.Status.ERROR_STOP, "auction_closed_before_refresh", lastRefreshResult, buyClick.getLastResult(), null);
        }

        if (timeLimitMode && System.currentTimeMillis() - loopStartedAtMs >= maxRuntimeMs) {
            return stopWith(AutoLoopResult.Status.LIMIT_REACHED_STOP, "time_limit_reached:" + maxRuntimeMs + "ms", lastRefreshResult, buyClick.getLastResult(), null);
        }

        smartReopenCommandSent = false;
        smartReopenAttempts = 0;

        cyclesStarted++;

        boolean started = refreshCycle.start();
        RefreshCycleResult refreshResult = refreshCycle.getLastResult();
        lastRefreshResult = refreshResult;
        if (started && refreshResult != null && refreshResult.getStartedAtMs() > 0L) {
            lastRefreshStartedAtMs = refreshResult.getStartedAtMs();
        }

        if (!started) {
            refreshFailStreak++;

            if (refreshFailStreak >= maxRefreshFailStreak) {
                return stopWith(
                        AutoLoopResult.Status.ERROR_STOP,
                        "max_refresh_fail_streak_reached_on_start: " + refreshFailStreak + "/" + maxRefreshFailStreak,
                        refreshResult,
                        buyClick.getLastResult(),
                        null
                );
            }

            scheduleNextCycle();
            return AutoLoopResult.of(
                    AutoLoopResult.Status.REFRESH_FAILED_CONTINUE,
                    true,
                    "refresh_start_failed, streak=" + refreshFailStreak + "/" + maxRefreshFailStreak,
                    loopStartedAtMs,
                    nextCycleAtMs,
                    cyclesStarted,
                    maxCycles,
                    buysDone,
                    maxBuys,
                    refreshResult,
                    buyClick.getLastResult(),
                    null
            );
        }

        phase = Phase.WAIT_REFRESH;
        lastResult = AutoLoopResult.of(
                AutoLoopResult.Status.REFRESHING,
                true,
                "refresh_started",
                loopStartedAtMs,
                nextCycleAtMs,
                cyclesStarted,
                maxCycles,
                buysDone,
                maxBuys,
                refreshResult,
                buyClick.getLastResult(),
                null
        );
        return lastResult;
    }

    private AutoLoopResult handleFinalBuyResult(ControlledBuyClickResult buyResult) {
        AutoLoopResult.Status status = mapBuyStatus(buyResult == null ? null : buyResult.getStatus());

        if (buyResult != null) {
            lastBuyFinalAtMs = System.currentTimeMillis();
        }

        if (buyResult != null && buyResult.getStatus() == ControlledBuyClickResult.Status.TIMEOUT_STILL_SAME) {
            recordBuyTimeoutAndShouldReopen(buyResult);
            return triggerSmartReopen(buildBuyTimeoutReopenReason(), lastRefreshResult);
        } else if (buyResult != null && buyResult.getStatus() != ControlledBuyClickResult.Status.CLICKED_PENDING) {
            resetBuyTimeoutGuard();
        }

        if (status == AutoLoopResult.Status.BUY_SUCCESS_CONTINUE) {
            buysDone++;
        }

        if (status == AutoLoopResult.Status.NO_MONEY_STOP
                || status == AutoLoopResult.Status.INVENTORY_FULL_STOP
                || status == AutoLoopResult.Status.ERROR_STOP) {
            return stopWith(status, status.name().toLowerCase(), lastRefreshResult, buyResult, buyResult == null ? null : buyResult.getCandidate());
        }

        if (!timeLimitMode && buysDone >= maxBuys) {
            return stopWith(AutoLoopResult.Status.LIMIT_REACHED_STOP, "max_buys_reached_after_buy", lastRefreshResult, buyResult, buyResult == null ? null : buyResult.getCandidate());
        }

        scheduleNextCycleAfterBuyStatus(status);
        lastResult = AutoLoopResult.of(
                status,
                true,
                status.name().toLowerCase(),
                loopStartedAtMs,
                nextCycleAtMs,
                cyclesStarted,
                maxCycles,
                buysDone,
                maxBuys,
                lastRefreshResult,
                buyResult,
                buyResult == null ? null : buyResult.getCandidate()
        );
        return lastResult;
    }

    private boolean recordBuyTimeoutAndShouldReopen(ControlledBuyClickResult buyResult) {
        long now = System.currentTimeMillis();
        String signature = buildBuyTimeoutSignature(buyResult);

        boolean sameTimeoutSeries = !signature.isEmpty()
                && signature.equals(lastBuyTimeoutSignature)
                && lastBuyTimeoutAtMs > 0L
                && now - lastBuyTimeoutAtMs <= MalfixTimings.FULL_AUTO_BUY_TIMEOUT_REOPEN_WINDOW_MS;

        if (sameTimeoutSeries) {
            buyTimeoutSamePageStreak++;
        } else {
            buyTimeoutSamePageStreak = 1;
            lastBuyTimeoutSignature = signature;
        }

        lastBuyTimeoutAtMs = now;

        return buyTimeoutSamePageStreak >= MalfixTimings.FULL_AUTO_BUY_TIMEOUT_REOPEN_STREAK
                && auctionView != null
                && auctionView.isAuctionOpen();
    }

    private String buildBuyTimeoutSignature(ControlledBuyClickResult buyResult) {
        if (buyResult == null) {
            return "";
        }

        ScanCandidate candidate = buyResult.getCandidate();
        if (candidate == null || candidate.getAuctionSlot() == null) {
            return "fp=" + buyResult.getBeforeFingerprint();
        }

        StringBuilder builder = new StringBuilder(96);
        builder.append("fp=").append(buyResult.getBeforeFingerprint());
        builder.append("|slot=").append(candidate.getAuctionSlot().getContainerSlotId());
        builder.append("|idx=").append(candidate.getAuctionSlot().getAuctionIndex());
        builder.append("|id=").append(candidate.getAuctionSlot().getItemId());
        builder.append("|name=").append(candidate.getAuctionSlot().getDisplayName());
        builder.append("|count=").append(candidate.getAuctionSlot().getCount());
        if (candidate.getPrice() != null) {
            builder.append("|total=").append(candidate.getPrice().getTotalPrice());
            builder.append("|unit=").append(candidate.getPrice().getUnitPrice());
        }
        return builder.toString();
    }

    private String buildBuyTimeoutReopenReason() {
        return "buy_timeout_reopen=" + buyTimeoutSamePageStreak
                + "/" + MalfixTimings.FULL_AUTO_BUY_TIMEOUT_REOPEN_STREAK
                + ":timeout_still_same";
    }

    private void resetBuyTimeoutGuard() {
        buyTimeoutSamePageStreak = 0;
        lastBuyTimeoutAtMs = 0L;
        lastBuyTimeoutSignature = "";
    }

    private boolean shouldSmartReopenAfterNoChange(long now) {
        if (auctionView == null || !auctionView.isAuctionOpen()) {
            return false;
        }

        if (now - lastSmartReopenAtMs < MalfixTimings.FULL_AUTO_REOPEN_COOLDOWN_MS) {
            return false;
        }

        int required = isInsidePostBuyStuckWindow(now)
                ? MalfixTimings.FULL_AUTO_POST_BUY_NO_CHANGE_REOPEN_STREAK
                : MalfixTimings.FULL_AUTO_GENERAL_NO_CHANGE_REOPEN_STREAK;

        return noChangeRefreshStreak >= required;
    }

    private boolean isInsidePostBuyStuckWindow(long now) {
        long lastBuyActivity = Math.max(lastBuyClickAtMs, lastBuyFinalAtMs);
        return lastBuyActivity > 0L
                && now - lastBuyActivity <= MalfixTimings.FULL_AUTO_POST_BUY_STUCK_WINDOW_MS;
    }

    private String buildSmartReopenReason(long now) {
        return isInsidePostBuyStuckWindow(now)
                ? "post_buy_no_change_streak=" + noChangeRefreshStreak
                : "general_no_change_streak=" + noChangeRefreshStreak;
    }

    private AutoLoopResult triggerSmartReopen(String reason, RefreshCycleResult refreshResult) {
        long now = System.currentTimeMillis();
        lastSmartReopenAtMs = now;
        lastSmartReopenReason = reason == null ? "smart_reopen" : reason;
        smartReopenCommandSent = false;
        smartReopenAttempts = 0;
        resetBuyTimeoutGuard();
        phase = Phase.WAIT_REOPEN_AUCTION;
        nextCycleAtMs = now + MalfixTimings.FULL_AUTO_REOPEN_CLOSE_WAIT_MS;

        try {
            if (auctionView != null) {
                auctionView.closeCurrentScreen();
            }
        } catch (Throwable ignored) {
        }

        lastResult = AutoLoopResult.of(
                AutoLoopResult.Status.REFRESH_FAILED_CONTINUE,
                true,
                "smart_reopen_close_auction: " + lastSmartReopenReason,
                loopStartedAtMs,
                nextCycleAtMs,
                cyclesStarted,
                maxCycles,
                buysDone,
                maxBuys,
                refreshResult,
                buyClick.getLastResult(),
                null
        );
        return lastResult;
    }

    private AutoLoopResult tickSmartReopen(long now) {
        if (now < nextCycleAtMs) {
            lastResult = AutoLoopResult.of(
                    AutoLoopResult.Status.REFRESH_FAILED_CONTINUE,
                    true,
                    "smart_reopen_wait_close: " + lastSmartReopenReason,
                    loopStartedAtMs,
                    nextCycleAtMs,
                    cyclesStarted,
                    maxCycles,
                    buysDone,
                    maxBuys,
                    lastRefreshResult,
                    buyClick.getLastResult(),
                    null
            );
            return null;
        }

        if (!smartReopenCommandSent) {
            try {
                if (auctionView != null) {
                    auctionView.requestOpenAuction();
                }
            } catch (Throwable ignored) {
            }
            smartReopenCommandSent = true;
            smartReopenAttempts = 1;
            nextCycleAtMs = now + MalfixTimings.FULL_AUTO_REOPEN_OPEN_WAIT_MS;
            lastResult = AutoLoopResult.of(
                    AutoLoopResult.Status.REFRESH_FAILED_CONTINUE,
                    true,
                    "smart_reopen_send_ah: " + lastSmartReopenReason,
                    loopStartedAtMs,
                    nextCycleAtMs,
                    cyclesStarted,
                    maxCycles,
                    buysDone,
                    maxBuys,
                    lastRefreshResult,
                    buyClick.getLastResult(),
                    null
            );
            return lastResult;
        }

        if (auctionView != null && auctionView.isAuctionOpen()) {
            refreshFailStreak = 0;
            noChangeRefreshStreak = 0;
            resetBuyTimeoutGuard();
            smartReopenCommandSent = false;
            smartReopenAttempts = 0;
            nextCycleAtMs = now;
            return startNextRefresh();
        }

        if (smartReopenAttempts >= MalfixTimings.FULL_AUTO_REOPEN_MAX_ATTEMPTS) {
            return stopWith(
                    AutoLoopResult.Status.ERROR_STOP,
                    "smart_reopen_failed: " + lastSmartReopenReason,
                    lastRefreshResult,
                    buyClick.getLastResult(),
                    null
            );
        }

        try {
            if (auctionView != null) {
                auctionView.requestOpenAuction();
            }
        } catch (Throwable ignored) {
        }
        smartReopenAttempts++;
        nextCycleAtMs = now + MalfixTimings.FULL_AUTO_REOPEN_OPEN_WAIT_MS;
        lastResult = AutoLoopResult.of(
                AutoLoopResult.Status.REFRESH_FAILED_CONTINUE,
                true,
                "smart_reopen_retry_ah=" + smartReopenAttempts + ": " + lastSmartReopenReason,
                loopStartedAtMs,
                nextCycleAtMs,
                cyclesStarted,
                maxCycles,
                buysDone,
                maxBuys,
                lastRefreshResult,
                buyClick.getLastResult(),
                null
        );
        return lastResult;
    }

    private AutoLoopResult stopWith(
            AutoLoopResult.Status status,
            String message,
            RefreshCycleResult refreshResult,
            ControlledBuyClickResult buyResult,
            ScanCandidate candidate
    ) {
        phase = Phase.STOPPED;
        lastResult = AutoLoopResult.of(
                status,
                false,
                message,
                loopStartedAtMs,
                0L,
                cyclesStarted,
                maxCycles,
                buysDone,
                maxBuys,
                refreshResult,
                buyResult,
                candidate
        );
        return lastResult;
    }

    private void scheduleNextCycle() {
        phase = Phase.WAIT_DELAY;
        scheduleNextCycleByRefreshCadence(delayBetweenCyclesMs);
    }

    private void scheduleNextCycleAfterBuyStatus(AutoLoopResult.Status status) {
        phase = Phase.WAIT_DELAY;
        long cadenceMs = delayBetweenCyclesMs;

        if (status == AutoLoopResult.Status.BUY_SUCCESS_CONTINUE) {
            cadenceMs = Math.max(delayBetweenCyclesMs, successCooldownMs);
        }

        scheduleNextCycleByRefreshCadence(cadenceMs);
    }

    /**
     * Schedule the next refresh click from the previous refresh click timestamp, not
     * from the moment scan/buy handling finished. This gives an actual click cadence
     * close to AB_UPDATE_MS=300 while keeping fingerprint-based refresh reliability.
     */
    private void scheduleNextCycleByRefreshCadence(long cadenceMs) {
        long now = System.currentTimeMillis();
        long safeCadence = cadenceMs < 1L ? 1L : cadenceMs;
        long base = lastRefreshStartedAtMs > 0L ? lastRefreshStartedAtMs : now;
        long target = base + safeCadence;
        nextCycleAtMs = Math.max(now, target);
    }

    private AutoLoopResult.Status mapBuyStatus(ControlledBuyClickResult.Status status) {
        if (status == null) {
            return AutoLoopResult.Status.BUY_FAILED_CONTINUE;
        }

        switch (status) {
            case BUY_SUCCESS:
                return AutoLoopResult.Status.BUY_SUCCESS_CONTINUE;
            case NO_MONEY:
                return AutoLoopResult.Status.NO_MONEY_STOP;
            case INVENTORY_FULL:
                return AutoLoopResult.Status.INVENTORY_FULL_STOP;
            case ALREADY_SOLD:
                return AutoLoopResult.Status.ALREADY_SOLD_CONTINUE;
            case PRICE_CHANGED:
                return AutoLoopResult.Status.PRICE_CHANGED_CONTINUE;
            case TIMEOUT_STILL_SAME:
                return AutoLoopResult.Status.BUY_TIMEOUT_CONTINUE;
            case AUCTION_CHANGED_AFTER_CLICK:
                return AutoLoopResult.Status.BUY_AUCTION_CHANGED_CONTINUE;
            case SCREEN_CHANGED_AFTER_CLICK:
                return AutoLoopResult.Status.BUY_SCREEN_CHANGED_CONTINUE;
            case BUY_FAILED_CHAT:
            case UNKNOWN_CHAT_RESULT:
            case VALIDATION_FAILED:
            case CLICK_FAILED:
            case ERROR:
                return AutoLoopResult.Status.BUY_FAILED_CONTINUE;
            default:
                return AutoLoopResult.Status.BUY_FAILED_CONTINUE;
        }
    }

    private int clampCycles(int value) {
        if (value <= 0) {
            return 10;
        }
        return Math.min(value, 1000);
    }

    private int clampBuys(int value) {
        if (value <= 0) {
            return 3;
        }
        return Math.min(value, 64);
    }
}
