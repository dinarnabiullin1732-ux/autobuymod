package ru.malfix.autobuy.seller;

import ru.malfix.autobuy.scanner.TargetItem;

public final class SellerResult {

    public enum Status {
        IDLE,
        FOUND_PREVIEW,
        NO_MATCH,
        NO_TARGETS,
        NO_PLAYER,
        ERROR
    }

    private final Status status;
    private final String message;
    private final InventoryItemSnapshot item;
    private final TargetItem target;
    private final long unitPrice;
    private final long totalPrice;
    private final String command;
    private final int selectedHotbarSlot;
    private final boolean itemInHotbar;
    private final boolean selectedHandMatches;
    private final boolean directHotbarSelectPossible;
    private final String handPlan;
    private final long createdAtMs;

    private SellerResult(
            Status status,
            String message,
            InventoryItemSnapshot item,
            TargetItem target,
            long unitPrice,
            long totalPrice,
            String command,
            int selectedHotbarSlot,
            boolean itemInHotbar,
            boolean selectedHandMatches,
            boolean directHotbarSelectPossible,
            String handPlan
    ) {
        this.status = status == null ? Status.ERROR : status;
        this.message = message == null ? "" : message;
        this.item = item;
        this.target = target;
        this.unitPrice = Math.max(0L, unitPrice);
        this.totalPrice = Math.max(0L, totalPrice);
        this.command = command == null ? "" : command;
        this.selectedHotbarSlot = selectedHotbarSlot;
        this.itemInHotbar = itemInHotbar;
        this.selectedHandMatches = selectedHandMatches;
        this.directHotbarSelectPossible = directHotbarSelectPossible;
        this.handPlan = handPlan == null ? "" : handPlan;
        this.createdAtMs = System.currentTimeMillis();
    }

    public static SellerResult idle() {
        return new SellerResult(Status.IDLE, "idle", null, null, 0L, 0L, "", -1, false, false, false, "");
    }

    public static SellerResult noPlayer() {
        return new SellerResult(Status.NO_PLAYER, "player_not_available", null, null, 0L, 0L, "", -1, false, false, false, "");
    }

    public static SellerResult noTargets() {
        return new SellerResult(Status.NO_TARGETS, "no_enabled_targets_with_price", null, null, 0L, 0L, "", -1, false, false, false, "");
    }

    public static SellerResult noMatch(int checked) {
        return new SellerResult(Status.NO_MATCH, "no_matching_inventory_item, checked=" + checked, null, null, 0L, 0L, "", -1, false, false, false, "");
    }

    public static SellerResult found(
            InventoryItemSnapshot item,
            TargetItem target,
            long unitPrice,
            long totalPrice,
            String command,
            int selectedHotbarSlot,
            boolean itemInHotbar,
            boolean selectedHandMatches,
            boolean directHotbarSelectPossible,
            String handPlan
    ) {
        return new SellerResult(
                Status.FOUND_PREVIEW,
                "found_preview",
                item,
                target,
                unitPrice,
                totalPrice,
                command,
                selectedHotbarSlot,
                itemInHotbar,
                selectedHandMatches,
                directHotbarSelectPossible,
                handPlan
        );
    }

    public static SellerResult error(String message) {
        return new SellerResult(Status.ERROR, message, null, null, 0L, 0L, "", -1, false, false, false, "");
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public InventoryItemSnapshot getItem() {
        return item;
    }

    public TargetItem getTarget() {
        return target;
    }

    /**
     * Price from GUI for one item.
     */
    public long getUnitPrice() {
        return unitPrice;
    }

    /**
     * Total /ah sell price for the whole stack.
     */
    public long getTotalPrice() {
        return totalPrice;
    }

    /**
     * Backward-compatible alias. In Step 16.1 this means total stack price.
     */
    public long getSellPrice() {
        return totalPrice;
    }

    public String getCommand() {
        return command;
    }

    public int getSelectedHotbarSlot() {
        return selectedHotbarSlot;
    }

    public boolean isItemInHotbar() {
        return itemInHotbar;
    }

    public boolean isSelectedHandMatches() {
        return selectedHandMatches;
    }

    public boolean isDirectHotbarSelectPossible() {
        return directHotbarSelectPossible;
    }

    public String getHandPlan() {
        return handPlan;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public boolean hasFoundItem() {
        return status == Status.FOUND_PREVIEW && item != null && target != null;
    }

    public String compact() {
        return "status=" + status
                + ", msg=" + message
                + ", item=" + (item == null ? "none" : item.compact())
                + ", target=" + (target == null ? "none" : target.getLabel())
                + ", unitPrice=" + unitPrice
                + ", totalPrice=" + totalPrice
                + ", command=" + command
                + ", handPlan=" + handPlan
                + ", selectedHotbarSlot=" + selectedHotbarSlot
                + ", itemInHotbar=" + itemInHotbar
                + ", selectedHandMatches=" + selectedHandMatches
                + ", directHotbarSelectPossible=" + directHotbarSelectPossible;
    }
}
