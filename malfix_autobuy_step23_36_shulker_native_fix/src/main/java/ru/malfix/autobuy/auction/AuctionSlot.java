package ru.malfix.autobuy.auction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import ru.malfix.autobuy.profiler.MalfixProfiler;

public final class AuctionSlot {

    private final int auctionIndex;
    private final int containerSlotId;
    private final boolean empty;
    private final String itemId;
    private final String displayName;
    private final int count;
    private final Supplier<List<String>> tooltipSupplier;
    private final Supplier<String> nbtSupplier;
    private List<String> tooltipLines;
    private String nbtString;

    public AuctionSlot(
            int auctionIndex,
            int containerSlotId,
            boolean empty,
            String itemId,
            String displayName,
            int count,
            List<String> tooltipLines
    ) {
        this(auctionIndex, containerSlotId, empty, itemId, displayName, count, tooltipLines, "");
    }

    public AuctionSlot(
            int auctionIndex,
            int containerSlotId,
            boolean empty,
            String itemId,
            String displayName,
            int count,
            List<String> tooltipLines,
            String nbtString
    ) {
        this(
                auctionIndex,
                containerSlotId,
                empty,
                itemId,
                displayName,
                count,
                constantTooltip(tooltipLines),
                constantString(nbtString)
        );
    }

    public AuctionSlot(
            int auctionIndex,
            int containerSlotId,
            boolean empty,
            String itemId,
            String displayName,
            int count,
            Supplier<List<String>> tooltipSupplier,
            Supplier<String> nbtSupplier
    ) {
        this.auctionIndex = auctionIndex;
        this.containerSlotId = containerSlotId;
        this.empty = empty;
        this.itemId = safe(itemId);
        this.displayName = safe(displayName);
        this.count = Math.max(0, count);
        this.tooltipSupplier = tooltipSupplier == null ? constantTooltip(Collections.<String>emptyList()) : tooltipSupplier;
        this.nbtSupplier = nbtSupplier == null ? constantString("") : nbtSupplier;
    }

    public static AuctionSlot empty(int auctionIndex, int containerSlotId) {
        return new AuctionSlot(
                auctionIndex,
                containerSlotId,
                true,
                "",
                "",
                0,
                Collections.<String>emptyList(),
                ""
        );
    }

    public int getAuctionIndex() {
        return auctionIndex;
    }

    public int getContainerSlotId() {
        return containerSlotId;
    }

    public boolean isEmpty() {
        return empty;
    }

    public String getItemId() {
        return itemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCount() {
        return count;
    }

    public List<String> getTooltipLines() {
        if (tooltipLines == null) {
            long profStart = MalfixProfiler.start();
            tooltipLines = freezeTooltip(safeGetTooltip());
            MalfixProfiler.recordTooltip(profStart);
        }
        return tooltipLines;
    }

    public String getNbtString() {
        if (nbtString == null) {
            long profStart = MalfixProfiler.start();
            nbtString = safeString(safeGetNbt());
            MalfixProfiler.recordNbt(profStart);
        }
        return nbtString;
    }

    /**
     * Fast identity hash for page-change detection. Do not force tooltip/NBT here:
     * tooltip rendering and SNBT serialization are the expensive scanner path.
     */
    public int stableHash() {
        int result = auctionIndex;
        result = 31 * result + containerSlotId;
        result = 31 * result + (empty ? 1 : 0);
        result = 31 * result + itemId.hashCode();
        result = 31 * result + displayName.hashCode();
        result = 31 * result + count;
        return result;
    }

    private List<String> safeGetTooltip() {
        try {
            List<String> lines = tooltipSupplier.get();
            return lines == null ? Collections.<String>emptyList() : lines;
        } catch (Throwable ignored) {
            return Collections.<String>emptyList();
        }
    }

    private String safeGetNbt() {
        try {
            return nbtSupplier.get();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Supplier<List<String>> constantTooltip(final List<String> tooltipLines) {
        final List<String> frozen = freezeTooltip(tooltipLines);
        return new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return frozen;
            }
        };
    }

    private static Supplier<String> constantString(final String value) {
        final String safeValue = safeString(value);
        return new Supplier<String>() {
            @Override
            public String get() {
                return safeValue;
            }
        };
    }

    private static List<String> freezeTooltip(List<String> tooltipLines) {
        return tooltipLines == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(tooltipLines));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
