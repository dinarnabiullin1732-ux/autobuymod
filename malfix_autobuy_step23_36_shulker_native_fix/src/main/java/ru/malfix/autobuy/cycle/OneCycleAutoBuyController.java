package ru.malfix.autobuy.cycle;

import ru.malfix.autobuy.auction.AuctionView;
import ru.malfix.autobuy.buy.BuyResult;
import ru.malfix.autobuy.buy.ControlledBuyClickExecutor;
import ru.malfix.autobuy.buy.ControlledBuyClickResult;
import ru.malfix.autobuy.refresh.RefreshCycleController;
import ru.malfix.autobuy.refresh.RefreshCycleResult;
import ru.malfix.autobuy.scanner.AuctionScanner;
import ru.malfix.autobuy.scanner.ScanCandidate;

/**
 * Step 9: one safe automatic cycle:
 *
 * refresh -> wait fingerprint changed -> scan -> controlled buy click -> wait buy result -> stop.
 *
 * This class does NOT loop forever. It intentionally performs only one attempt.
 */
public final class OneCycleAutoBuyController {

    private enum Phase {
        IDLE,
        WAIT_REFRESH,
        WAIT_BUY_RESULT,
        DONE
    }

    private final AuctionView auctionView;
    private final RefreshCycleController refreshCycle;
    private final ControlledBuyClickExecutor buyClick;

    private Phase phase = Phase.IDLE;
    private long startedAtMs = 0L;

    private RefreshCycleResult lastRefreshResult = RefreshCycleResult.idle();
    private OneCycleResult lastResult = OneCycleResult.idle();

    public OneCycleAutoBuyController(AuctionView auctionView, AuctionScanner scanner) {
        this.auctionView = auctionView;
        this.refreshCycle = new RefreshCycleController(auctionView, scanner);
        this.buyClick = new ControlledBuyClickExecutor(auctionView, scanner);
    }

    public boolean start() {
        if (isPending()) {
            lastResult = OneCycleResult.error("one_cycle_already_pending");
            return false;
        }

        if (auctionView == null || !auctionView.isAuctionOpen()) {
            lastResult = OneCycleResult.error("auction_not_open");
            return false;
        }

        boolean refreshStarted = refreshCycle.start();
        if (!refreshStarted) {
            lastResult = OneCycleResult.refreshFailed(System.currentTimeMillis(), refreshCycle.getLastResult(), "refresh_start_failed");
            phase = Phase.DONE;
            return false;
        }

        startedAtMs = System.currentTimeMillis();
        phase = Phase.WAIT_REFRESH;
        lastRefreshResult = refreshCycle.getLastResult();
        lastResult = OneCycleResult.refreshing(startedAtMs, lastRefreshResult);
        return true;
    }

    /**
     * @return a new event/status when something important happens; otherwise null.
     */
    public OneCycleResult tick() {
        if (phase == Phase.IDLE || phase == Phase.DONE) {
            return null;
        }

        if (phase == Phase.WAIT_REFRESH) {
            RefreshCycleResult refreshResult = refreshCycle.tick();
            if (refreshResult == null) {
                lastResult = OneCycleResult.refreshing(startedAtMs, refreshCycle.getLastResult());
                return null;
            }

            lastRefreshResult = refreshResult;

            if (!RefreshCycleResult.STATUS_SUCCESS_CHANGED.equals(refreshResult.getStatus())) {
                return finish(OneCycleResult.refreshFailed(startedAtMs, refreshResult, "refresh_failed_or_no_change: " + refreshResult.getStatus()));
            }

            ScanCandidate best = refreshResult.getBestCandidate();
            if (best == null) {
                return finish(OneCycleResult.noMatch(startedAtMs, refreshResult));
            }

            lastResult = OneCycleResult.readyToBuy(startedAtMs, refreshResult, best);

            boolean buyStarted = buyClick.start();
            ControlledBuyClickResult buyResult = buyClick.getLastResult();

            if (!buyStarted) {
                return finish(OneCycleResult.buyStartFailed(startedAtMs, refreshResult, buyResult));
            }

            phase = Phase.WAIT_BUY_RESULT;
            lastResult = OneCycleResult.buyClicked(startedAtMs, refreshResult, buyResult);
            return lastResult;
        }

        if (phase == Phase.WAIT_BUY_RESULT) {
            ControlledBuyClickResult buyResult = buyClick.tick();
            if (buyResult == null) {
                return null;
            }

            return finish(OneCycleResult.fromBuyClick(startedAtMs, lastRefreshResult, buyResult));
        }

        return null;
    }

    public OneCycleResult onBuyResult(BuyResult buyResult) {
        if (phase != Phase.WAIT_BUY_RESULT || buyResult == null || !buyResult.isDetected()) {
            return null;
        }

        ControlledBuyClickResult clickResult = buyClick.onBuyResult(buyResult);
        if (clickResult == null) {
            return null;
        }

        return finish(OneCycleResult.fromBuyClick(startedAtMs, lastRefreshResult, clickResult));
    }

    public void cancel(String reason) {
        if (phase == Phase.IDLE || phase == Phase.DONE) {
            return;
        }

        refreshCycle.cancel(reason);
        buyClick.cancel(reason);
        phase = Phase.DONE;
        lastResult = OneCycleResult.error(reason == null ? "one_cycle_cancelled" : reason);
    }

    public boolean isPending() {
        return phase == Phase.WAIT_REFRESH || phase == Phase.WAIT_BUY_RESULT;
    }

    public boolean isWaitingRefresh() {
        return phase == Phase.WAIT_REFRESH;
    }

    public boolean isWaitingBuyResult() {
        return phase == Phase.WAIT_BUY_RESULT;
    }

    public void setRefreshTimeoutMs(long refreshTimeoutMs) {
        refreshCycle.setTimeoutMs(refreshTimeoutMs);
    }

    public OneCycleResult getLastResult() {
        return lastResult;
    }

    public RefreshCycleResult getLastRefreshResult() {
        return lastRefreshResult;
    }

    private OneCycleResult finish(OneCycleResult result) {
        phase = Phase.DONE;
        lastResult = result;
        return result;
    }
}
