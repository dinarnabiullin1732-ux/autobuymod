package ru.malfix.autobuy.refresh;

import ru.malfix.autobuy.scanner.ScanCandidate;
import ru.malfix.autobuy.scanner.ScanResult;

public final class RefreshCycleResult {

    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_STARTED = "started";
    public static final String STATUS_SUCCESS_CHANGED = "success_changed";
    public static final String STATUS_TIMEOUT_NO_CHANGE = "timeout_no_change";
    public static final String STATUS_ERROR = "error";

    private final String status;
    private final boolean pending;
    private final String message;
    private final long startedAtMs;
    private final long finishedAtMs;
    private final long elapsedMs;
    private final int beforeFingerprint;
    private final int afterFingerprint;
    private final boolean changed;
    private final int checkedSlots;
    private final ScanResult scanResult;

    private RefreshCycleResult(
            String status,
            boolean pending,
            String message,
            long startedAtMs,
            long finishedAtMs,
            long elapsedMs,
            int beforeFingerprint,
            int afterFingerprint,
            boolean changed,
            int checkedSlots,
            ScanResult scanResult
    ) {
        this.status = status;
        this.pending = pending;
        this.message = message == null ? "" : message;
        this.startedAtMs = startedAtMs;
        this.finishedAtMs = finishedAtMs;
        this.elapsedMs = elapsedMs;
        this.beforeFingerprint = beforeFingerprint;
        this.afterFingerprint = afterFingerprint;
        this.changed = changed;
        this.checkedSlots = checkedSlots;
        this.scanResult = scanResult == null ? ScanResult.notScanned() : scanResult;
    }

    public static RefreshCycleResult idle() {
        return new RefreshCycleResult(STATUS_IDLE, false, "idle", 0L, 0L, 0L, 0, 0, false, 0, ScanResult.notScanned());
    }

    public static RefreshCycleResult pending(long startedAtMs, int beforeFingerprint) {
        long now = System.currentTimeMillis();
        return new RefreshCycleResult(STATUS_PENDING, true, "waiting_refresh_result", startedAtMs, 0L, now - startedAtMs, beforeFingerprint, beforeFingerprint, false, 0, ScanResult.notScanned());
    }

    public static RefreshCycleResult started(long startedAtMs, int beforeFingerprint, int checkedSlots) {
        return new RefreshCycleResult(STATUS_STARTED, true, "refresh_clicked", startedAtMs, 0L, 0L, beforeFingerprint, beforeFingerprint, false, checkedSlots, ScanResult.notScanned());
    }

    public static RefreshCycleResult success(long startedAtMs, int beforeFingerprint, int afterFingerprint, int checkedSlots, ScanResult scanResult) {
        long now = System.currentTimeMillis();
        return new RefreshCycleResult(STATUS_SUCCESS_CHANGED, false, "fingerprint_changed", startedAtMs, now, now - startedAtMs, beforeFingerprint, afterFingerprint, true, checkedSlots, scanResult);
    }

    public static RefreshCycleResult timeout(long startedAtMs, int beforeFingerprint, int afterFingerprint, int checkedSlots, ScanResult scanResult) {
        long now = System.currentTimeMillis();
        return new RefreshCycleResult(STATUS_TIMEOUT_NO_CHANGE, false, "fingerprint_not_changed_before_timeout", startedAtMs, now, now - startedAtMs, beforeFingerprint, afterFingerprint, false, checkedSlots, scanResult);
    }

    public static RefreshCycleResult error(String message) {
        return new RefreshCycleResult(STATUS_ERROR, false, message, 0L, System.currentTimeMillis(), 0L, 0, 0, false, 0, ScanResult.error(message));
    }

    public String getStatus() {
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

    public long getElapsedMs() {
        if (pending && startedAtMs > 0L) {
            return System.currentTimeMillis() - startedAtMs;
        }
        return elapsedMs;
    }

    public int getBeforeFingerprint() {
        return beforeFingerprint;
    }

    public int getAfterFingerprint() {
        return afterFingerprint;
    }

    public boolean isChanged() {
        return changed;
    }

    public int getCheckedSlots() {
        return checkedSlots;
    }

    public ScanResult getScanResult() {
        return scanResult;
    }

    public ScanCandidate getBestCandidate() {
        return scanResult == null ? null : scanResult.getBestCandidate();
    }

    public String compact() {
        return "status=" + status
                + ", pending=" + pending
                + ", elapsedMs=" + getElapsedMs()
                + ", beforeFp=" + beforeFingerprint
                + ", afterFp=" + afterFingerprint
                + ", changed=" + changed
                + ", checked=" + checkedSlots
                + ", msg=" + message;
    }
}
