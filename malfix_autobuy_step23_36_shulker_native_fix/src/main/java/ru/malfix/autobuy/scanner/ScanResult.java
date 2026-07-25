package ru.malfix.autobuy.scanner;

public final class ScanResult {

    public enum Status {
        NOT_SCANNED,
        NO_SLOTS,
        NO_MATCH,
        FOUND,
        ERROR
    }

    private final Status status;
    private final int checkedSlots;
    private final ScanCandidate bestCandidate;
    private final String message;

    private ScanResult(Status status, int checkedSlots, ScanCandidate bestCandidate, String message) {
        this.status = status;
        this.checkedSlots = checkedSlots;
        this.bestCandidate = bestCandidate;
        this.message = message == null ? "" : message;
    }

    public static ScanResult notScanned() {
        return new ScanResult(Status.NOT_SCANNED, 0, null, "");
    }

    public static ScanResult noSlots() {
        return new ScanResult(Status.NO_SLOTS, 0, null, "no_slots");
    }

    public static ScanResult noMatch(int checkedSlots) {
        return new ScanResult(Status.NO_MATCH, checkedSlots, null, "no_match");
    }

    public static ScanResult found(int checkedSlots, ScanCandidate candidate) {
        return new ScanResult(Status.FOUND, checkedSlots, candidate, "found");
    }

    public static ScanResult error(String message) {
        return new ScanResult(Status.ERROR, 0, null, message);
    }

    public Status getStatus() {
        return status;
    }

    public int getCheckedSlots() {
        return checkedSlots;
    }

    public ScanCandidate getBestCandidate() {
        return bestCandidate;
    }

    public String getMessage() {
        return message;
    }

    public boolean hasBestCandidate() {
        return bestCandidate != null;
    }
}
