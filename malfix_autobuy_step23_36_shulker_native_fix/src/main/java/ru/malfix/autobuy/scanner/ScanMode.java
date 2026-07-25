package ru.malfix.autobuy.scanner;

public enum ScanMode {
    TOP9(9),
    TOP18(18),
    TOP27(27),
    ALL45(45);

    private final int maxSlots;

    ScanMode(int maxSlots) {
        this.maxSlots = maxSlots;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public static ScanMode fromString(String value) {
        if (value == null) {
            return ALL45;
        }

        String normalized = value.trim().toUpperCase();

        if ("9".equals(normalized) || "TOP_9".equals(normalized)) {
            return TOP9;
        }

        if ("18".equals(normalized) || "TOP_18".equals(normalized)) {
            return TOP18;
        }

        if ("27".equals(normalized) || "TOP_27".equals(normalized)) {
            return TOP27;
        }

        if ("45".equals(normalized) || "ALL".equals(normalized) || "ALL_45".equals(normalized)) {
            return ALL45;
        }

        try {
            return ScanMode.valueOf(normalized);
        } catch (Throwable ignored) {
            return ALL45;
        }
    }
}
