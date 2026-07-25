package ru.malfix.autobuy.seller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InventoryItemSnapshot {

    private final int inventorySlot;
    private final String itemId;
    private final String displayName;
    private final int count;
    private final List<String> tooltipLines;
    private final String nbtString;

    public InventoryItemSnapshot(
            int inventorySlot,
            String itemId,
            String displayName,
            int count,
            List<String> tooltipLines,
            String nbtString
    ) {
        this.inventorySlot = inventorySlot;
        this.itemId = itemId == null ? "" : itemId;
        this.displayName = displayName == null ? "" : displayName;
        this.count = Math.max(0, count);
        this.tooltipLines = tooltipLines == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(tooltipLines));
        this.nbtString = nbtString == null ? "" : nbtString;
    }

    public int getInventorySlot() {
        return inventorySlot;
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
        return tooltipLines;
    }

    public String getNbtString() {
        return nbtString;
    }

    public String compact() {
        return "slot=" + inventorySlot
                + ", item=" + displayName
                + ", id=" + itemId
                + ", count=" + count;
    }
}
