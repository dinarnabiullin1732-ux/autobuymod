package ru.malfix.autobuy.core;

import ru.malfix.autobuy.scanner.ScanCandidate;
import ru.malfix.autobuy.scanner.ScanResult;

public final class DebugSnapshot {

    private final AutoBuyContext ctx;

    public DebugSnapshot(AutoBuyContext ctx) {
        this.ctx = ctx;
    }

    public String build() {
        long now = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();

        sb.append("enabled=").append(ctx.enabled).append('\n');
        sb.append("state=").append(ctx.state).append('\n');
        sb.append("reason=").append(ctx.reason).append('\n');
        sb.append("stateAgeMs=").append(ctx.stateAgeMs()).append('\n');
        sb.append("failedRefreshes=").append(ctx.failedRefreshes).append('\n');
        sb.append("failedBuyAttempts=").append(ctx.failedBuyAttempts).append('\n');
        sb.append("lastAuctionOpenAgo=").append(ago(now, ctx.lastAuctionOpenAt)).append('\n');
        sb.append("lastRefreshAgo=").append(ago(now, ctx.lastRefreshAt)).append('\n');
        sb.append("lastScanAgo=").append(ago(now, ctx.lastScanAt)).append('\n');
        sb.append("lastBuyAttemptAgo=").append(ago(now, ctx.lastBuyAttemptAt)).append('\n');
        sb.append("lastRecoveryAgo=").append(ago(now, ctx.lastRecoveryAt)).append('\n');
        sb.append("lastFingerprint=").append(ctx.lastFingerprint).append('\n');
        sb.append("currentFingerprint=").append(ctx.currentFingerprint).append('\n');
        sb.append("lastFingerprintChanged=").append(ctx.lastFingerprintChanged).append('\n');

        appendScan(sb, ctx.lastScanResult);

        return sb.toString();
    }

    private void appendScan(StringBuilder sb, ScanResult result) {
        if (result == null) {
            sb.append("lastScanResult=null\n");
            return;
        }

        sb.append("lastScanResult=").append(result.getStatus()).append('\n');
        sb.append("scanCheckedSlots=").append(result.getCheckedSlots()).append('\n');

        ScanCandidate best = result.getBestCandidate();
        if (best == null) {
            sb.append("bestSlot=none\n");
            return;
        }

        sb.append("bestSlot=").append(best.getAuctionSlot().getAuctionIndex()).append('\n');
        sb.append("bestContainerSlot=").append(best.getAuctionSlot().getContainerSlotId()).append('\n');
        sb.append("bestItemLabel=").append(best.getAuctionSlot().getDisplayName()).append('\n');
        sb.append("bestTotalPrice=").append(best.getPrice().getTotalPrice()).append('\n');
        sb.append("bestUnitPrice=").append(best.getPrice().getUnitPrice()).append('\n');
        sb.append("bestTarget=").append(best.getTarget().getLabel()).append('\n');
    }

    private String ago(long now, long time) {
        if (time <= 0L) {
            return "never";
        }
        return (now - time) + "ms";
    }
}
