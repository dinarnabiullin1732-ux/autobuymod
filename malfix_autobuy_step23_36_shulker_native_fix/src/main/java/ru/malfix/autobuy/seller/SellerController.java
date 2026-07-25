package ru.malfix.autobuy.seller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import ru.malfix.autobuy.mc.McItemStacks;
import ru.malfix.autobuy.scanner.MatchResult;
import ru.malfix.autobuy.scanner.TargetItem;
import ru.malfix.autobuy.profiler.MalfixProfiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SellerController {

    private final MinecraftClient client;
    private final InventoryItemMatcher matcher;
    private final SellCommandBuilder commandBuilder;

    private List<TargetItem> targets;
    private int sellerMarkupPercent = 10;
    private SellerResult lastResult = SellerResult.idle();

    public SellerController(MinecraftClient client, List<TargetItem> targets) {
        this.client = client;
        this.targets = targets == null ? Collections.<TargetItem>emptyList() : new ArrayList<TargetItem>(targets);
        this.matcher = new InventoryItemMatcher();
        this.commandBuilder = new SellCommandBuilder();
    }

    public void setTargets(List<TargetItem> targets) {
        this.targets = targets == null ? Collections.<TargetItem>emptyList() : new ArrayList<TargetItem>(targets);
    }

    public void setSellerMarkupPercent(int sellerMarkupPercent) {
        if (sellerMarkupPercent < 0) {
            this.sellerMarkupPercent = 0;
        } else if (sellerMarkupPercent > 500) {
            this.sellerMarkupPercent = 500;
        } else {
            this.sellerMarkupPercent = sellerMarkupPercent;
        }
    }

    public int getSellerMarkupPercent() {
        return sellerMarkupPercent;
    }

    /**
     * Safe seller preview.
     * Does NOT move items, does NOT send /ah sell.
     *
     * Step 17:
     * - sell command uses sellUnitPrice from GUI only.
     * - if sellUnitPrice == 0, seller must not sell this item.
     * - hand dry-run reports whether the item is already selected or can be selected directly from hotbar.
     */
    public SellerResult previewNextSell() {
        long profStart = MalfixProfiler.start();
        int checkedBefore = -1;
        try {
            SellerResult result = previewNextSellInternal();
            checkedBefore = extractCheckedSlots(result);
            return result;
        } finally {
            MalfixProfiler.recordInventoryPreview(profStart, checkedBefore < 0 ? 0 : checkedBefore);
        }
    }

    private SellerResult previewNextSellInternal() {
        try {
            if (client == null || client.player == null) {
                lastResult = SellerResult.noPlayer();
                return lastResult;
            }

            if (!hasEnabledTargetsWithSellPrice()) {
                lastResult = SellerResult.noTargets();
                return lastResult;
            }

            int checked = 0;

            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = client.player.getInventory().getStack(slot);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                checked++;

                // Step 22.75: never auto-sell storage containers. A shulker box may
                // contain a configured item such as silver in its BlockEntityTag, and
                // the matcher must not treat that inner item as the stack being sold.
                if (isStorageContainerItemId(safeItemId(stack))) {
                    continue;
                }

                // Step 22.34: seller hot path for 2-3 launchers.
                // Most items can be matched by id/name only. Full tooltip/NBT reading is much
                // heavier and used only when the cheap pass does not find a sellable target.
                InventoryItemSnapshot item = snapshotBasic(slot, stack);
                MatchResult match = matcher.match(item, targets);

                if ((!match.isMatched() || match.getTarget() == null) && shouldUseFullSnapshot(stack)) {
                    // Step 22.39: old Spooky-style seller should not build expensive tooltip
                    // snapshots on every sell step. For selling, display name + item id + NBT
                    // is enough for our configured targets; tooltip rendering is one of the
                    // biggest FPS spikes when 2-3 launchers sell at the same time.
                    item = snapshotNbtOnly(slot, stack);
                    match = matcher.match(item, targets);
                }

                if (!match.isMatched() || match.getTarget() == null) {
                    continue;
                }

                TargetItem target = match.getTarget();

                if (target.getSellUnitPrice() <= 0L) {
                    continue;
                }

                long sellUnitPrice = target.getSellUnitPrice();
                long totalPrice = safeMultiply(sellUnitPrice, Math.max(1, item.getCount()));
                String command = commandBuilder.build(totalPrice);

                int selectedHotbarSlot = getSelectedHotbarSlot();
                boolean itemInHotbar = slot >= 0 && slot <= 8;
                boolean selectedHandMatches = selectedHotbarSlot == slot;
                boolean directHotbarSelectPossible = itemInHotbar && !selectedHandMatches;
                String handPlan = buildHandPlan(slot, selectedHotbarSlot, itemInHotbar, selectedHandMatches);

                lastResult = SellerResult.found(
                        item,
                        target,
                        sellUnitPrice,
                        totalPrice,
                        command,
                        selectedHotbarSlot,
                        itemInHotbar,
                        selectedHandMatches,
                        directHotbarSelectPossible,
                        handPlan
                );
                return lastResult;
            }

            lastResult = SellerResult.noMatch(checked);
            return lastResult;
        } catch (Throwable throwable) {
            lastResult = SellerResult.error(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            return lastResult;
        }
        }


    public SellerResult getLastResult() {
        return lastResult;
    }

    private int extractCheckedSlots(SellerResult result) {
        if (result == null) {
            return 0;
        }
        if (result.getItem() != null) {
            return Math.max(1, result.getItem().getInventorySlot() + 1);
        }
        String message = result.getMessage();
        if (message == null) {
            return 0;
        }
        int idx = message.indexOf("checked=");
        if (idx < 0) {
            return 0;
        }
        String raw = message.substring(idx + "checked=".length()).trim();
        int end = 0;
        while (end < raw.length() && raw.charAt(end) >= '0' && raw.charAt(end) <= '9') {
            end++;
        }
        if (end <= 0) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.substring(0, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean shouldUseFullSnapshot(ItemStack stack) {
        if (targets == null || targets.isEmpty() || stack == null || stack.isEmpty()) {
            return false;
        }
        String componentText = McItemStacks.componentString(stack);
        boolean stackHasCustomData = componentText != null && !componentText.isEmpty();
        for (TargetItem target : targets) {
            if (target == null || !target.isEnabled() || target.getSellUnitPrice() <= 0L) {
                continue;
            }
            if ((target.requiresTag() || target.requiresItemId()) && stackHasCustomData) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEnabledTargetsWithSellPrice() {
        if (targets == null || targets.isEmpty()) {
            return false;
        }

        for (TargetItem target : targets) {
            if (target != null && target.isEnabled() && target.getSellUnitPrice() > 0L) {
                return true;
            }
        }

        return false;
    }

    private int getSelectedHotbarSlot() {
        try {
            return client.player.getInventory().selectedSlot;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private String buildHandPlan(int inventorySlot, int selectedHotbarSlot, boolean itemInHotbar, boolean selectedHandMatches) {
        if (selectedHandMatches) {
            return "READY_IN_HAND";
        }

        if (itemInHotbar) {
            return "CAN_SELECT_HOTBAR_" + inventorySlot;
        }

        return "NEEDS_MOVE_TO_HOTBAR_FROM_SLOT_" + inventorySlot;
    }

    private long applySellerMarkup(long buyMaxUnitPrice) {
        if (buyMaxUnitPrice <= 0L) {
            return 0L;
        }

        long multiplier = 100L + (long) sellerMarkupPercent;

        if (buyMaxUnitPrice > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }

        long value = buyMaxUnitPrice * multiplier;
        long result = value / 100L;

        if (value % 100L != 0L) {
            result++;
        }

        return Math.max(buyMaxUnitPrice, result);
    }

    private long safeMultiply(long unitPrice, int count) {
        if (unitPrice <= 0L || count <= 0) {
            return 0L;
        }

        if (unitPrice > Long.MAX_VALUE / count) {
            return Long.MAX_VALUE;
        }

        return unitPrice * (long) count;
    }

    private InventoryItemSnapshot snapshotBasic(int inventorySlot, ItemStack stack) {
        return new InventoryItemSnapshot(
                inventorySlot,
                safeItemId(stack),
                stack.getName().getString(),
                stack.getCount(),
                Collections.<String>emptyList(),
                ""
        );
    }

    private InventoryItemSnapshot snapshotNbtOnly(int inventorySlot, ItemStack stack) {
        return new InventoryItemSnapshot(
                inventorySlot,
                safeItemId(stack),
                stack.getName().getString(),
                stack.getCount(),
                Collections.<String>emptyList(),
                readNbt(stack)
        );
    }

    private InventoryItemSnapshot snapshotFull(int inventorySlot, ItemStack stack) {
        return new InventoryItemSnapshot(
                inventorySlot,
                safeItemId(stack),
                stack.getName().getString(),
                stack.getCount(),
                readTooltip(stack),
                readNbt(stack)
        );
    }

    private String safeItemId(ItemStack stack) {
        try {
            return McItemStacks.itemId(stack);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean isStorageContainerItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        String id = itemId.toLowerCase();
        return id.contains("shulker_box") && !id.contains("spawn_egg");
    }

    private String readNbt(ItemStack stack) {
        long profStart = MalfixProfiler.start();
        try {
            return McItemStacks.componentString(stack);
        } finally {
            MalfixProfiler.recordNbt(profStart);
        }
    }

    private List<String> readTooltip(ItemStack stack) {
        long profStart = MalfixProfiler.start();
        try {
            return McItemStacks.tooltip(stack, client);
        } finally {
            MalfixProfiler.recordTooltip(profStart);
        }
    }

}
