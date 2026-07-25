package ru.malfix.autobuy.buy;

import ru.malfix.autobuy.auction.AuctionFingerprint;
import ru.malfix.autobuy.auction.AuctionSlot;
import ru.malfix.autobuy.auction.AuctionView;
import ru.malfix.autobuy.scanner.AuctionScanner;
import ru.malfix.autobuy.scanner.ScanCandidate;
import ru.malfix.autobuy.config.MalfixTimings;

import java.util.Collections;
import java.util.List;

/**
 * Step 8: controlled real buy click + chat result detection.
 *
 * Step 22.57: removed the same-slot buy retry. It was not reliable on the target
 * server and could add extra ignored clicks without improving buy confirmation.
 */
public final class ControlledBuyClickExecutor {

    private final AuctionView auctionView;
    private final BuyDryRunExecutor dryRunExecutor;

    private boolean pending;
    private long startedAtMs;
    private long lastPollAtMs;
    private int beforeFingerprint;
    private int checkedSlots;
    private ScanCandidate clickedCandidate;

    private ControlledBuyClickResult lastResult = ControlledBuyClickResult.idle();

    private static final long MIN_WAIT_AFTER_CLICK_MS = MalfixTimings.AB_BUY_MS;
    private static final long POLL_INTERVAL_MS = MalfixTimings.BUY_RESULT_POLL_MS;
    private static final long TIMEOUT_MS = MalfixTimings.CONTROLLED_BUY_RESULT_TIMEOUT_MS;
    private static final long NO_MONEY_GUARD_TTL_MS = 120_000L;
    private static long noMoneyGuardMinPrice = 0L;
    private static long noMoneyGuardAtMs = 0L;

    public ControlledBuyClickExecutor(AuctionView auctionView, AuctionScanner scanner) {
        this.auctionView = auctionView;
        this.dryRunExecutor = new BuyDryRunExecutor(auctionView, scanner);
    }

    /**
     * Validates the current best candidate and sends exactly one click.
     */
    public boolean start() {
        if (pending) {
            lastResult = ControlledBuyClickResult.validationFailed("buy_click_already_pending", null);
            return false;
        }

        if (auctionView == null || !auctionView.isAuctionOpen()) {
            lastResult = ControlledBuyClickResult.validationFailed("auction_not_open", null);
            return false;
        }

        BuyDryRunResult dryRun = dryRunExecutor.dryRun();
        if (dryRun == null || !dryRun.isReady() || dryRun.getCandidate() == null) {
            lastResult = ControlledBuyClickResult.validationFailed("dry_run_not_ready: " + (dryRun == null ? "null" : dryRun.getStatus()), dryRun);
            return false;
        }

        clickedCandidate = dryRun.getCandidate();
        beforeFingerprint = dryRun.getBeforeFingerprint();
        checkedSlots = dryRun.getCheckedSlots();
        long neededMoney = 0L;
        try {
            if (clickedCandidate != null && clickedCandidate.getPrice() != null) {
                neededMoney = clickedCandidate.getPrice().getTotalPrice();
            }
        } catch (Throwable ignored) {
            neededMoney = 0L;
        }

        long availableMoney = -1L;
        try {
            availableMoney = auctionView.readPlayerBalance();
        } catch (Throwable ignored) {
            availableMoney = -1L;
        }

        if (availableMoney >= 0L && neededMoney > 0L && availableMoney < neededMoney) {
            lastResult = ControlledBuyClickResult.validationFailed(
                    "balance_not_enough: balance=" + availableMoney + ", price=" + neededMoney,
                    dryRun
            );
            clearPendingCandidate();
            return false;
        }

        if (availableMoney >= 0L && neededMoney > 0L && availableMoney >= neededMoney) {
            clearNoMoneyGuard();
        } else if (neededMoney > 0L && isBlockedByNoMoneyGuard(neededMoney)) {
            lastResult = ControlledBuyClickResult.validationFailed(
                    "no_money_guard: minPrice=" + noMoneyGuardMinPrice + ", price=" + neededMoney,
                    dryRun
            );
            clearPendingCandidate();
            return false;
        }

        boolean clicked = sendBuyClick();
        if (!clicked) {
            lastResult = ControlledBuyClickResult.clickFailed("ctrl_lmb_click_not_sent", dryRun);
            clearPendingCandidate();
            return false;
        }

        startedAtMs = System.currentTimeMillis();
        lastPollAtMs = 0L;
        pending = true;
        lastResult = ControlledBuyClickResult.clickedPending(startedAtMs, beforeFingerprint, checkedSlots, clickedCandidate);
        return true;
    }

    /**
     * Called by chat detector when the server sends a buy-related message.
     */
    public ControlledBuyClickResult onBuyResult(BuyResult buyResult) {
        if (!pending || buyResult == null || !buyResult.isDetected()) {
            return null;
        }

        int currentFingerprint = beforeFingerprint;
        int currentChecked = checkedSlots;

        if (auctionView != null && auctionView.isAuctionOpen()) {
            List<AuctionSlot> currentSlots = safeReadSlots();
            currentFingerprint = AuctionFingerprint.compute(currentSlots);
            currentChecked = currentSlots.size();
        }

        return finish(ControlledBuyClickResult.fromBuyResult(
                startedAtMs,
                beforeFingerprint,
                currentFingerprint,
                currentChecked,
                clickedCandidate,
                buyResult
        ));
    }

    /**
     * @return completed result exactly once, otherwise null.
     */
    public ControlledBuyClickResult tick() {
        if (!pending) {
            return null;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - startedAtMs;

        lastResult = ControlledBuyClickResult.clickedPending(startedAtMs, beforeFingerprint, checkedSlots, clickedCandidate);

        if (elapsed < MIN_WAIT_AFTER_CLICK_MS) {
            return null;
        }

        if (auctionView == null || !auctionView.isAuctionOpen()) {
            return finish(ControlledBuyClickResult.screenChanged(startedAtMs, beforeFingerprint, checkedSlots, clickedCandidate));
        }

        if (now - lastPollAtMs < POLL_INTERVAL_MS) {
            return null;
        }
        lastPollAtMs = now;

        List<AuctionSlot> currentSlots = safeReadSlots();
        int currentFingerprint = AuctionFingerprint.compute(currentSlots);

        if (currentFingerprint != beforeFingerprint) {
            return finish(ControlledBuyClickResult.auctionChanged(startedAtMs, beforeFingerprint, currentFingerprint, currentSlots.size(), clickedCandidate));
        }

        if (elapsed >= TIMEOUT_MS) {
            return finish(ControlledBuyClickResult.timeout(startedAtMs, beforeFingerprint, currentFingerprint, currentSlots.size(), clickedCandidate));
        }

        return null;
    }

    public void cancel(String reason) {
        if (!pending) {
            return;
        }

        pending = false;
        clearPendingCandidate();
        lastResult = ControlledBuyClickResult.error(reason == null ? "cancelled" : reason);
    }

    public boolean isPending() {
        return pending;
    }

    public ControlledBuyClickResult getLastResult() {
        return lastResult;
    }

    public static void rememberNoMoneyGuard(long minPrice) {
        if (minPrice <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (noMoneyGuardMinPrice <= 0L || minPrice < noMoneyGuardMinPrice || now - noMoneyGuardAtMs > NO_MONEY_GUARD_TTL_MS) {
            noMoneyGuardMinPrice = minPrice;
        }
        noMoneyGuardAtMs = now;
    }

    public static void clearNoMoneyGuard() {
        noMoneyGuardMinPrice = 0L;
        noMoneyGuardAtMs = 0L;
    }

    private static boolean isBlockedByNoMoneyGuard(long price) {
        if (noMoneyGuardMinPrice <= 0L || noMoneyGuardAtMs <= 0L || price <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - noMoneyGuardAtMs > NO_MONEY_GUARD_TTL_MS) {
            clearNoMoneyGuard();
            return false;
        }
        return price >= noMoneyGuardMinPrice;
    }

    public long getTimeoutMs() {
        return TIMEOUT_MS;
    }

    private boolean sendBuyClick() {
        return clickedCandidate != null
                && clickedCandidate.getAuctionSlot() != null
                && auctionView != null
                && auctionView.ctrlLeftClickAuctionSlot(clickedCandidate.getAuctionSlot());
    }

    private ControlledBuyClickResult finish(ControlledBuyClickResult result) {
        pending = false;
        lastResult = result;
        clearPendingCandidate();
        return result;
    }

    private void clearPendingCandidate() {
        clickedCandidate = null;
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
