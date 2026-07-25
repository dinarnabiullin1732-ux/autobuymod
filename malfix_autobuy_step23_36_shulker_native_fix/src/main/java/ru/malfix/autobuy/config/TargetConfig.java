package ru.malfix.autobuy.config;

import ru.malfix.autobuy.scanner.TargetItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TargetConfig {

    private String label;
    private List<String> contains;
    private String itemId;
    private String tagContains;
    private long maxUnitPrice;
    private long sellUnitPrice;
    private boolean enabled;
    private boolean unstack;
    private int unstackAmount;
    /**
     * Step 22.59: used only by potion LMB-drag unstack.
     * The potion stack is treated as a drag-unstack source only when its count is
     * at least this value. This prevents already distributed 8-16 item piles from
     * being dragged again after all full stacks were processed.
     */
    private int potionDragMinSourceCount;
    private boolean parserEnabled;

    public TargetConfig(String label, List<String> contains, long maxUnitPrice, boolean enabled) {
        this(label, contains, "", "", maxUnitPrice, 0L, enabled, false, 1);
    }

    public TargetConfig(String label, List<String> contains, String itemId, String tagContains, long maxUnitPrice, boolean enabled) {
        this(label, contains, itemId, tagContains, maxUnitPrice, 0L, enabled, false, 1, false);
    }

    public TargetConfig(String label, List<String> contains, String itemId, String tagContains, long maxUnitPrice, long sellUnitPrice, boolean enabled, boolean unstack, int unstackAmount) {
        this(label, contains, itemId, tagContains, maxUnitPrice, sellUnitPrice, enabled, unstack, unstackAmount, 24, false);
    }

    public TargetConfig(String label, List<String> contains, String itemId, String tagContains, long maxUnitPrice, long sellUnitPrice, boolean enabled, boolean unstack, int unstackAmount, boolean parserEnabled) {
        this(label, contains, itemId, tagContains, maxUnitPrice, sellUnitPrice, enabled, unstack, unstackAmount, 24, parserEnabled);
    }

    public TargetConfig(String label, List<String> contains, String itemId, String tagContains, long maxUnitPrice, long sellUnitPrice, boolean enabled, boolean unstack, int unstackAmount, int potionDragMinSourceCount, boolean parserEnabled) {
        this.label = label == null ? "" : label;
        this.contains = contains == null ? new ArrayList<String>() : new ArrayList<String>(contains);
        this.itemId = itemId == null ? "" : itemId.trim();
        this.tagContains = tagContains == null ? "" : tagContains.trim();
        this.maxUnitPrice = Math.max(0L, maxUnitPrice);
        this.sellUnitPrice = Math.max(0L, sellUnitPrice);
        this.enabled = enabled;
        this.unstack = unstack;
        this.unstackAmount = sanitizeUnstackAmount(unstackAmount);
        this.potionDragMinSourceCount = sanitizePotionDragMinSourceCount(potionDragMinSourceCount);
        this.parserEnabled = parserEnabled;
    }

    public static TargetConfig of(String label, List<String> contains, long maxUnitPrice) {
        return new TargetConfig(label, contains, "", "", maxUnitPrice, 0L, true, false, 1, false);
    }

    public TargetItem toTargetItem() {
        return new TargetItem(label, contains, itemId, tagContains, maxUnitPrice, sellUnitPrice, enabled, unstack, unstackAmount, potionDragMinSourceCount);
    }

    public String getLabel() {
        return label;
    }

    public List<String> getContains() {
        return Collections.unmodifiableList(contains);
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

    public boolean isParserEnabled() {
        return parserEnabled;
    }

    public void setParserEnabled(boolean parserEnabled) {
        this.parserEnabled = parserEnabled;
    }

    public void setMaxUnitPrice(long maxUnitPrice) {
        this.maxUnitPrice = Math.max(0L, maxUnitPrice);
    }

    public void setSellUnitPrice(long sellUnitPrice) {
        this.sellUnitPrice = Math.max(0L, sellUnitPrice);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId == null ? "" : itemId.trim();
    }

    public void setTagContains(String tagContains) {
        this.tagContains = tagContains == null ? "" : tagContains.trim();
    }

    public void setUnstack(boolean unstack) {
        this.unstack = unstack;
    }

    public void setUnstackAmount(int unstackAmount) {
        this.unstackAmount = sanitizeUnstackAmount(unstackAmount);
    }

    public void setPotionDragMinSourceCount(int potionDragMinSourceCount) {
        this.potionDragMinSourceCount = sanitizePotionDragMinSourceCount(potionDragMinSourceCount);
    }

    public boolean matchesLabel(String value) {
        if (value == null) {
            return false;
        }
        return label.equalsIgnoreCase(value.trim());
    }

    public String compact() {
        return "label=" + label
                + ", enabled=" + enabled
                + ", buyPrice=" + maxUnitPrice
                + ", sellPrice=" + sellUnitPrice
                + ", unstack=" + unstack
                + ", unstackAmount=" + unstackAmount
                + ", potionDragMinSourceCount=" + potionDragMinSourceCount
                + ", parserEnabled=" + parserEnabled
                + ", itemId=" + itemId
                + ", tag=" + tagContains
                + ", contains=" + contains;
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
