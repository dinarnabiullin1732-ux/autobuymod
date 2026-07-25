package ru.malfix.autobuy.config;

import org.lwjgl.glfw.GLFW;
import ru.malfix.autobuy.scanner.ScanMode;
import ru.malfix.autobuy.scanner.ScannerSettings;
import ru.malfix.autobuy.scanner.TargetItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AutoBuyConfig {

    private int defaultLoopCycles;
    private int defaultLoopBuys;
    private long loopDelayMs;
    private List<TargetConfig> targets;

    private ScanMode scanMode;
    private boolean requireMaxPrice;
    private boolean allowUnlimitedPrice;
    private long refreshTimeoutMs;
    private int maxRefreshFailStreak;
    private List<String> blacklistKeywords;

    private int sellerMarkupPercent;

    private int parserBuyPercent;
    private int parserSellPercent;
    private long parserOpenWaitMs;
    private long parserBetweenItemsMs;

    private boolean antiAfkEnabled;
    private long antiAfkIntervalMs;
    private String antiAfkAnarchy;

    private int keyDebug;
    private int keyFingerprint;
    private int keyScan;
    private int keyObserver;
    private int keyRefresh;
    private int keyBuy;
    private int keyOneCycle;
    private int keyLimitedLoop;
    private int keyGui;
    private int keyParser;
    private int keyFullAuto;
    private int keySellOnly;

    public AutoBuyConfig(int defaultLoopCycles, int defaultLoopBuys, long loopDelayMs, List<TargetConfig> targets) {
        this(
                defaultLoopCycles,
                defaultLoopBuys,
                loopDelayMs,
                targets,
                GLFW.GLFW_KEY_D,
                GLFW.GLFW_KEY_F,
                GLFW.GLFW_KEY_S,
                GLFW.GLFW_KEY_O,
                GLFW.GLFW_KEY_R,
                GLFW.GLFW_KEY_B,
                GLFW.GLFW_KEY_A,
                GLFW.GLFW_KEY_L,
                GLFW.GLFW_KEY_G,
                GLFW.GLFW_KEY_P,
                GLFW.GLFW_KEY_R,
                ScanMode.ALL45,
                true,
                false,
                MalfixTimings.SMART_REOPEN_REFRESH_FAIL_MS,
                MalfixTimings.DEFAULT_MAX_REFRESH_FAIL_STREAK,
                Collections.<String>emptyList(),
                10
        );
    }

    public AutoBuyConfig(
            int defaultLoopCycles,
            int defaultLoopBuys,
            long loopDelayMs,
            List<TargetConfig> targets,
            int keyDebug,
            int keyFingerprint,
            int keyScan,
            int keyObserver,
            int keyRefresh,
            int keyBuy,
            int keyOneCycle,
            int keyLimitedLoop,
            int keyGui,
            int keyParser,
            int keyFullAuto
    ) {
        this(
                defaultLoopCycles,
                defaultLoopBuys,
                loopDelayMs,
                targets,
                keyDebug,
                keyFingerprint,
                keyScan,
                keyObserver,
                keyRefresh,
                keyBuy,
                keyOneCycle,
                keyLimitedLoop,
                keyGui,
                keyParser,
                keyFullAuto,
                ScanMode.ALL45,
                true,
                false,
                MalfixTimings.SMART_REOPEN_REFRESH_FAIL_MS,
                MalfixTimings.DEFAULT_MAX_REFRESH_FAIL_STREAK,
                Collections.<String>emptyList(),
                10
        );
    }

    public AutoBuyConfig(
            int defaultLoopCycles,
            int defaultLoopBuys,
            long loopDelayMs,
            List<TargetConfig> targets,
            int keyDebug,
            int keyFingerprint,
            int keyScan,
            int keyObserver,
            int keyRefresh,
            int keyBuy,
            int keyOneCycle,
            int keyLimitedLoop,
            int keyGui,
            int keyParser,
            int keyFullAuto,
            ScanMode scanMode,
            boolean requireMaxPrice,
            boolean allowUnlimitedPrice,
            long refreshTimeoutMs,
            int maxRefreshFailStreak,
            List<String> blacklistKeywords,
            int sellerMarkupPercent
    ) {
        this.defaultLoopCycles = clamp(defaultLoopCycles, 1, 100, 10);
        this.defaultLoopBuys = clamp(defaultLoopBuys, 1, 20, 3);
        this.loopDelayMs = clampLong(loopDelayMs, MalfixTimings.AB_UPDATE_MS, 30000L, MalfixTimings.AB_UPDATE_MS);
        this.targets = targets == null ? new ArrayList<TargetConfig>() : new ArrayList<TargetConfig>(targets);
        if (this.targets.isEmpty()) {
            this.targets.addAll(defaultTargets());
        }

        this.keyDebug = sanitizeKey(keyDebug, GLFW.GLFW_KEY_D);
        this.keyFingerprint = sanitizeKey(keyFingerprint, GLFW.GLFW_KEY_F);
        this.keyScan = sanitizeKey(keyScan, GLFW.GLFW_KEY_S);
        this.keyObserver = sanitizeKey(keyObserver, GLFW.GLFW_KEY_O);
        this.keyRefresh = sanitizeKey(keyRefresh, GLFW.GLFW_KEY_R);
        this.keyBuy = sanitizeKey(keyBuy, GLFW.GLFW_KEY_B);
        this.keyOneCycle = sanitizeKey(keyOneCycle, GLFW.GLFW_KEY_A);
        this.keyLimitedLoop = sanitizeKey(keyLimitedLoop, GLFW.GLFW_KEY_L);
        this.keyGui = sanitizeKey(keyGui, GLFW.GLFW_KEY_G);
        this.keyParser = sanitizeKey(keyParser, GLFW.GLFW_KEY_P);
        this.keyFullAuto = sanitizeKey(keyFullAuto, GLFW.GLFW_KEY_R);
        this.keySellOnly = sanitizeKey(GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_V);

        this.scanMode = scanMode == null ? ScanMode.ALL45 : scanMode;
        this.requireMaxPrice = requireMaxPrice;
        this.allowUnlimitedPrice = allowUnlimitedPrice;
        this.refreshTimeoutMs = clampLong(refreshTimeoutMs, 150L, 5000L, MalfixTimings.SMART_REOPEN_REFRESH_FAIL_MS);
        this.maxRefreshFailStreak = clamp(maxRefreshFailStreak, 1, 20, MalfixTimings.DEFAULT_MAX_REFRESH_FAIL_STREAK);
        this.blacklistKeywords = normalizeBlacklist(blacklistKeywords);
        this.sellerMarkupPercent = clampMarkup(sellerMarkupPercent);

        this.parserBuyPercent = clampPercent(MalfixTimings.PARSER_BUY_PERCENT);
        this.parserSellPercent = clampPercent(MalfixTimings.PARSER_SELL_PERCENT);
        this.parserOpenWaitMs = clampLong(MalfixTimings.PARSER_OPEN_WAIT_MS, 100L, 5000L, MalfixTimings.PARSER_OPEN_WAIT_MS);
        this.parserBetweenItemsMs = clampLong(MalfixTimings.PARSER_BETWEEN_ITEMS_MS, 50L, 5000L, MalfixTimings.PARSER_BETWEEN_ITEMS_MS);

        this.antiAfkEnabled = true;
        this.antiAfkIntervalMs = clampLong(MalfixTimings.ANTI_AFK_INTERVAL_MS, 60_000L, 1_800_000L, MalfixTimings.ANTI_AFK_INTERVAL_MS);
        this.antiAfkAnarchy = "";
    }

    public static AutoBuyConfig defaults() {
        return new AutoBuyConfig(10, 3, MalfixTimings.AB_UPDATE_MS, defaultTargets());
    }

    public static List<TargetConfig> defaultTargets() {
        return ScriptItemCatalog.createScriptTargets();
    }

    public List<TargetItem> toTargetItems() {
        List<TargetItem> result = new ArrayList<TargetItem>();
        for (TargetConfig target : targets) {
            if (target != null) {
                result.add(target.toTargetItem());
            }
        }
        return result;
    }

    public ScannerSettings toScannerSettings() {
        return new ScannerSettings(scanMode, requireMaxPrice, allowUnlimitedPrice, blacklistKeywords);
    }

    public int getDefaultLoopCycles() {
        return defaultLoopCycles;
    }

    public void setDefaultLoopCycles(int defaultLoopCycles) {
        this.defaultLoopCycles = clamp(defaultLoopCycles, 1, 100, 10);
    }

    public int getDefaultLoopBuys() {
        return defaultLoopBuys;
    }

    public void setDefaultLoopBuys(int defaultLoopBuys) {
        this.defaultLoopBuys = clamp(defaultLoopBuys, 1, 20, 3);
    }

    public long getLoopDelayMs() {
        return loopDelayMs;
    }

    public void setLoopDelayMs(long loopDelayMs) {
        this.loopDelayMs = clampLong(loopDelayMs, MalfixTimings.AB_UPDATE_MS, 30000L, MalfixTimings.AB_UPDATE_MS);
    }

    public List<TargetConfig> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    public int targetCount() {
        return targets.size();
    }

    public TargetConfig findTarget(String label) {
        if (label == null) {
            return null;
        }

        for (TargetConfig target : targets) {
            if (target != null && target.matchesLabel(label)) {
                return target;
            }
        }

        return null;
    }

    public boolean addTarget(TargetConfig target) {
        if (target == null || target.getLabel().trim().isEmpty()) {
            return false;
        }

        if (findTarget(target.getLabel()) != null) {
            return false;
        }

        targets.add(target);
        return true;
    }

    public boolean removeTarget(String label) {
        TargetConfig target = findTarget(label);
        if (target == null) {
            return false;
        }

        targets.remove(target);
        return true;
    }

    public void resetTargets() {
        targets.clear();
        targets.addAll(defaultTargets());
    }

    public void clearTargets() {
        targets.clear();
    }

    public ScanMode getScanMode() {
        return scanMode;
    }

    public void setScanMode(ScanMode scanMode) {
        this.scanMode = scanMode == null ? ScanMode.ALL45 : scanMode;
    }

    public void setScanMode(String scanMode) {
        this.scanMode = ScanMode.fromString(scanMode);
    }

    public boolean isRequireMaxPrice() {
        return requireMaxPrice;
    }

    public void setRequireMaxPrice(boolean requireMaxPrice) {
        this.requireMaxPrice = requireMaxPrice;
    }

    public boolean isAllowUnlimitedPrice() {
        return allowUnlimitedPrice;
    }

    public void setAllowUnlimitedPrice(boolean allowUnlimitedPrice) {
        this.allowUnlimitedPrice = allowUnlimitedPrice;
    }

    public long getRefreshTimeoutMs() {
        return refreshTimeoutMs;
    }

    public void setRefreshTimeoutMs(long refreshTimeoutMs) {
        this.refreshTimeoutMs = clampLong(refreshTimeoutMs, 150L, 5000L, MalfixTimings.SMART_REOPEN_REFRESH_FAIL_MS);
    }

    public int getMaxRefreshFailStreak() {
        return maxRefreshFailStreak;
    }

    public void setMaxRefreshFailStreak(int maxRefreshFailStreak) {
        this.maxRefreshFailStreak = clamp(maxRefreshFailStreak, 1, 20, MalfixTimings.DEFAULT_MAX_REFRESH_FAIL_STREAK);
    }

    public int getSellerMarkupPercent() {
        return sellerMarkupPercent;
    }

    public void setSellerMarkupPercent(int sellerMarkupPercent) {
        this.sellerMarkupPercent = clampMarkup(sellerMarkupPercent);
    }

    public List<String> getBlacklistKeywords() {
        return Collections.unmodifiableList(blacklistKeywords);
    }

    public int getParserBuyPercent() {
        return parserBuyPercent;
    }

    public void setParserBuyPercent(int parserBuyPercent) {
        this.parserBuyPercent = clampPercent(parserBuyPercent);
    }

    public int getParserSellPercent() {
        return parserSellPercent;
    }

    public void setParserSellPercent(int parserSellPercent) {
        this.parserSellPercent = clampPercent(parserSellPercent);
    }

    public long getParserOpenWaitMs() {
        return parserOpenWaitMs;
    }

    public void setParserOpenWaitMs(long parserOpenWaitMs) {
        this.parserOpenWaitMs = clampLong(parserOpenWaitMs, 100L, 5000L, MalfixTimings.PARSER_OPEN_WAIT_MS);
    }

    public long getParserBetweenItemsMs() {
        return parserBetweenItemsMs;
    }

    public void setParserBetweenItemsMs(long parserBetweenItemsMs) {
        this.parserBetweenItemsMs = clampLong(parserBetweenItemsMs, 50L, 5000L, MalfixTimings.PARSER_BETWEEN_ITEMS_MS);
    }

    public boolean isAntiAfkEnabled() {
        return antiAfkEnabled;
    }

    public void setAntiAfkEnabled(boolean antiAfkEnabled) {
        this.antiAfkEnabled = antiAfkEnabled;
    }

    public long getAntiAfkIntervalMs() {
        return antiAfkIntervalMs;
    }

    public void setAntiAfkIntervalMs(long antiAfkIntervalMs) {
        this.antiAfkIntervalMs = clampLong(antiAfkIntervalMs, 60_000L, 1_800_000L, MalfixTimings.ANTI_AFK_INTERVAL_MS);
    }

    public String getAntiAfkAnarchy() {
        return antiAfkAnarchy;
    }

    public void setAntiAfkAnarchy(String antiAfkAnarchy) {
        this.antiAfkAnarchy = normalizeAnarchy(antiAfkAnarchy);
    }

    public boolean addBlacklistKeyword(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (normalized.isEmpty() || blacklistKeywords.contains(normalized)) {
            return false;
        }

        blacklistKeywords.add(normalized);
        return true;
    }

    public boolean removeBlacklistKeyword(String keyword) {
        String normalized = normalizeKeyword(keyword);
        return blacklistKeywords.remove(normalized);
    }

    public void clearBlacklistKeywords() {
        blacklistKeywords.clear();
    }

    public int getKeyDebug() {
        return keyDebug;
    }

    public int getKeyFingerprint() {
        return keyFingerprint;
    }

    public int getKeyScan() {
        return keyScan;
    }

    public int getKeyObserver() {
        return keyObserver;
    }

    public int getKeyRefresh() {
        return keyRefresh;
    }

    public int getKeyBuy() {
        return keyBuy;
    }

    public int getKeyOneCycle() {
        return keyOneCycle;
    }

    public int getKeyLimitedLoop() {
        return keyLimitedLoop;
    }

    public int getKeyGui() {
        return keyGui;
    }

    public int getKeyParser() {
        return keyParser;
    }

    public int getKeyFullAuto() {
        return keyFullAuto;
    }

    public int getKeySellOnly() {
        return keySellOnly;
    }

    public void setKeyDebug(int keyDebug) {
        this.keyDebug = sanitizeKey(keyDebug, GLFW.GLFW_KEY_D);
    }

    public void setKeyFingerprint(int keyFingerprint) {
        this.keyFingerprint = sanitizeKey(keyFingerprint, GLFW.GLFW_KEY_F);
    }

    public void setKeyScan(int keyScan) {
        this.keyScan = sanitizeKey(keyScan, GLFW.GLFW_KEY_S);
    }

    public void setKeyObserver(int keyObserver) {
        this.keyObserver = sanitizeKey(keyObserver, GLFW.GLFW_KEY_O);
    }

    public void setKeyRefresh(int keyRefresh) {
        this.keyRefresh = sanitizeKey(keyRefresh, GLFW.GLFW_KEY_R);
    }

    public void setKeyBuy(int keyBuy) {
        this.keyBuy = sanitizeKey(keyBuy, GLFW.GLFW_KEY_B);
    }

    public void setKeyOneCycle(int keyOneCycle) {
        this.keyOneCycle = sanitizeKey(keyOneCycle, GLFW.GLFW_KEY_A);
    }

    public void setKeyLimitedLoop(int keyLimitedLoop) {
        this.keyLimitedLoop = sanitizeKey(keyLimitedLoop, GLFW.GLFW_KEY_L);
    }

    public void setKeyGui(int keyGui) {
        this.keyGui = sanitizeKey(keyGui, GLFW.GLFW_KEY_G);
    }

    public void setKeyParser(int keyParser) {
        this.keyParser = sanitizeKey(keyParser, GLFW.GLFW_KEY_P);
    }

    public void setKeyFullAuto(int keyFullAuto) {
        this.keyFullAuto = sanitizeKey(keyFullAuto, GLFW.GLFW_KEY_R);
    }

    public void setKeySellOnly(int keySellOnly) {
        this.keySellOnly = sanitizeKey(keySellOnly, GLFW.GLFW_KEY_V);
    }

    public int getKeyCode(String action) {
        if ("debug".equals(action)) {
            return keyDebug;
        }
        if ("fingerprint".equals(action)) {
            return keyFingerprint;
        }
        if ("scan".equals(action)) {
            return keyScan;
        }
        if ("observer".equals(action)) {
            return keyObserver;
        }
        if ("refresh".equals(action)) {
            return keyRefresh;
        }
        if ("buy".equals(action)) {
            return keyBuy;
        }
        if ("oneCycle".equals(action)) {
            return keyOneCycle;
        }
        if ("limitedLoop".equals(action)) {
            return keyLimitedLoop;
        }
        if ("gui".equals(action)) {
            return keyGui;
        }
        if ("parser".equals(action)) {
            return keyParser;
        }
        if ("fullAuto".equals(action)) {
            return keyFullAuto;
        }
        if ("sellOnly".equals(action)) {
            return keySellOnly;
        }
        return -1;
    }

    public void setKeyCode(String action, int keyCode) {
        if ("debug".equals(action)) {
            setKeyDebug(keyCode);
        } else if ("fingerprint".equals(action)) {
            setKeyFingerprint(keyCode);
        } else if ("scan".equals(action)) {
            setKeyScan(keyCode);
        } else if ("observer".equals(action)) {
            setKeyObserver(keyCode);
        } else if ("refresh".equals(action)) {
            setKeyRefresh(keyCode);
        } else if ("buy".equals(action)) {
            setKeyBuy(keyCode);
        } else if ("oneCycle".equals(action)) {
            setKeyOneCycle(keyCode);
        } else if ("limitedLoop".equals(action)) {
            setKeyLimitedLoop(keyCode);
        } else if ("gui".equals(action)) {
            setKeyGui(keyCode);
        } else if ("parser".equals(action)) {
            setKeyParser(keyCode);
        } else if ("fullAuto".equals(action)) {
            setKeyFullAuto(keyCode);
        } else if ("sellOnly".equals(action)) {
            setKeySellOnly(keyCode);
        }
    }

    public String compact() {
        return "cycles=" + defaultLoopCycles
                + ", buys=" + defaultLoopBuys
                + ", delayMs=" + loopDelayMs
                + ", targets=" + targets.size()
                + ", " + safetySummary()
                + ", " + sellerSummary()
                + ", " + parserSummary()
                + ", " + antiAfkSummary()
                + ", keys=" + keySummary();
    }

    public String safetySummary() {
        return "scanMode=" + scanMode
                + ", requireMaxPrice=" + requireMaxPrice
                + ", allowUnlimitedPrice=" + allowUnlimitedPrice
                + ", refreshTimeoutMs=" + refreshTimeoutMs
                + ", maxRefreshFailStreak=" + maxRefreshFailStreak
                + ", blacklist=" + blacklistKeywords.size();
    }

    public String sellerSummary() {
        return "sellerMarkupPercent=" + sellerMarkupPercent;
    }

    public String parserSummary() {
        return "parserBuyPercent=" + parserBuyPercent
                + ", parserSellPercent=" + parserSellPercent
                + ", parserOpenWaitMs=" + parserOpenWaitMs
                + ", parserBetweenItemsMs=" + parserBetweenItemsMs;
    }

    public String antiAfkSummary() {
        return "antiAfkEnabled=" + antiAfkEnabled
                + ", antiAfkIntervalMs=" + antiAfkIntervalMs
                + ", antiAfkAnarchy=" + (antiAfkAnarchy == null || antiAfkAnarchy.isEmpty() ? "auto" : antiAfkAnarchy);
    }

    public String keySummary() {
        return "debug=" + keyDebug
                + ", fp=" + keyFingerprint
                + ", scan=" + keyScan
                + ", observer=" + keyObserver
                + ", refresh=" + keyRefresh
                + ", buy=" + keyBuy
                + ", oneCycle=" + keyOneCycle
                + ", loop=" + keyLimitedLoop
                + ", gui=" + keyGui
                + ", parser=" + keyParser
                + ", fullAuto=" + keyFullAuto
                + ", sellOnly=" + keySellOnly;
    }

    private List<String> normalizeBlacklist(List<String> source) {
        List<String> result = new ArrayList<String>();

        if (source == null) {
            return result;
        }

        for (String value : source) {
            String normalized = normalizeKeyword(value);
            if (!normalized.isEmpty() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }

        return result;
    }

    private String normalizeKeyword(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static int clampPercent(int value) {
        if (value < 1) {
            return 1;
        }

        if (value > 500) {
            return 500;
        }

        return value;
    }

    private String normalizeAnarchy(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().toLowerCase();
        if (normalized.startsWith("/an")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("an")) {
            normalized = normalized.substring(2);
        }

        normalized = normalized.replaceAll("[^0-9]", "");
        return normalized;
    }

    private static int clampMarkup(int value) {
        if (value < 0) {
            return 0;
        }

        if (value > 500) {
            return 500;
        }

        return value;
    }

    private static int sanitizeKey(int value, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return value;
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static long clampLong(long value, long min, long max, long fallback) {
        if (value <= 0L) {
            return fallback;
        }
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
