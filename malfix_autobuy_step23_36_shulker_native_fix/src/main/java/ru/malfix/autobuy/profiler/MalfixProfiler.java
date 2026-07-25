package ru.malfix.autobuy.profiler;

import java.util.Locale;

/**
 * Step 22.52 lightweight profiler for scanner/seller lag analysis.
 *
 * Hot path rules:
 * - only primitive counters on record* methods;
 * - no StringBuilder/String.format from tick/scan paths;
 * - detailed strings are built only for overlay/chat/log requests.
 */
public final class MalfixProfiler {

    private static final long WINDOW_MS = 1000L;
    private static final long OVERLAY_CACHE_MS = 250L;

    private static boolean enabled = true;
    // Overlay text itself can cost FPS because it formats and draws lines every frame.
    // Keep profiler counters enabled, but require explicit .mab prof overlay on for visual spam.
    private static boolean overlayEnabled = false;

    private static long windowStartedAtMs = System.currentTimeMillis();

    private static long ticks;
    private static long tickTotalNs;
    private static long tickMaxNs;

    private static long auctionReads;
    private static long auctionReadSlots;
    private static long auctionReadTotalNs;
    private static long auctionReadMaxNs;

    private static long scannerCalls;
    private static long scannerCheckedSlots;
    private static long scannerTotalNs;
    private static long scannerMaxNs;
    private static long scannerEmptySlots;
    private static long scannerFastBlacklistSkips;
    private static long scannerPrefilterRejects;
    private static long scannerPrefilterPasses;
    private static long scannerMatchRejects;
    private static long scannerMatchPasses;
    private static long scannerFullBlacklistSkips;
    private static long scannerPriceMissing;
    private static long scannerPriceRejected;
    private static long scannerAcceptedCandidates;

    private static long matcherCalls;
    private static long matcherTotalNs;
    private static long matcherMaxNs;

    private static long priceParseCalls;
    private static long priceParseTotalNs;
    private static long priceParseMaxNs;

    private static long tooltipCalls;
    private static long tooltipTotalNs;
    private static long tooltipMaxNs;

    private static long nbtCalls;
    private static long nbtTotalNs;
    private static long nbtMaxNs;

    private static long inventoryPreviewCalls;
    private static long inventoryPreviewCheckedSlots;
    private static long inventoryPreviewTotalNs;
    private static long inventoryPreviewMaxNs;

    private static long clickCalls;
    private static long refreshClicks;
    private static long storageClicks;
    private static long sellerClicks;

    private static long totalStartedAtMs = System.currentTimeMillis();

    private static long totalTicks;
    private static long totalTickTotalNs;
    private static long totalTickMaxNs;

    private static long totalAuctionReads;
    private static long totalAuctionReadSlots;
    private static long totalAuctionReadTotalNs;
    private static long totalAuctionReadMaxNs;

    private static long totalScannerCalls;
    private static long totalScannerCheckedSlots;
    private static long totalScannerTotalNs;
    private static long totalScannerMaxNs;
    private static long totalScannerEmptySlots;
    private static long totalScannerFastBlacklistSkips;
    private static long totalScannerPrefilterRejects;
    private static long totalScannerPrefilterPasses;
    private static long totalScannerMatchRejects;
    private static long totalScannerMatchPasses;
    private static long totalScannerFullBlacklistSkips;
    private static long totalScannerPriceMissing;
    private static long totalScannerPriceRejected;
    private static long totalScannerAcceptedCandidates;

    private static long totalMatcherCalls;
    private static long totalMatcherTotalNs;
    private static long totalMatcherMaxNs;

    private static long totalPriceParseCalls;
    private static long totalPriceParseTotalNs;
    private static long totalPriceParseMaxNs;

    private static long totalTooltipCalls;
    private static long totalTooltipTotalNs;
    private static long totalTooltipMaxNs;

    private static long totalNbtCalls;
    private static long totalNbtTotalNs;
    private static long totalNbtMaxNs;

    private static long totalInventoryPreviewCalls;
    private static long totalInventoryPreviewCheckedSlots;
    private static long totalInventoryPreviewTotalNs;
    private static long totalInventoryPreviewMaxNs;

    private static long totalClickCalls;
    private static long totalRefreshClicks;
    private static long totalStorageClicks;
    private static long totalSellerClicks;

    private static Snapshot lastSnapshot = Snapshot.empty();

    private static long overlayCacheBuiltAtMs;
    private static String overlayLine1Cache = "";
    private static String overlayLine2Cache = "";
    private static String overlayLine3Cache = "";
    private static String overlayLine4Cache = "";

    private MalfixProfiler() {
    }

    public static long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            reset();
        }
    }

    public static boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public static void setOverlayEnabled(boolean value) {
        overlayEnabled = value;
        overlayCacheBuiltAtMs = 0L;
    }

    public static void reset() {
        windowStartedAtMs = System.currentTimeMillis();
        ticks = 0L;
        tickTotalNs = 0L;
        tickMaxNs = 0L;
        auctionReads = 0L;
        auctionReadSlots = 0L;
        auctionReadTotalNs = 0L;
        auctionReadMaxNs = 0L;
        scannerCalls = 0L;
        scannerCheckedSlots = 0L;
        scannerTotalNs = 0L;
        scannerMaxNs = 0L;
        scannerEmptySlots = 0L;
        scannerFastBlacklistSkips = 0L;
        scannerPrefilterRejects = 0L;
        scannerPrefilterPasses = 0L;
        scannerMatchRejects = 0L;
        scannerMatchPasses = 0L;
        scannerFullBlacklistSkips = 0L;
        scannerPriceMissing = 0L;
        scannerPriceRejected = 0L;
        scannerAcceptedCandidates = 0L;
        matcherCalls = 0L;
        matcherTotalNs = 0L;
        matcherMaxNs = 0L;
        priceParseCalls = 0L;
        priceParseTotalNs = 0L;
        priceParseMaxNs = 0L;
        tooltipCalls = 0L;
        tooltipTotalNs = 0L;
        tooltipMaxNs = 0L;
        nbtCalls = 0L;
        nbtTotalNs = 0L;
        nbtMaxNs = 0L;
        inventoryPreviewCalls = 0L;
        inventoryPreviewCheckedSlots = 0L;
        inventoryPreviewTotalNs = 0L;
        inventoryPreviewMaxNs = 0L;
        clickCalls = 0L;
        refreshClicks = 0L;
        storageClicks = 0L;
        sellerClicks = 0L;

        totalStartedAtMs = System.currentTimeMillis();
        totalTicks = 0L;
        totalTickTotalNs = 0L;
        totalTickMaxNs = 0L;
        totalAuctionReads = 0L;
        totalAuctionReadSlots = 0L;
        totalAuctionReadTotalNs = 0L;
        totalAuctionReadMaxNs = 0L;
        totalScannerCalls = 0L;
        totalScannerCheckedSlots = 0L;
        totalScannerTotalNs = 0L;
        totalScannerMaxNs = 0L;
        totalScannerEmptySlots = 0L;
        totalScannerFastBlacklistSkips = 0L;
        totalScannerPrefilterRejects = 0L;
        totalScannerPrefilterPasses = 0L;
        totalScannerMatchRejects = 0L;
        totalScannerMatchPasses = 0L;
        totalScannerFullBlacklistSkips = 0L;
        totalScannerPriceMissing = 0L;
        totalScannerPriceRejected = 0L;
        totalScannerAcceptedCandidates = 0L;
        totalMatcherCalls = 0L;
        totalMatcherTotalNs = 0L;
        totalMatcherMaxNs = 0L;
        totalPriceParseCalls = 0L;
        totalPriceParseTotalNs = 0L;
        totalPriceParseMaxNs = 0L;
        totalTooltipCalls = 0L;
        totalTooltipTotalNs = 0L;
        totalTooltipMaxNs = 0L;
        totalNbtCalls = 0L;
        totalNbtTotalNs = 0L;
        totalNbtMaxNs = 0L;
        totalInventoryPreviewCalls = 0L;
        totalInventoryPreviewCheckedSlots = 0L;
        totalInventoryPreviewTotalNs = 0L;
        totalInventoryPreviewMaxNs = 0L;
        totalClickCalls = 0L;
        totalRefreshClicks = 0L;
        totalStorageClicks = 0L;
        totalSellerClicks = 0L;

        lastSnapshot = Snapshot.empty();
        overlayCacheBuiltAtMs = 0L;
        overlayLine1Cache = "";
        overlayLine2Cache = "";
        overlayLine3Cache = "";
        overlayLine4Cache = "";
    }

    public static void tickWindow() {
        if (!enabled) {
            return;
        }
        rollIfNeeded();
    }

    public static void recordClientTick(long startedNs) {
        if (!enabled || startedNs <= 0L) {
            return;
        }
        long ns = elapsed(startedNs);
        rollIfNeeded();
        ticks++;
        tickTotalNs += ns;
        if (ns > tickMaxNs) {
            tickMaxNs = ns;
        }
        totalTicks++;
        totalTickTotalNs += ns;
        if (ns > totalTickMaxNs) {
            totalTickMaxNs = ns;
        }
    }

    public static void recordAuctionRead(long startedNs, int slotCount) {
        if (!enabled || startedNs <= 0L) {
            return;
        }
        long ns = elapsed(startedNs);
        rollIfNeeded();
        int safeSlotCount = Math.max(0, slotCount);
        auctionReads++;
        auctionReadSlots += safeSlotCount;
        auctionReadTotalNs += ns;
        if (ns > auctionReadMaxNs) {
            auctionReadMaxNs = ns;
        }
        totalAuctionReads++;
        totalAuctionReadSlots += safeSlotCount;
        totalAuctionReadTotalNs += ns;
        if (ns > totalAuctionReadMaxNs) {
            totalAuctionReadMaxNs = ns;
        }
    }

    public static void recordScan(long startedNs, int checkedSlots) {
        recordScan(startedNs, checkedSlots, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static void recordScan(
            long startedNs,
            int checkedSlots,
            int emptySlots,
            int fastBlacklistSkips,
            int prefilterRejects,
            int prefilterPasses,
            int matchRejects,
            int matchPasses,
            int fullBlacklistSkips,
            int priceMissing,
            int priceRejected,
            int acceptedCandidates
    ) {
        if (!enabled || startedNs <= 0L) {
            return;
        }
        long ns = elapsed(startedNs);
        rollIfNeeded();
        int safeCheckedSlots = Math.max(0, checkedSlots);
        int safeEmptySlots = Math.max(0, emptySlots);
        int safeFastBlacklistSkips = Math.max(0, fastBlacklistSkips);
        int safePrefilterRejects = Math.max(0, prefilterRejects);
        int safePrefilterPasses = Math.max(0, prefilterPasses);
        int safeMatchRejects = Math.max(0, matchRejects);
        int safeMatchPasses = Math.max(0, matchPasses);
        int safeFullBlacklistSkips = Math.max(0, fullBlacklistSkips);
        int safePriceMissing = Math.max(0, priceMissing);
        int safePriceRejected = Math.max(0, priceRejected);
        int safeAcceptedCandidates = Math.max(0, acceptedCandidates);

        scannerCalls++;
        scannerCheckedSlots += safeCheckedSlots;
        scannerTotalNs += ns;
        if (ns > scannerMaxNs) {
            scannerMaxNs = ns;
        }
        scannerEmptySlots += safeEmptySlots;
        scannerFastBlacklistSkips += safeFastBlacklistSkips;
        scannerPrefilterRejects += safePrefilterRejects;
        scannerPrefilterPasses += safePrefilterPasses;
        scannerMatchRejects += safeMatchRejects;
        scannerMatchPasses += safeMatchPasses;
        scannerFullBlacklistSkips += safeFullBlacklistSkips;
        scannerPriceMissing += safePriceMissing;
        scannerPriceRejected += safePriceRejected;
        scannerAcceptedCandidates += safeAcceptedCandidates;

        totalScannerCalls++;
        totalScannerCheckedSlots += safeCheckedSlots;
        totalScannerTotalNs += ns;
        if (ns > totalScannerMaxNs) {
            totalScannerMaxNs = ns;
        }
        totalScannerEmptySlots += safeEmptySlots;
        totalScannerFastBlacklistSkips += safeFastBlacklistSkips;
        totalScannerPrefilterRejects += safePrefilterRejects;
        totalScannerPrefilterPasses += safePrefilterPasses;
        totalScannerMatchRejects += safeMatchRejects;
        totalScannerMatchPasses += safeMatchPasses;
        totalScannerFullBlacklistSkips += safeFullBlacklistSkips;
        totalScannerPriceMissing += safePriceMissing;
        totalScannerPriceRejected += safePriceRejected;
        totalScannerAcceptedCandidates += safeAcceptedCandidates;
    }

    public static void recordMatcher(long startedNs) {
        if (!enabled || startedNs <= 0L) {
            return;
        }
        long ns = elapsed(startedNs);
        rollIfNeeded();
        matcherCalls++;
        matcherTotalNs += ns;
        if (ns > matcherMaxNs) {
            matcherMaxNs = ns;
        }
        totalMatcherCalls++;
        totalMatcherTotalNs += ns;
        if (ns > totalMatcherMaxNs) {
            totalMatcherMaxNs = ns;
        }
    }

    public static void recordPriceParse(long startedNs) {
        if (!enabled || startedNs <= 0L) {
            return;
        }
        long ns = elapsed(startedNs);
        rollIfNeeded();
        priceParseCalls++;
        priceParseTotalNs += ns;
        if (ns > priceParseMaxNs) {
            priceParseMaxNs = ns;
        }
        totalPriceParseCalls++;
        totalPriceParseTotalNs += ns;
        if (ns > totalPriceParseMaxNs) {
            totalPriceParseMaxNs = ns;
        }
    }

    public static void recordTooltip(long startedNs) {
        if (!enabled || startedNs <= 0L) {
            return;
        }
        long ns = elapsed(startedNs);
        rollIfNeeded();
        tooltipCalls++;
        tooltipTotalNs += ns;
        if (ns > tooltipMaxNs) {
            tooltipMaxNs = ns;
        }
        totalTooltipCalls++;
        totalTooltipTotalNs += ns;
        if (ns > totalTooltipMaxNs) {
            totalTooltipMaxNs = ns;
        }
    }

    public static void recordNbt(long startedNs) {
        if (!enabled || startedNs <= 0L) {
            return;
        }
        long ns = elapsed(startedNs);
        rollIfNeeded();
        nbtCalls++;
        nbtTotalNs += ns;
        if (ns > nbtMaxNs) {
            nbtMaxNs = ns;
        }
        totalNbtCalls++;
        totalNbtTotalNs += ns;
        if (ns > totalNbtMaxNs) {
            totalNbtMaxNs = ns;
        }
    }

    public static void recordInventoryPreview(long startedNs, int checkedSlots) {
        if (!enabled || startedNs <= 0L) {
            return;
        }
        long ns = elapsed(startedNs);
        rollIfNeeded();
        int safeCheckedSlots = Math.max(0, checkedSlots);
        inventoryPreviewCalls++;
        inventoryPreviewCheckedSlots += safeCheckedSlots;
        inventoryPreviewTotalNs += ns;
        if (ns > inventoryPreviewMaxNs) {
            inventoryPreviewMaxNs = ns;
        }
        totalInventoryPreviewCalls++;
        totalInventoryPreviewCheckedSlots += safeCheckedSlots;
        totalInventoryPreviewTotalNs += ns;
        if (ns > totalInventoryPreviewMaxNs) {
            totalInventoryPreviewMaxNs = ns;
        }
    }

    public static void recordClick(String kind) {
        if (!enabled) {
            return;
        }
        rollIfNeeded();
        clickCalls++;
        totalClickCalls++;
        String safe = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        if (safe.contains("refresh")) {
            refreshClicks++;
            totalRefreshClicks++;
        } else if (safe.contains("storage")) {
            storageClicks++;
            totalStorageClicks++;
        } else if (safe.contains("seller") || safe.contains("sell")) {
            sellerClicks++;
            totalSellerClicks++;
        }
    }

    public static Snapshot snapshot() {
        rollIfNeeded();
        return lastSnapshot;
    }

    public static String overlayLine1() {
        refreshOverlayCache();
        return overlayLine1Cache;
    }

    public static String overlayLine2() {
        refreshOverlayCache();
        return overlayLine2Cache;
    }

    public static String overlayLine3() {
        refreshOverlayCache();
        return overlayLine3Cache;
    }

    public static String overlayLine4() {
        refreshOverlayCache();
        return overlayLine4Cache;
    }

    public static String summary() {
        Snapshot s = snapshot();
        StringBuilder builder = new StringBuilder(1024);
        long totalMs = Math.max(0L, System.currentTimeMillis() - totalStartedAtMs);

        builder.append("enabled=").append(enabled)
                .append(", overlay=").append(overlayEnabled)
                .append(", recentMs=").append(s.windowMs)
                .append(", totalSec=").append(totalMs / 1000L).append('\n');

        builder.append("recent.scan calls=").append(s.scannerCalls)
                .append(", slots=").append(s.scannerCheckedSlots)
                .append(", avg/max=").append(fmt(s.scannerAvgMs)).append('/').append(fmt(s.scannerMaxMs)).append("ms")
                .append(", preReject/pass=").append(s.scannerPrefilterRejects).append('/').append(s.scannerPrefilterPasses)
                .append(", matchPass=").append(s.scannerMatchPasses)
                .append(", accepted=").append(s.scannerAcceptedCandidates).append('\n');

        builder.append("recent.heavy tooltip=").append(s.tooltipCalls)
                .append(" avg/max=").append(fmt(s.tooltipAvgMs)).append('/').append(fmt(s.tooltipMaxMs)).append("ms")
                .append(", nbt=").append(s.nbtCalls)
                .append(" avg/max=").append(fmt(s.nbtAvgMs)).append('/').append(fmt(s.nbtMaxMs)).append("ms")
                .append(", price=").append(s.priceParseCalls)
                .append(", matcher=").append(s.matcherCalls).append('\n');

        builder.append("recent.reject empty=").append(s.scannerEmptySlots)
                .append(", blacklistFast/full=").append(s.scannerFastBlacklistSkips).append('/').append(s.scannerFullBlacklistSkips)
                .append(", matchReject=").append(s.scannerMatchRejects)
                .append(", noPrice=").append(s.scannerPriceMissing)
                .append(", priceReject=").append(s.scannerPriceRejected).append('\n');

        builder.append("recent.read avg/max=").append(fmt(s.auctionReadAvgMs)).append('/').append(fmt(s.auctionReadMaxMs)).append("ms")
                .append(", sellerPreview avg/max=").append(fmt(s.inventoryPreviewAvgMs)).append('/').append(fmt(s.inventoryPreviewMaxMs)).append("ms")
                .append(", clicks r/storage/sell=").append(s.refreshClicks).append('/').append(s.storageClicks).append('/').append(s.sellerClicks).append('\n');

        builder.append("total.scan calls=").append(totalScannerCalls)
                .append(", slots=").append(totalScannerCheckedSlots)
                .append(", avg/max=").append(fmt(avgMs(totalScannerTotalNs, totalScannerCalls))).append('/').append(fmt(ms(totalScannerMaxNs))).append("ms")
                .append(", preReject/pass=").append(totalScannerPrefilterRejects).append('/').append(totalScannerPrefilterPasses)
                .append(", accepted=").append(totalScannerAcceptedCandidates).append('\n');

        builder.append("total.heavy tooltip=").append(totalTooltipCalls)
                .append(", nbt=").append(totalNbtCalls)
                .append(", price=").append(totalPriceParseCalls)
                .append(", matcher=").append(totalMatcherCalls)
                .append(", invPreview=").append(totalInventoryPreviewCalls)
                .append('/').append(totalInventoryPreviewCheckedSlots).append('\n');

        builder.append("bottleneck=").append(diagnoseRecent(s));
        return builder.toString();
    }

    public static String debug() {
        Snapshot s = snapshot();
        StringBuilder builder = new StringBuilder(2048);
        builder.append("profilerEnabled=").append(enabled).append('\n');
        builder.append("profilerOverlay=").append(overlayEnabled).append('\n');
        builder.append("recentWindowMs=").append(s.windowMs).append('\n');
        appendSnapshot(builder, "recent", s);
        builder.append("totalSinceResetMs=").append(Math.max(0L, System.currentTimeMillis() - totalStartedAtMs)).append('\n');
        appendTotals(builder, "total");
        builder.append("diagnosis=").append(diagnoseRecent(s)).append('\n');
        return builder.toString();
    }

    private static void refreshOverlayCache() {
        long now = System.currentTimeMillis();
        if (overlayCacheBuiltAtMs > 0L && now - overlayCacheBuiltAtMs < OVERLAY_CACHE_MS) {
            return;
        }

        Snapshot s = snapshot();
        overlayLine1Cache = "SCAN " + fmt(s.scannerAvgMs) + "/" + fmt(s.scannerMaxMs) + "ms c=" + s.scannerCalls + " slots=" + s.scannerCheckedSlots;
        overlayLine2Cache = "PF r/p=" + s.scannerPrefilterRejects + "/" + s.scannerPrefilterPasses
                + " match=" + s.scannerMatchPasses + " ok=" + s.scannerAcceptedCandidates;
        overlayLine3Cache = "TT " + s.tooltipCalls + " " + fmt(s.tooltipAvgMs) + "/" + fmt(s.tooltipMaxMs)
                + " NBT " + s.nbtCalls + " " + fmt(s.nbtAvgMs) + "/" + fmt(s.nbtMaxMs);
        overlayLine4Cache = "PRICE " + s.priceParseCalls + " MATCH " + s.matcherCalls
                + " SELL " + s.inventoryPreviewCalls + "/" + s.inventoryPreviewCheckedSlots;
        overlayCacheBuiltAtMs = now;
    }

    private static void appendSnapshot(StringBuilder builder, String prefix, Snapshot s) {
        builder.append(prefix).append(".ticks=").append(s.ticks)
                .append(", avgMs=").append(fmt(s.tickAvgMs))
                .append(", maxMs=").append(fmt(s.tickMaxMs)).append('\n');
        builder.append(prefix).append(".auctionReads=").append(s.auctionReads)
                .append(", slots=").append(s.auctionReadSlots)
                .append(", avgMs=").append(fmt(s.auctionReadAvgMs))
                .append(", maxMs=").append(fmt(s.auctionReadMaxMs)).append('\n');
        builder.append(prefix).append(".scanCalls=").append(s.scannerCalls)
                .append(", checkedSlots=").append(s.scannerCheckedSlots)
                .append(", avgMs=").append(fmt(s.scannerAvgMs))
                .append(", maxMs=").append(fmt(s.scannerMaxMs)).append('\n');
        builder.append(prefix).append(".scanDetails=")
                .append("empty=").append(s.scannerEmptySlots)
                .append(", blacklistFast=").append(s.scannerFastBlacklistSkips)
                .append(", prefilterReject=").append(s.scannerPrefilterRejects)
                .append(", prefilterPass=").append(s.scannerPrefilterPasses)
                .append(", matchReject=").append(s.scannerMatchRejects)
                .append(", matchPass=").append(s.scannerMatchPasses)
                .append(", blacklistFull=").append(s.scannerFullBlacklistSkips)
                .append(", priceMissing=").append(s.scannerPriceMissing)
                .append(", priceRejected=").append(s.scannerPriceRejected)
                .append(", accepted=").append(s.scannerAcceptedCandidates).append('\n');
        builder.append(prefix).append(".matcherCalls=").append(s.matcherCalls)
                .append(", avgMs=").append(fmt(s.matcherAvgMs))
                .append(", maxMs=").append(fmt(s.matcherMaxMs)).append('\n');
        builder.append(prefix).append(".priceParseCalls=").append(s.priceParseCalls)
                .append(", avgMs=").append(fmt(s.priceParseAvgMs))
                .append(", maxMs=").append(fmt(s.priceParseMaxMs)).append('\n');
        builder.append(prefix).append(".tooltipCalls=").append(s.tooltipCalls)
                .append(", avgMs=").append(fmt(s.tooltipAvgMs))
                .append(", maxMs=").append(fmt(s.tooltipMaxMs)).append('\n');
        builder.append(prefix).append(".nbtCalls=").append(s.nbtCalls)
                .append(", avgMs=").append(fmt(s.nbtAvgMs))
                .append(", maxMs=").append(fmt(s.nbtMaxMs)).append('\n');
        builder.append(prefix).append(".inventoryPreviewCalls=").append(s.inventoryPreviewCalls)
                .append(", checkedSlots=").append(s.inventoryPreviewCheckedSlots)
                .append(", avgMs=").append(fmt(s.inventoryPreviewAvgMs))
                .append(", maxMs=").append(fmt(s.inventoryPreviewMaxMs)).append('\n');
        builder.append(prefix).append(".clicks=").append(s.clickCalls)
                .append(", refresh=").append(s.refreshClicks)
                .append(", storage=").append(s.storageClicks)
                .append(", seller=").append(s.sellerClicks).append('\n');
    }

    private static void appendTotals(StringBuilder builder, String prefix) {
        builder.append(prefix).append(".ticks=").append(totalTicks)
                .append(", avgMs=").append(fmt(avgMs(totalTickTotalNs, totalTicks)))
                .append(", maxMs=").append(fmt(ms(totalTickMaxNs))).append('\n');
        builder.append(prefix).append(".auctionReads=").append(totalAuctionReads)
                .append(", slots=").append(totalAuctionReadSlots)
                .append(", avgMs=").append(fmt(avgMs(totalAuctionReadTotalNs, totalAuctionReads)))
                .append(", maxMs=").append(fmt(ms(totalAuctionReadMaxNs))).append('\n');
        builder.append(prefix).append(".scanCalls=").append(totalScannerCalls)
                .append(", checkedSlots=").append(totalScannerCheckedSlots)
                .append(", avgMs=").append(fmt(avgMs(totalScannerTotalNs, totalScannerCalls)))
                .append(", maxMs=").append(fmt(ms(totalScannerMaxNs))).append('\n');
        builder.append(prefix).append(".scanDetails=")
                .append("empty=").append(totalScannerEmptySlots)
                .append(", blacklistFast=").append(totalScannerFastBlacklistSkips)
                .append(", prefilterReject=").append(totalScannerPrefilterRejects)
                .append(", prefilterPass=").append(totalScannerPrefilterPasses)
                .append(", matchReject=").append(totalScannerMatchRejects)
                .append(", matchPass=").append(totalScannerMatchPasses)
                .append(", blacklistFull=").append(totalScannerFullBlacklistSkips)
                .append(", priceMissing=").append(totalScannerPriceMissing)
                .append(", priceRejected=").append(totalScannerPriceRejected)
                .append(", accepted=").append(totalScannerAcceptedCandidates).append('\n');
        builder.append(prefix).append(".matcherCalls=").append(totalMatcherCalls)
                .append(", avgMs=").append(fmt(avgMs(totalMatcherTotalNs, totalMatcherCalls)))
                .append(", maxMs=").append(fmt(ms(totalMatcherMaxNs))).append('\n');
        builder.append(prefix).append(".priceParseCalls=").append(totalPriceParseCalls)
                .append(", avgMs=").append(fmt(avgMs(totalPriceParseTotalNs, totalPriceParseCalls)))
                .append(", maxMs=").append(fmt(ms(totalPriceParseMaxNs))).append('\n');
        builder.append(prefix).append(".tooltipCalls=").append(totalTooltipCalls)
                .append(", avgMs=").append(fmt(avgMs(totalTooltipTotalNs, totalTooltipCalls)))
                .append(", maxMs=").append(fmt(ms(totalTooltipMaxNs))).append('\n');
        builder.append(prefix).append(".nbtCalls=").append(totalNbtCalls)
                .append(", avgMs=").append(fmt(avgMs(totalNbtTotalNs, totalNbtCalls)))
                .append(", maxMs=").append(fmt(ms(totalNbtMaxNs))).append('\n');
        builder.append(prefix).append(".inventoryPreviewCalls=").append(totalInventoryPreviewCalls)
                .append(", checkedSlots=").append(totalInventoryPreviewCheckedSlots)
                .append(", avgMs=").append(fmt(avgMs(totalInventoryPreviewTotalNs, totalInventoryPreviewCalls)))
                .append(", maxMs=").append(fmt(ms(totalInventoryPreviewMaxNs))).append('\n');
        builder.append(prefix).append(".clicks=").append(totalClickCalls)
                .append(", refresh=").append(totalRefreshClicks)
                .append(", storage=").append(totalStorageClicks)
                .append(", seller=").append(totalSellerClicks).append('\n');
    }

    private static String diagnoseRecent(Snapshot s) {
        if (s == null) {
            return "no_snapshot";
        }
        if (s.scannerCalls <= 0L && s.auctionReads <= 0L && s.inventoryPreviewCalls <= 0L) {
            return "no_recent_activity; use total.* after real scan/sell";
        }
        if (s.inventoryPreviewMaxMs >= 8.0D || s.inventoryPreviewAvgMs >= 4.0D) {
            return "seller_inventory_preview_or_take_item";
        }
        if (s.auctionReadMaxMs >= 8.0D || s.auctionReadAvgMs >= 4.0D) {
            return "auction_slot_read_or_screen_handler";
        }
        if (s.tooltipMaxMs >= 5.0D || s.tooltipCalls >= 30L) {
            return "tooltip_generation_too_hot";
        }
        if (s.nbtMaxMs >= 5.0D || s.nbtCalls >= 20L) {
            return "nbt_snbt_serialization_too_hot";
        }
        if (s.priceParseMaxMs >= 4.0D || s.priceParseAvgMs >= 1.5D) {
            return "price_parser";
        }
        if (s.matcherMaxMs >= 4.0D || s.matcherAvgMs >= 1.5D) {
            return "item_matcher";
        }
        if (s.scannerMaxMs >= 10.0D || s.scannerAvgMs >= 5.0D) {
            return "scanner_loop_general";
        }
        if (s.scannerPrefilterRejects == 0L && s.scannerCheckedSlots > 0L && s.scannerPrefilterPasses > 0L) {
            return "prefilter_too_broad; too_many_slots_reach_heavy_match";
        }
        return "no_clear_recent_spike";
    }

    private static void rollIfNeeded() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsedMs = now - windowStartedAtMs;
        if (elapsedMs < WINDOW_MS) {
            return;
        }

        lastSnapshot = new Snapshot(
                elapsedMs,
                ticks,
                avgMs(tickTotalNs, ticks),
                ms(tickMaxNs),
                auctionReads,
                auctionReadSlots,
                avgMs(auctionReadTotalNs, auctionReads),
                ms(auctionReadMaxNs),
                scannerCalls,
                scannerCheckedSlots,
                avgMs(scannerTotalNs, scannerCalls),
                ms(scannerMaxNs),
                scannerEmptySlots,
                scannerFastBlacklistSkips,
                scannerPrefilterRejects,
                scannerPrefilterPasses,
                scannerMatchRejects,
                scannerMatchPasses,
                scannerFullBlacklistSkips,
                scannerPriceMissing,
                scannerPriceRejected,
                scannerAcceptedCandidates,
                matcherCalls,
                avgMs(matcherTotalNs, matcherCalls),
                ms(matcherMaxNs),
                priceParseCalls,
                avgMs(priceParseTotalNs, priceParseCalls),
                ms(priceParseMaxNs),
                tooltipCalls,
                avgMs(tooltipTotalNs, tooltipCalls),
                ms(tooltipMaxNs),
                nbtCalls,
                avgMs(nbtTotalNs, nbtCalls),
                ms(nbtMaxNs),
                inventoryPreviewCalls,
                inventoryPreviewCheckedSlots,
                avgMs(inventoryPreviewTotalNs, inventoryPreviewCalls),
                ms(inventoryPreviewMaxNs),
                clickCalls,
                refreshClicks,
                storageClicks,
                sellerClicks
        );

        ticks = 0L;
        tickTotalNs = 0L;
        tickMaxNs = 0L;
        auctionReads = 0L;
        auctionReadSlots = 0L;
        auctionReadTotalNs = 0L;
        auctionReadMaxNs = 0L;
        scannerCalls = 0L;
        scannerCheckedSlots = 0L;
        scannerTotalNs = 0L;
        scannerMaxNs = 0L;
        scannerEmptySlots = 0L;
        scannerFastBlacklistSkips = 0L;
        scannerPrefilterRejects = 0L;
        scannerPrefilterPasses = 0L;
        scannerMatchRejects = 0L;
        scannerMatchPasses = 0L;
        scannerFullBlacklistSkips = 0L;
        scannerPriceMissing = 0L;
        scannerPriceRejected = 0L;
        scannerAcceptedCandidates = 0L;
        matcherCalls = 0L;
        matcherTotalNs = 0L;
        matcherMaxNs = 0L;
        priceParseCalls = 0L;
        priceParseTotalNs = 0L;
        priceParseMaxNs = 0L;
        tooltipCalls = 0L;
        tooltipTotalNs = 0L;
        tooltipMaxNs = 0L;
        nbtCalls = 0L;
        nbtTotalNs = 0L;
        nbtMaxNs = 0L;
        inventoryPreviewCalls = 0L;
        inventoryPreviewCheckedSlots = 0L;
        inventoryPreviewTotalNs = 0L;
        inventoryPreviewMaxNs = 0L;
        clickCalls = 0L;
        refreshClicks = 0L;
        storageClicks = 0L;
        sellerClicks = 0L;
        windowStartedAtMs = now;
        overlayCacheBuiltAtMs = 0L;
    }

    private static long elapsed(long startedNs) {
        long now = System.nanoTime();
        return startedNs <= 0L || now < startedNs ? 0L : now - startedNs;
    }

    private static double avgMs(long totalNs, long count) {
        if (count <= 0L) {
            return 0.0D;
        }
        return ms(totalNs) / (double) count;
    }

    private static double ms(long ns) {
        return ((double) ns) / 1_000_000.0D;
    }

    private static String fmt(double value) {
        if (value < 0.05D) {
            return "0.00";
        }
        return String.format(Locale.US, "%.2f", Double.valueOf(value));
    }

    public static final class Snapshot {
        public final long windowMs;
        public final long ticks;
        public final double tickAvgMs;
        public final double tickMaxMs;
        public final long auctionReads;
        public final long auctionReadSlots;
        public final double auctionReadAvgMs;
        public final double auctionReadMaxMs;
        public final long scannerCalls;
        public final long scannerCheckedSlots;
        public final double scannerAvgMs;
        public final double scannerMaxMs;
        public final long scannerEmptySlots;
        public final long scannerFastBlacklistSkips;
        public final long scannerPrefilterRejects;
        public final long scannerPrefilterPasses;
        public final long scannerMatchRejects;
        public final long scannerMatchPasses;
        public final long scannerFullBlacklistSkips;
        public final long scannerPriceMissing;
        public final long scannerPriceRejected;
        public final long scannerAcceptedCandidates;
        public final long matcherCalls;
        public final double matcherAvgMs;
        public final double matcherMaxMs;
        public final long priceParseCalls;
        public final double priceParseAvgMs;
        public final double priceParseMaxMs;
        public final long tooltipCalls;
        public final double tooltipAvgMs;
        public final double tooltipMaxMs;
        public final long nbtCalls;
        public final double nbtAvgMs;
        public final double nbtMaxMs;
        public final long inventoryPreviewCalls;
        public final long inventoryPreviewCheckedSlots;
        public final double inventoryPreviewAvgMs;
        public final double inventoryPreviewMaxMs;
        public final long clickCalls;
        public final long refreshClicks;
        public final long storageClicks;
        public final long sellerClicks;

        private Snapshot(
                long windowMs,
                long ticks,
                double tickAvgMs,
                double tickMaxMs,
                long auctionReads,
                long auctionReadSlots,
                double auctionReadAvgMs,
                double auctionReadMaxMs,
                long scannerCalls,
                long scannerCheckedSlots,
                double scannerAvgMs,
                double scannerMaxMs,
                long scannerEmptySlots,
                long scannerFastBlacklistSkips,
                long scannerPrefilterRejects,
                long scannerPrefilterPasses,
                long scannerMatchRejects,
                long scannerMatchPasses,
                long scannerFullBlacklistSkips,
                long scannerPriceMissing,
                long scannerPriceRejected,
                long scannerAcceptedCandidates,
                long matcherCalls,
                double matcherAvgMs,
                double matcherMaxMs,
                long priceParseCalls,
                double priceParseAvgMs,
                double priceParseMaxMs,
                long tooltipCalls,
                double tooltipAvgMs,
                double tooltipMaxMs,
                long nbtCalls,
                double nbtAvgMs,
                double nbtMaxMs,
                long inventoryPreviewCalls,
                long inventoryPreviewCheckedSlots,
                double inventoryPreviewAvgMs,
                double inventoryPreviewMaxMs,
                long clickCalls,
                long refreshClicks,
                long storageClicks,
                long sellerClicks
        ) {
            this.windowMs = windowMs;
            this.ticks = ticks;
            this.tickAvgMs = tickAvgMs;
            this.tickMaxMs = tickMaxMs;
            this.auctionReads = auctionReads;
            this.auctionReadSlots = auctionReadSlots;
            this.auctionReadAvgMs = auctionReadAvgMs;
            this.auctionReadMaxMs = auctionReadMaxMs;
            this.scannerCalls = scannerCalls;
            this.scannerCheckedSlots = scannerCheckedSlots;
            this.scannerAvgMs = scannerAvgMs;
            this.scannerMaxMs = scannerMaxMs;
            this.scannerEmptySlots = scannerEmptySlots;
            this.scannerFastBlacklistSkips = scannerFastBlacklistSkips;
            this.scannerPrefilterRejects = scannerPrefilterRejects;
            this.scannerPrefilterPasses = scannerPrefilterPasses;
            this.scannerMatchRejects = scannerMatchRejects;
            this.scannerMatchPasses = scannerMatchPasses;
            this.scannerFullBlacklistSkips = scannerFullBlacklistSkips;
            this.scannerPriceMissing = scannerPriceMissing;
            this.scannerPriceRejected = scannerPriceRejected;
            this.scannerAcceptedCandidates = scannerAcceptedCandidates;
            this.matcherCalls = matcherCalls;
            this.matcherAvgMs = matcherAvgMs;
            this.matcherMaxMs = matcherMaxMs;
            this.priceParseCalls = priceParseCalls;
            this.priceParseAvgMs = priceParseAvgMs;
            this.priceParseMaxMs = priceParseMaxMs;
            this.tooltipCalls = tooltipCalls;
            this.tooltipAvgMs = tooltipAvgMs;
            this.tooltipMaxMs = tooltipMaxMs;
            this.nbtCalls = nbtCalls;
            this.nbtAvgMs = nbtAvgMs;
            this.nbtMaxMs = nbtMaxMs;
            this.inventoryPreviewCalls = inventoryPreviewCalls;
            this.inventoryPreviewCheckedSlots = inventoryPreviewCheckedSlots;
            this.inventoryPreviewAvgMs = inventoryPreviewAvgMs;
            this.inventoryPreviewMaxMs = inventoryPreviewMaxMs;
            this.clickCalls = clickCalls;
            this.refreshClicks = refreshClicks;
            this.storageClicks = storageClicks;
            this.sellerClicks = sellerClicks;
        }

        private static Snapshot empty() {
            return new Snapshot(0L, 0L, 0.0D, 0.0D, 0L, 0L, 0.0D, 0.0D,
                    0L, 0L, 0.0D, 0.0D, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                    0L, 0.0D, 0.0D, 0L, 0.0D, 0.0D,
                    0L, 0.0D, 0.0D, 0L, 0.0D, 0.0D,
                    0L, 0L, 0.0D, 0.0D, 0L, 0L, 0L, 0L);
        }
    }
}
