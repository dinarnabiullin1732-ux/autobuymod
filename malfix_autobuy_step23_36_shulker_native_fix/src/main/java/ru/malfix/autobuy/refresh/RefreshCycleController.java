package ru.malfix.autobuy.refresh;

import ru.malfix.autobuy.auction.AuctionFingerprint;
import ru.malfix.autobuy.auction.AuctionSlot;
import ru.malfix.autobuy.auction.AuctionView;
import ru.malfix.autobuy.scanner.AuctionScanner;
import ru.malfix.autobuy.scanner.ScanResult;
import ru.malfix.autobuy.config.MalfixTimings;

import java.util.Collections;
import java.util.List;

/**
 * Step 22.60: restored the pre-Step-22.53 refresh behavior.
 *
 * The "old Spooky-style" one-shot refresh from Step 22.53/22.54 clicked refresh, waited
 * a tiny settle window and scanned once. On this server that can scan the still-old page and
 * make the buy loop miss valid lots after refresh.
 *
 * This controller again does the safer old Malfix flow:
 * read fingerprint before click -> click refresh -> poll until fingerprint changes or timeout
 * -> scan the resulting page. It may cost a little more around refresh, but it is the version
 * that preserved reliable buying.
 */
public final class RefreshCycleController {

    private final AuctionView auctionView;
    private final AuctionScanner scanner;

    private boolean pending;
    private long startedAtMs;
    private long lastPollAtMs;
    private int beforeFingerprint;
    private int beforeCheckedSlots;

    private RefreshCycleResult lastResult = RefreshCycleResult.idle();

    private static final long MIN_WAIT_AFTER_CLICK_MS = MalfixTimings.AB_BUY_MS;
    private static final long POLL_INTERVAL_MS = MalfixTimings.AB_BUY_MS;
    private static final long DEFAULT_TIMEOUT_MS = MalfixTimings.SMART_REOPEN_REFRESH_FAIL_MS;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    public RefreshCycleController(AuctionView auctionView, AuctionScanner scanner) {
        this.auctionView = auctionView;
        this.scanner = scanner;
    }

    public boolean start() {
        if (pending) {
            lastResult = RefreshCycleResult.error("refresh_cycle_already_pending");
            return false;
        }

        if (auctionView == null) {
            lastResult = RefreshCycleResult.error("auction_view_missing");
            return false;
        }

        if (!auctionView.isAuctionOpen()) {
            lastResult = RefreshCycleResult.error("auction_not_open");
            return false;
        }

        List<AuctionSlot> beforeSlots = safeReadSlots();
        beforeFingerprint = AuctionFingerprint.compute(beforeSlots);
        beforeCheckedSlots = beforeSlots.size();
        startedAtMs = System.currentTimeMillis();
        lastPollAtMs = 0L;
        boolean clickSent = auctionView.clickRefresh();
        if (!clickSent) {
            pending = false;
            lastResult = RefreshCycleResult.error("refresh_click_not_sent");
            return false;
        }

        pending = true;

        lastResult = RefreshCycleResult.started(startedAtMs, beforeFingerprint, beforeCheckedSlots);
        return true;
    }

    /**
     * @return completed result exactly once, otherwise null.
     */
    public RefreshCycleResult tick() {
        if (!pending) {
            return null;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - startedAtMs;

        lastResult = RefreshCycleResult.pending(startedAtMs, beforeFingerprint);

        if (auctionView == null || !auctionView.isAuctionOpen()) {
            return finishError("auction_closed_during_refresh_wait");
        }

        if (elapsed < MIN_WAIT_AFTER_CLICK_MS) {
            return null;
        }

        if (now - lastPollAtMs < POLL_INTERVAL_MS) {
            return null;
        }
        lastPollAtMs = now;

        List<AuctionSlot> afterSlots = safeReadSlots();
        int afterFingerprint = AuctionFingerprint.compute(afterSlots);
        boolean changed = afterFingerprint != beforeFingerprint;

        if (changed) {
            return finishSuccess(afterSlots, afterFingerprint);
        }

        if (elapsed >= timeoutMs) {
            return finishTimeout(afterSlots, afterFingerprint);
        }

        return null;
    }

    public void cancel(String reason) {
        if (!pending) {
            return;
        }
        pending = false;
        lastResult = RefreshCycleResult.error(reason == null ? "cancelled" : reason);
    }

    public boolean isPending() {
        return pending;
    }

    public RefreshCycleResult getLastResult() {
        return lastResult;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        if (timeoutMs < 150L) {
            this.timeoutMs = 150L;
        } else if (timeoutMs > 5000L) {
            this.timeoutMs = 5000L;
        } else {
            this.timeoutMs = timeoutMs;
        }
    }

    private RefreshCycleResult finishSuccess(List<AuctionSlot> afterSlots, int afterFingerprint) {
        pending = false;
        ScanResult scanResult = scan(afterSlots);
        lastResult = RefreshCycleResult.success(startedAtMs, beforeFingerprint, afterFingerprint, afterSlots.size(), scanResult);
        return lastResult;
    }

    private RefreshCycleResult finishTimeout(List<AuctionSlot> afterSlots, int afterFingerprint) {
        pending = false;
        ScanResult scanResult = scan(afterSlots);
        lastResult = RefreshCycleResult.timeout(startedAtMs, beforeFingerprint, afterFingerprint, afterSlots.size(), scanResult);
        return lastResult;
    }

    private RefreshCycleResult finishError(String message) {
        pending = false;
        lastResult = RefreshCycleResult.error(message);
        return lastResult;
    }

    private ScanResult scan(List<AuctionSlot> slots) {
        if (scanner == null) {
            return ScanResult.error("scanner_missing");
        }
        return scanner.scan(slots);
    }

    private List<AuctionSlot> safeReadSlots() {
        try {
            List<AuctionSlot> slots = auctionView.readAuctionSlots();
            return slots == null ? Collections.<AuctionSlot>emptyList() : slots;
        } catch (Throwable throwable) {
            return Collections.emptyList();
        }
    }
}
