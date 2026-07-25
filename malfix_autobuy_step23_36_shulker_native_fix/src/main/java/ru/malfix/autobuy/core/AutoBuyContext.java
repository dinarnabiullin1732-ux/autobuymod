package ru.malfix.autobuy.core;

import ru.malfix.autobuy.scanner.ScanResult;

public final class AutoBuyContext {

    public boolean enabled = false;

    public AutoBuyState state = AutoBuyState.DISABLED;

    public long stateStartedAt = System.currentTimeMillis();
    public long lastTickAt = 0L;

    public long lastAuctionOpenAt = 0L;
    public long lastRefreshAt = 0L;
    public long lastScanAt = 0L;
    public long lastBuyAttemptAt = 0L;
    public long lastRecoveryAt = 0L;

    public int failedRefreshes = 0;
    public int failedBuyAttempts = 0;

    public String reason = "init";

    public int lastFingerprint = 0;
    public int currentFingerprint = 0;
    public boolean lastFingerprintChanged = false;

    public ScanResult lastScanResult = ScanResult.notScanned();

    public void switchState(AutoBuyState newState, String reason) {
        if (this.state == newState) {
            this.reason = reason;
            return;
        }

        this.state = newState;
        this.reason = reason;
        this.stateStartedAt = System.currentTimeMillis();
    }

    public long stateAgeMs() {
        return System.currentTimeMillis() - stateStartedAt;
    }
}
