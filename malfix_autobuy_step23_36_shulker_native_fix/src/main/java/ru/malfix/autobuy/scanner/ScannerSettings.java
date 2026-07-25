package ru.malfix.autobuy.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScannerSettings {

    private final ScanMode scanMode;
    private final boolean requireMaxPrice;
    private final boolean allowUnlimitedPrice;
    private final List<String> blacklistKeywords;

    public ScannerSettings(
            ScanMode scanMode,
            boolean requireMaxPrice,
            boolean allowUnlimitedPrice,
            List<String> blacklistKeywords
    ) {
        this.scanMode = scanMode == null ? ScanMode.ALL45 : scanMode;
        this.requireMaxPrice = requireMaxPrice;
        this.allowUnlimitedPrice = allowUnlimitedPrice;
        this.blacklistKeywords = normalizeKeywords(blacklistKeywords);
    }

    public static ScannerSettings defaults() {
        return new ScannerSettings(ScanMode.ALL45, true, false, Collections.<String>emptyList());
    }

    public ScanMode getScanMode() {
        return scanMode;
    }

    public boolean isRequireMaxPrice() {
        return requireMaxPrice;
    }

    public boolean isAllowUnlimitedPrice() {
        return allowUnlimitedPrice;
    }

    public List<String> getBlacklistKeywords() {
        return blacklistKeywords;
    }

    public String compact() {
        return "scanMode=" + scanMode
                + ", requireMaxPrice=" + requireMaxPrice
                + ", allowUnlimitedPrice=" + allowUnlimitedPrice
                + ", blacklist=" + blacklistKeywords.size();
    }

    private List<String> normalizeKeywords(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<String>();

        for (String value : source) {
            if (value == null) {
                continue;
            }

            String normalized = value.trim().toLowerCase();
            if (!normalized.isEmpty() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }

        return Collections.unmodifiableList(result);
    }
}
