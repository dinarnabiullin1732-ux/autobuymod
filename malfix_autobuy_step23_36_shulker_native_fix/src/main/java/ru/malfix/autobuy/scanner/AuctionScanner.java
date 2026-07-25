package ru.malfix.autobuy.scanner;

import ru.malfix.autobuy.auction.AuctionSlot;
import ru.malfix.autobuy.price.ParsedPrice;
import ru.malfix.autobuy.price.PriceParser;
import ru.malfix.autobuy.profiler.MalfixProfiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public final class AuctionScanner {

    private List<TargetItem> targets;
    private ScannerSettings settings;
    private final ItemMatcher matcher;
    private final PriceParser priceParser;

    // Reused buffers: scanner is used from the client thread, so avoid per-slot/per-scan list garbage.
    private final ArrayList<TargetItem> candidateBuffer = new ArrayList<TargetItem>(8);
    private final ArrayList<TargetItem> singleTargetBuffer = new ArrayList<TargetItem>(1);

    // Old SpookyBuy-style speedup: pre-index targets by effective minecraft item id.
    // The hot path no longer checks every configured target for every auction slot.
    private final Map<String, ArrayList<TargetItem>> targetsByItemId = new HashMap<String, ArrayList<TargetItem>>();
    private final ArrayList<TargetItem> targetsWithoutItemId = new ArrayList<TargetItem>(8);

    private int profEmptySlots;
    private int profFastBlacklistSkips;
    private int profPrefilterRejects;
    private int profPrefilterPasses;
    private int profMatchRejects;
    private int profMatchPasses;
    private int profFullBlacklistSkips;
    private int profPriceMissing;
    private int profPriceRejected;
    private int profAcceptedCandidates;

    public AuctionScanner(List<TargetItem> targets, ItemMatcher matcher, PriceParser priceParser) {
        this.targets = targets == null ? defaultTargets() : new ArrayList<TargetItem>(targets);
        this.settings = ScannerSettings.defaults();
        this.matcher = matcher == null ? new ItemMatcher() : matcher;
        this.priceParser = priceParser == null ? new PriceParser() : priceParser;
        rebuildTargetIndex();
    }

    public void setTargets(List<TargetItem> targets) {
        this.targets = targets == null ? defaultTargets() : new ArrayList<TargetItem>(targets);
        rebuildTargetIndex();
    }

    public List<TargetItem> getTargets() {
        return new ArrayList<TargetItem>(targets);
    }

    public int getTargetCount() {
        return targets == null ? 0 : targets.size();
    }

    public void setSettings(ScannerSettings settings) {
        this.settings = settings == null ? ScannerSettings.defaults() : settings;
    }

    public ScannerSettings getSettings() {
        return settings == null ? ScannerSettings.defaults() : settings;
    }

    public static AuctionScanner defaultScanner() {
        return new AuctionScanner(defaultTargets(), new ItemMatcher(), new PriceParser());
    }

    public ScanResult scan(List<AuctionSlot> slots) {
        long profStart = MalfixProfiler.start();
        resetProfilerCounters();
        ScanResult result = scanInternal(slots);
        MalfixProfiler.recordScan(
                profStart,
                result == null ? 0 : result.getCheckedSlots(),
                profEmptySlots,
                profFastBlacklistSkips,
                profPrefilterRejects,
                profPrefilterPasses,
                profMatchRejects,
                profMatchPasses,
                profFullBlacklistSkips,
                profPriceMissing,
                profPriceRejected,
                profAcceptedCandidates
        );
        return result;
    }

    private ScanResult scanInternal(List<AuctionSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return ScanResult.noSlots();
        }

        ScannerSettings currentSettings = getSettings();

        int checked = 0;
        ScanCandidate best = null;

        int[] order = buildPriorityOrder(slots.size(), currentSettings.getScanMode());

        for (int i = 0; i < order.length; i++) {
            int slotIndex = order[i];
            if (slotIndex < 0 || slotIndex >= slots.size()) {
                continue;
            }

            AuctionSlot slot = slots.get(slotIndex);
            checked++;

            if (slot == null || slot.isEmpty()) {
                profEmptySlots++;
                continue;
            }

            if (isBlacklistedFast(slot, currentSettings)) {
                profFastBlacklistSkips++;
                continue;
            }

            List<TargetItem> candidates = fastCandidates(slot);
            if (candidates.isEmpty()) {
                profPrefilterRejects++;
                continue;
            }
            profPrefilterPasses++;

            MatchResult match = matcher.match(slot, candidates);
            if (!match.isMatched()) {
                profMatchRejects++;
                continue;
            }
            profMatchPasses++;

            if (isBlacklistedFull(slot, currentSettings)) {
                profFullBlacklistSkips++;
                continue;
            }

            ParsedPrice price = priceParser.parse(slot.getTooltipLines(), slot.getCount());
            if (!price.isFound()) {
                profPriceMissing++;
                continue;
            }

            TargetItem target = match.getTarget();

            if (!isPriceAllowed(target, price, currentSettings)) {
                profPriceRejected++;
                continue;
            }

            profAcceptedCandidates++;
            ScanCandidate candidate = new ScanCandidate(slot, target, price);
            if (candidate.isBetterThan(best)) {
                best = candidate;
            }
        }

        if (best == null) {
            return ScanResult.noMatch(checked);
        }

        return ScanResult.found(checked, best);
    }

    public ScanCandidate findCheapestForTarget(List<AuctionSlot> slots, TargetItem target) {
        long profStart = MalfixProfiler.start();
        resetProfilerCounters();
        ScanCandidate result = findCheapestForTargetInternal(slots, target);
        MalfixProfiler.recordScan(
                profStart,
                slots == null ? 0 : Math.min(slots.size(), getSettings().getScanMode().getMaxSlots()),
                profEmptySlots,
                profFastBlacklistSkips,
                profPrefilterRejects,
                profPrefilterPasses,
                profMatchRejects,
                profMatchPasses,
                profFullBlacklistSkips,
                profPriceMissing,
                profPriceRejected,
                profAcceptedCandidates
        );
        return result;
    }

    private ScanCandidate findCheapestForTargetInternal(List<AuctionSlot> slots, TargetItem target) {
        if (slots == null || slots.isEmpty() || target == null) {
            return null;
        }

        ScannerSettings currentSettings = getSettings();
        int[] order = buildPriorityOrder(slots.size(), currentSettings.getScanMode());
        ScanCandidate best = null;
        singleTargetBuffer.clear();
        singleTargetBuffer.add(target);

        for (int i = 0; i < order.length; i++) {
            int slotIndex = order[i];
            if (slotIndex < 0 || slotIndex >= slots.size()) {
                continue;
            }

            AuctionSlot slot = slots.get(slotIndex);
            if (slot == null || slot.isEmpty()) {
                profEmptySlots++;
                continue;
            }

            if (isBlacklistedFast(slot, currentSettings)) {
                profFastBlacklistSkips++;
                continue;
            }

            if (!matcher.isFastCandidate(slot, target)) {
                profPrefilterRejects++;
                continue;
            }
            profPrefilterPasses++;

            MatchResult match = matcher.match(slot, singleTargetBuffer);
            if (!match.isMatched()) {
                profMatchRejects++;
                continue;
            }
            profMatchPasses++;

            if (isBlacklistedFull(slot, currentSettings)) {
                profFullBlacklistSkips++;
                continue;
            }

            ParsedPrice price = priceParser.parse(slot.getTooltipLines(), slot.getCount());
            if (!price.isFound()) {
                profPriceMissing++;
                continue;
            }

            if (!isPriceAllowed(target, price, currentSettings)) {
                profPriceRejected++;
                continue;
            }

            profAcceptedCandidates++;
            ScanCandidate candidate = new ScanCandidate(slot, target, price);
            if (best == null
                    || candidate.getPrice().getUnitPrice() < best.getPrice().getUnitPrice()
                    || (candidate.getPrice().getUnitPrice() == best.getPrice().getUnitPrice()
                    && candidate.getAuctionSlot().getAuctionIndex() < best.getAuctionSlot().getAuctionIndex())) {
                best = candidate;
            }
        }

        return best;
    }

    private void resetProfilerCounters() {
        profEmptySlots = 0;
        profFastBlacklistSkips = 0;
        profPrefilterRejects = 0;
        profPrefilterPasses = 0;
        profMatchRejects = 0;
        profMatchPasses = 0;
        profFullBlacklistSkips = 0;
        profPriceMissing = 0;
        profPriceRejected = 0;
        profAcceptedCandidates = 0;
    }

    private boolean isPriceAllowed(TargetItem target, ParsedPrice price, ScannerSettings currentSettings) {
        if (target == null || price == null || !price.isFound()) {
            return false;
        }

        long maxUnitPrice = target.getMaxUnitPrice();

        if (maxUnitPrice <= 0L) {
            // The critical safety rule:
            // An enabled target with no configured price must NOT be bought unless explicitly allowed.
            return !currentSettings.isRequireMaxPrice() && currentSettings.isAllowUnlimitedPrice();
        }

        return price.getUnitPrice() <= maxUnitPrice;
    }

    private List<TargetItem> fastCandidates(AuctionSlot slot) {
        candidateBuffer.clear();
        if (targets == null || targets.isEmpty() || slot == null || slot.isEmpty()) {
            return candidateBuffer;
        }

        String slotItemId = normalizeItemId(slot.getItemId());
        ArrayList<TargetItem> exactBucket = targetsByItemId.get(slotItemId);
        addFastCandidatesFromBucket(slot, exactBucket);
        addFastCandidatesFromBucket(slot, targetsWithoutItemId);

        return candidateBuffer;
    }

    private void addFastCandidatesFromBucket(AuctionSlot slot, List<TargetItem> bucket) {
        if (bucket == null || bucket.isEmpty()) {
            return;
        }
        for (TargetItem target : bucket) {
            if (matcher.isFastCandidate(slot, target)) {
                candidateBuffer.add(target);
            }
        }
    }

    private void rebuildTargetIndex() {
        targetsByItemId.clear();
        targetsWithoutItemId.clear();

        if (targets == null || targets.isEmpty()) {
            return;
        }

        for (TargetItem target : targets) {
            if (target == null || !target.isEnabled()) {
                continue;
            }

            String expectedItemId = matcher.expectedItemId(target);
            if (expectedItemId.isEmpty()) {
                targetsWithoutItemId.add(target);
                continue;
            }

            ArrayList<TargetItem> bucket = targetsByItemId.get(expectedItemId);
            if (bucket == null) {
                bucket = new ArrayList<TargetItem>(4);
                targetsByItemId.put(expectedItemId, bucket);
            }
            bucket.add(target);
        }
    }

    private String normalizeItemId(String value) {
        if (value == null) {
            return "";
        }
        String id = value.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return "";
        }
        if (id.indexOf(':') < 0) {
            return "minecraft:" + id;
        }
        return id;
    }

    private boolean isBlacklistedFast(AuctionSlot slot, ScannerSettings currentSettings) {
        List<String> blacklist = currentSettings.getBlacklistKeywords();
        if (blacklist == null || blacklist.isEmpty() || slot == null) {
            return false;
        }

        String haystack = (String.valueOf(slot.getDisplayName()) + " " + String.valueOf(slot.getItemId())).toLowerCase(Locale.ROOT);
        return containsBlacklist(haystack, blacklist);
    }

    private boolean isBlacklistedFull(AuctionSlot slot, ScannerSettings currentSettings) {
        List<String> blacklist = currentSettings.getBlacklistKeywords();
        if (blacklist == null || blacklist.isEmpty() || slot == null) {
            return false;
        }

        return containsBlacklist(buildSearchText(slot), blacklist);
    }

    private boolean containsBlacklist(String haystack, List<String> blacklist) {
        if (haystack == null || haystack.isEmpty() || blacklist == null || blacklist.isEmpty()) {
            return false;
        }

        String lower = haystack.toLowerCase(Locale.ROOT);
        for (String keyword : blacklist) {
            if (keyword != null && !keyword.trim().isEmpty() && lower.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String buildSearchText(AuctionSlot slot) {
        StringBuilder builder = new StringBuilder();

        builder.append(slot.getDisplayName()).append(' ');
        builder.append(slot.getItemId()).append(' ');
        builder.append(slot.getNbtString()).append(' ');

        List<String> tooltip = slot.getTooltipLines();
        if (tooltip != null) {
            for (String line : tooltip) {
                if (line != null) {
                    builder.append(line).append(' ');
                }
            }
        }

        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private int[] buildPriorityOrder(int size, ScanMode scanMode) {
        int limit = Math.min(size, (scanMode == null ? ScanMode.ALL45 : scanMode).getMaxSlots());
        if (limit <= 0) {
            return new int[0];
        }

        int[] result = new int[limit];
        int pos = 0;
        pos = addRange(result, pos, 0, 8, limit);
        pos = addRange(result, pos, 9, 17, limit);
        pos = addRange(result, pos, 18, 26, limit);
        addRange(result, pos, 27, limit - 1, limit);
        return result;
    }

    private int addRange(int[] order, int pos, int from, int to, int limit) {
        for (int i = from; i <= to && i < limit && pos < order.length; i++) {
            if (i >= 0) {
                order[pos++] = i;
            }
        }
        return pos;
    }

    public static List<TargetItem> defaultTargets() {
        List<TargetItem> list = new ArrayList<TargetItem>();

        list.add(new TargetItem(
                "Talisman Krushitelya",
                Arrays.asList("талисман крушителя", "crusher talisman"),
                0L,
                true
        ));

        list.add(new TargetItem(
                "Talisman Karatelya",
                Arrays.asList("талисман карателя", "punisher talisman"),
                0L,
                true
        ));

        list.add(new TargetItem(
                "Talisman Yarosti",
                Arrays.asList("талисман ярости", "rage talisman"),
                0L,
                true
        ));

        list.add(new TargetItem(
                "Sfera Titana",
                Arrays.asList("сфера титана", "titan sphere"),
                0L,
                true
        ));

        return list;
    }
}
