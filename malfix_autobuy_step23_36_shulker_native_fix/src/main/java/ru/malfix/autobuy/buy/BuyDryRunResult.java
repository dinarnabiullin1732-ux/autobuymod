package ru.malfix.autobuy.buy;

import ru.malfix.autobuy.scanner.ScanCandidate;
import ru.malfix.autobuy.scanner.ScanResult;

public final class BuyDryRunResult {

    public enum Status {
        IDLE,
        READY_TO_BUY,
        AUCTION_NOT_OPEN,
        NO_MATCH,
        SLOT_CHANGED,
        ERROR
    }

    private final Status status;
    private final String message;
    private final long createdAtMs;
    private final int beforeFingerprint;
    private final int afterFingerprint;
    private final int checkedSlots;
    private final ScanResult scanResult;
    private final ScanCandidate candidate;

    private BuyDryRunResult(
            Status status,
            String message,
            long createdAtMs,
            int beforeFingerprint,
            int afterFingerprint,
            int checkedSlots,
            ScanResult scanResult,
            ScanCandidate candidate
    ) {
        this.status = status == null ? Status.ERROR : status;
        this.message = message == null ? "" : message;
        this.createdAtMs = createdAtMs;
        this.beforeFingerprint = beforeFingerprint;
        this.afterFingerprint = afterFingerprint;
        this.checkedSlots = checkedSlots;
        this.scanResult = scanResult;
        this.candidate = candidate;
    }

    public static BuyDryRunResult idle() {
        return new BuyDryRunResult(Status.IDLE, "idle", 0L, 0, 0, 0, null, null);
    }

    public static BuyDryRunResult auctionNotOpen() {
        return new BuyDryRunResult(Status.AUCTION_NOT_OPEN, "auction_not_open", System.currentTimeMillis(), 0, 0, 0, null, null);
    }

    public static BuyDryRunResult noMatch(int fingerprint, int checkedSlots, ScanResult scanResult) {
        return new BuyDryRunResult(Status.NO_MATCH, "no_match", System.currentTimeMillis(), fingerprint, fingerprint, checkedSlots, scanResult, null);
    }

    public static BuyDryRunResult slotChanged(int beforeFingerprint, int afterFingerprint, int checkedSlots, ScanResult scanResult, ScanCandidate candidate, String message) {
        return new BuyDryRunResult(Status.SLOT_CHANGED, message, System.currentTimeMillis(), beforeFingerprint, afterFingerprint, checkedSlots, scanResult, candidate);
    }

    public static BuyDryRunResult ready(int fingerprint, int checkedSlots, ScanResult scanResult, ScanCandidate candidate) {
        return new BuyDryRunResult(Status.READY_TO_BUY, "ready_to_buy", System.currentTimeMillis(), fingerprint, fingerprint, checkedSlots, scanResult, candidate);
    }

    public static BuyDryRunResult error(String message) {
        return new BuyDryRunResult(Status.ERROR, message, System.currentTimeMillis(), 0, 0, 0, null, null);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
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

    public ScanResult getScanResult() {
        return scanResult;
    }

    public ScanCandidate getCandidate() {
        return candidate;
    }

    public boolean isReady() {
        return status == Status.READY_TO_BUY && candidate != null;
    }

    public boolean isChanged() {
        return beforeFingerprint != afterFingerprint;
    }

    public String compact() {
        return "status=" + status
                + ", message=" + message
                + ", beforeFp=" + beforeFingerprint
                + ", afterFp=" + afterFingerprint
                + ", changed=" + isChanged()
                + ", checked=" + checkedSlots;
    }
}
