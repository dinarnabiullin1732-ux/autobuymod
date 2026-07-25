package ru.malfix.autobuy.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TargetItem {

    private final String label;
    private final List<String> nameContains;
    private final String itemId;
    private final String tagContains;
    private final long maxUnitPrice;
    private final long sellUnitPrice;
    private final boolean enabled;
    private final boolean unstack;
    private final int unstackAmount;
    private final int potionDragMinSourceCount;

    public TargetItem(String label, List<String> nameContains, long maxUnitPrice, boolean enabled) {
        this(label, nameContains, "", "", maxUnitPrice, 0L, enabled, false, 1);
    }

    public TargetItem(String label, List<String> nameContains, String itemId, String tagContains, long maxUnitPrice, boolean enabled) {
        this(label, nameContains, itemId, tagContains, maxUnitPrice, 0L, enabled, false, 1);
    }

    public TargetItem(String label, List<String> nameContains, String itemId, String tagContains, long maxUnitPrice, long sellUnitPrice, boolean enabled, boolean unstack, int unstackAmount) {
        this(label, nameContains, itemId, tagContains, maxUnitPrice, sellUnitPrice, enabled, unstack, unstackAmount, 24);
    }

    public TargetItem(String label, List<String> nameContains, String itemId, String tagContains, long maxUnitPrice, long sellUnitPrice, boolean enabled, boolean unstack, int unstackAmount, int potionDragMinSourceCount) {
        this.label = label == null ? "" : label;
        this.nameContains = nameContains == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(nameContains));
        this.itemId = itemId == null ? "" : itemId.trim().toLowerCase();
        this.tagContains = tagContains == null ? "" : tagContains.trim().toLowerCase();
        this.maxUnitPrice = Math.max(0L, maxUnitPrice);
        this.sellUnitPrice = Math.max(0L, sellUnitPrice);
        this.enabled = enabled;
        this.unstack = unstack;
        this.unstackAmount = sanitizeUnstackAmount(unstackAmount);
        this.potionDragMinSourceCount = sanitizePotionDragMinSourceCount(potionDragMinSourceCount);
    }

    public String getLabel() {
        return label;
    }

    public List<String> getNameContains() {
        return nameContains;
    }

    public String getItemId() {
        return itemId;
    }

    public String getTagContains() {
        return tagContains;
    }

    public long getMaxUnitPrice() {
        return maxUnitPrice;
    }

    public long getSellUnitPrice() {
        return sellUnitPrice;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isUnstack() {
        return unstack;
    }

    public int getUnstackAmount() {
        return unstackAmount;
    }

    public int getPotionDragMinSourceCount() {
        return potionDragMinSourceCount;
    }

    public boolean requiresItemId() {
        return !itemId.isEmpty();
    }

    public boolean requiresTag() {
        return !tagContains.isEmpty();
    }

    private int sanitizeUnstackAmount(int value) {
        if (value < 1) {
            return 1;
        }
        if (value > 64) {
            return 64;
        }
        return value;
    }

    private int sanitizePotionDragMinSourceCount(int value) {
        if (value < 1) {
            return 1;
        }
        if (value > 64) {
            return 64;
        }
        return value;
    }
}
