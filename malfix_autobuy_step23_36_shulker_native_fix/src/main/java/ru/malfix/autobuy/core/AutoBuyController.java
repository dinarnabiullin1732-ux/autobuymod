package ru.malfix.autobuy.core;

import ru.malfix.autobuy.auction.AuctionFingerprint;
import ru.malfix.autobuy.auction.AuctionView;
import ru.malfix.autobuy.scanner.AuctionScanner;
import ru.malfix.autobuy.scanner.ScanResult;
import ru.malfix.autobuy.config.MalfixTimings;

public final class AutoBuyController {

    private final AutoBuyContext ctx = new AutoBuyContext();

    private final AuctionView auctionView;
    private final AuctionScanner scanner;

    private static final long AUCTION_OPEN_TIMEOUT_MS = MalfixTimings.AUTOSELL_OPEN_MS;
    private static final long REFRESH_TIMEOUT_MS = MalfixTimings.SMART_REOPEN_REFRESH_FAIL_MS;
    private static final long BUY_RESULT_TIMEOUT_MS = MalfixTimings.SMART_REOPEN_MIN_SCREEN_AGE_MS;
    private static final long RECOVERY_COOLDOWN_MS = MalfixTimings.SMART_REOPEN_MIN_SCREEN_AGE_MS;

    public AutoBuyController(AuctionView auctionView, AuctionScanner scanner) {
        this.auctionView = auctionView;
        this.scanner = scanner;
    }

    public void enable() {
        ctx.enabled = true;
        ctx.failedRefreshes = 0;
        ctx.failedBuyAttempts = 0;
        ctx.switchState(AutoBuyState.IDLE, "enabled");
    }

    public void disable() {
        ctx.enabled = false;
        ctx.switchState(AutoBuyState.DISABLED, "disabled");
    }

    public void pause(String reason) {
        if (!ctx.enabled) {
            return;
        }

        ctx.switchState(AutoBuyState.PAUSED, reason);
    }

    public void resume() {
        if (!ctx.enabled) {
            return;
        }

        ctx.switchState(AutoBuyState.IDLE, "resumed");
    }

    public void tick() {
        ctx.lastTickAt = System.currentTimeMillis();

        if (!ctx.enabled) {
            ctx.switchState(AutoBuyState.DISABLED, "not_enabled");
            return;
        }

        switch (ctx.state) {
            case DISABLED:
                ctx.switchState(AutoBuyState.IDLE, "enabled_from_disabled");
                break;

            case IDLE:
                ctx.switchState(AutoBuyState.OPEN_AUCTION, "need_open_auction");
                break;

            case OPEN_AUCTION:
                handleOpenAuction();
                break;

            case WAIT_AUCTION:
                handleWaitAuction();
                break;

            case REFRESH_AUCTION:
                handleRefreshAuction();
                break;

            case WAIT_REFRESH:
                handleWaitRefresh();
                break;

            case SCAN:
                handleScan();
                break;

            case TRY_BUY:
                handleTryBuy();
                break;

            case WAIT_BUY_RESULT:
                handleWaitBuyResult();
                break;

            case PAUSED:
                break;

            case ERROR_RECOVERY:
                handleRecovery();
                break;
        }
    }

    private void handleOpenAuction() {
        if (auctionView == null) {
            ctx.switchState(AutoBuyState.ERROR_RECOVERY, "auction_view_missing");
            return;
        }

        ctx.lastAuctionOpenAt = System.currentTimeMillis();

        if (auctionView.isAuctionOpen()) {
            ctx.switchState(AutoBuyState.REFRESH_AUCTION, "auction_already_open");
        } else {
            auctionView.requestOpenAuction();
            ctx.switchState(AutoBuyState.WAIT_AUCTION, "open_auction_requested");
        }
    }

    private void handleWaitAuction() {
        if (auctionView != null && auctionView.isAuctionOpen()) {
            ctx.switchState(AutoBuyState.REFRESH_AUCTION, "auction_opened");
            return;
        }

        if (ctx.stateAgeMs() > AUCTION_OPEN_TIMEOUT_MS) {
            ctx.switchState(AutoBuyState.ERROR_RECOVERY, "auction_open_timeout");
        }
    }

    private void handleRefreshAuction() {
        if (auctionView == null || !auctionView.isAuctionOpen()) {
            ctx.switchState(AutoBuyState.OPEN_AUCTION, "auction_not_open_before_refresh");
            return;
        }

        ctx.lastFingerprint = AuctionFingerprint.compute(auctionView.readAuctionSlots());
        auctionView.clickRefresh();
        ctx.lastRefreshAt = System.currentTimeMillis();
        ctx.switchState(AutoBuyState.WAIT_REFRESH, "refresh_clicked");
    }

    private void handleWaitRefresh() {
        if (auctionView == null || !auctionView.isAuctionOpen()) {
            ctx.switchState(AutoBuyState.OPEN_AUCTION, "auction_closed_during_wait_refresh");
            return;
        }

        ctx.currentFingerprint = AuctionFingerprint.compute(auctionView.readAuctionSlots());
        ctx.lastFingerprintChanged = ctx.currentFingerprint != ctx.lastFingerprint;

        if (ctx.lastFingerprintChanged) {
            ctx.failedRefreshes = 0;
            ctx.switchState(AutoBuyState.SCAN, "slots_changed");
            return;
        }

        if (ctx.stateAgeMs() > REFRESH_TIMEOUT_MS) {
            ctx.failedRefreshes++;

            if (ctx.failedRefreshes >= 2) {
                ctx.switchState(AutoBuyState.ERROR_RECOVERY, "refresh_failed_twice");
            } else {
                ctx.switchState(AutoBuyState.REFRESH_AUCTION, "refresh_retry");
            }
        }
    }

    private void handleScan() {
        ctx.lastScanAt = System.currentTimeMillis();

        if (scanner == null || auctionView == null) {
            ctx.lastScanResult = ScanResult.error("scanner_or_view_missing");
            ctx.switchState(AutoBuyState.REFRESH_AUCTION, "scanner_or_view_missing");
            return;
        }

        ctx.lastScanResult = scanner.scan(auctionView.readAuctionSlots());

        if (ctx.lastScanResult.hasBestCandidate()) {
            ctx.switchState(AutoBuyState.TRY_BUY, "found_item");
        } else {
            ctx.switchState(AutoBuyState.REFRESH_AUCTION, "nothing_found");
        }
    }

    private void handleTryBuy() {
        ctx.lastBuyAttemptAt = System.currentTimeMillis();

        // BuyExecutor will be connected in the next step.
        ctx.switchState(AutoBuyState.WAIT_BUY_RESULT, "buy_executor_missing");
    }

    private void handleWaitBuyResult() {
        if (ctx.stateAgeMs() > BUY_RESULT_TIMEOUT_MS) {
            ctx.failedBuyAttempts++;
            ctx.switchState(AutoBuyState.REFRESH_AUCTION, "buy_result_timeout");
        }
    }

    private void handleRecovery() {
        long now = System.currentTimeMillis();

        if (now - ctx.lastRecoveryAt < RECOVERY_COOLDOWN_MS) {
            return;
        }

        ctx.lastRecoveryAt = now;

        if (auctionView != null) {
            auctionView.closeCurrentScreen();
        }

        ctx.switchState(AutoBuyState.OPEN_AUCTION, "recovery_reopen_auction");
    }

    public String debug() {
        return new DebugSnapshot(ctx).build();
    }

    public AutoBuyContext context() {
        return ctx;
    }
}
