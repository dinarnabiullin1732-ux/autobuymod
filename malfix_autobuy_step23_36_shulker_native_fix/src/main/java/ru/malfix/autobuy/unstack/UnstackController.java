package ru.malfix.autobuy.unstack;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import ru.malfix.autobuy.mc.McItemStacks;
import ru.malfix.autobuy.scanner.MatchResult;
import ru.malfix.autobuy.scanner.TargetItem;
import ru.malfix.autobuy.seller.InventoryItemMatcher;
import ru.malfix.autobuy.seller.InventoryItemSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Step 22.19: old-SpookyBuy-style unstack/restack before seller.
 *
 * Logic copied by behavior from UnstackHandler in the uploaded old autobuy:
 * - every ~250ms;
 * - works only when cursor stack is empty;
 * - finds configured item with unstack enabled and stack count > configured amount;
 * - picks the stack, right-clicks an empty slot N times, then returns the remainder.
 */
public final class UnstackController {

    /**
     * Step 22.57: some SpookyTime potion stacks are server-protected against the
     * old right-click split method, but accept vanilla left-drag distribution.
     * For the special effect potion target, unstackAmount means "how many slots
     * to drag across" instead of "how many items to place into one slot".
     */
    private static final int POTION_DRAG_DEFAULT_SLOTS = 6;
    private static final int POTION_DRAG_MIN_SLOTS = 2;
    private static final int POTION_DRAG_MAX_SLOTS = 16;
    // Step 22.62: faster staged drag for potions, but still not one-tick spam.
    // Step 22.57 one-tick QUICK_CRAFT was rejected partially by the server; keep staged
    // slot-by-slot drag and only reduce the safe gaps.
    private static final long POTION_DRAG_STEP_DELAY_MS = 45L;
    private static final long POTION_DRAG_SETTLE_DELAY_MS = 80L;
    private static final long POTION_DRAG_NEXT_STACK_DELAY_MS = 80L;
    private static final int POTION_DRAG_CURSOR_PLACE_RETRIES = 6;

    private final MinecraftClient client;
    private final InventoryItemMatcher matcher = new InventoryItemMatcher();
    private List<TargetItem> targets = Collections.emptyList();
    private long delayMs = 400L;
    private long nextAtMs = 0L;
    private String lastStatus = "idle";
    private long lastActionAtMs = 0L;
    private int lastSourceSlot = -1;
    private int lastDestSlot = -1;
    private int lastAmount = 0;
    private int actionsDone = 0;
    private PotionDragState activePotionDrag = null;

    public UnstackController(MinecraftClient client, List<TargetItem> targets) {
        this.client = client;
        setTargets(targets);
    }

    public void setTargets(List<TargetItem> targets) {
        this.targets = targets == null ? Collections.<TargetItem>emptyList() : new ArrayList<TargetItem>(targets);
    }

    public void setDelayMs(long delayMs) {
        if (delayMs < 100L) {
            this.delayMs = 100L;
        } else if (delayMs > 2_000L) {
            this.delayMs = 2_000L;
        } else {
            this.delayMs = delayMs;
        }
    }

    public long getDelayMs() {
        return delayMs;
    }

    public boolean needsUnstack() {
        try {
            if (activePotionDrag != null) {
                return true;
            }

            if (!isReady() || !hasEnabledUnstackTargets()) {
                return false;
            }

            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = client.player.getInventory().getStack(slot);
                if (isEmpty(stack) || isShulker(stack)) {
                    continue;
                }

                TargetItem target = findUnstackTarget(slot, stack);
                if (target == null) {
                    continue;
                }

                if (isPotionDragUnstackTarget(target, stack)) {
                    int minSourceCount = getPotionDragMinSourceCount(target);
                    if (stack.getCount() >= minSourceCount && countEmptyInventorySlotsExcept(slot) > 0) {
                        return true;
                    }
                    continue;
                }

                int amount = target.getUnstackAmount();
                if (amount > 0 && stack.getCount() > amount) {
                    return true;
                }
            }
        } catch (Throwable throwable) {
            lastStatus = "needs_error:" + throwable.getClass().getSimpleName() + ":" + safeMsg(throwable);
        }

        return false;
    }

    public boolean canUnstackNow() {
        if (activePotionDrag != null) {
            return isReady();
        }

        // Same condition as old SpookyBuy: at least one empty inventory slot.
        // The cursor check is done inside tick(), exactly like the old handler.
        return isReady() && getEmptyInventorySlot() >= 0;
    }

    /**
     * @return true if seller should wait this tick, false if no unstack work is pending/possible.
     */
    public boolean tick() {
        long now = System.currentTimeMillis();

        if (activePotionDrag != null) {
            if (now < nextAtMs) {
                return true;
            }

            continuePotionDrag(now);
            lastActionAtMs = now;
            return true;
        }

        if (now < nextAtMs) {
            return needsUnstack();
        }

        if (!isReady()) {
            lastStatus = "not_ready";
            return false;
        }

        if (!hasEnabledUnstackTargets()) {
            lastStatus = "no_unstack_targets";
            return false;
        }

        if (!isCursorEmpty()) {
            if (tryPlaceLooseCursorIntoEmptySlot()) {
                lastStatus = "cursor_recovered_to_empty_slot";
            } else {
                lastStatus = "cursor_not_empty";
            }
            nextAtMs = now + delayMs;
            return true;
        }

        UnstackPlan plan = findPlan();
        if (plan == null) {
            lastStatus = "nothing_to_unstack";
            return false;
        }

        if (!plan.potionDrag && plan.destInventorySlot < 0) {
            // Matches old behavior: if there is no empty inventory slot, normal right-click unstack cannot run.
            lastStatus = "no_empty_slot";
            return false;
        }

        boolean ok = executePlan(plan);
        lastActionAtMs = now;

        if (plan.potionDrag) {
            if (!ok) {
                nextAtMs = now + delayMs;
            }
            return true;
        }

        nextAtMs = now + delayMs;

        if (ok) {
            actionsDone++;
            lastSourceSlot = plan.sourceInventorySlot;
            lastDestSlot = plan.destInventorySlot;
            lastAmount = plan.amount;
            lastStatus = "unstacked:" + plan.targetLabel
                    + ":src=" + plan.sourceInventorySlot
                    + ":dst=" + plan.destInventorySlot
                    + ":amount=" + plan.amount
                    + ":before=" + plan.sourceCount;
            return true;
        }

        return true;
    }

    public String compact() {
        long delayLeft = Math.max(0L, nextAtMs - System.currentTimeMillis());
        long lastAgo = lastActionAtMs <= 0L ? -1L : Math.max(0L, System.currentTimeMillis() - lastActionAtMs);
        return "status=" + lastStatus
                + ", delayMs=" + delayMs
                + ", potionDragStepMs=" + POTION_DRAG_STEP_DELAY_MS
                + ", potionNextStackMs=" + POTION_DRAG_NEXT_STACK_DELAY_MS
                + ", delayLeftMs=" + delayLeft
                + ", actions=" + actionsDone
                + ", lastSource=" + lastSourceSlot
                + ", lastDest=" + lastDestSlot
                + ", lastAmount=" + lastAmount
                + ", lastAgoMs=" + lastAgo
                + ", needs=" + needsUnstack()
                + ", canNow=" + canUnstackNow();
    }

    private UnstackPlan findPlan() {
        int emptySlot = getEmptyInventorySlot();
        UnstackPlan bestPotionPlan = null;
        UnstackPlan firstNormalPlan = null;

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (isEmpty(stack) || isShulker(stack)) {
                continue;
            }

            TargetItem target = findUnstackTarget(slot, stack);
            if (target == null) {
                continue;
            }

            if (isPotionDragUnstackTarget(target, stack)) {
                int minSourceCount = getPotionDragMinSourceCount(target);
                if (stack.getCount() < minSourceCount) {
                    continue;
                }

                List<Integer> dragSlots = buildPotionDragInventorySlots(slot, stack.getCount(), target.getUnstackAmount());
                if (dragSlots.size() >= POTION_DRAG_MIN_SLOTS) {
                    UnstackPlan potionPlan = UnstackPlan.potionDrag(slot, dragSlots, stack.getCount(), target.getLabel(), minSourceCount);
                    if (bestPotionPlan == null || potionPlan.sourceCount > bestPotionPlan.sourceCount) {
                        bestPotionPlan = potionPlan;
                    }
                }
                continue;
            }

            int amount = target.getUnstackAmount();
            if (amount <= 0 || stack.getCount() <= amount) {
                continue;
            }

            if (firstNormalPlan == null) {
                firstNormalPlan = UnstackPlan.rightClick(slot, emptySlot, amount, stack.getCount(), target.getLabel());
            }
        }

        // For potions choose the largest matching stack first. Otherwise a previously
        // created 10-16 item potion pile may be processed before a full 64-stack.
        if (bestPotionPlan != null) {
            return bestPotionPlan;
        }

        return firstNormalPlan;
    }

    private boolean executePlan(UnstackPlan plan) {
        try {
            if (client.interactionManager == null || client.player == null) {
                lastStatus = "no_interaction_manager";
                return false;
            }

            if (client.currentScreen != null && !isChatScreenOpen()) {
                closeHandledScreenForUnstack();
                lastStatus = "close_screen_before_unstack:" + safeScreenName();
                return false;
            }

            ScreenHandler handler = client.player.currentScreenHandler;
            if (handler == null) {
                lastStatus = "no_screen_handler";
                return false;
            }

            // Old SpookyBuy clicks player inventory handler id 0. The important missing part
            // is that the handled container must be closed with player.closeHandledScreen(),
            // not only visually hidden with openScreen(null), otherwise slot 36..44 can still
            // point to the previous auction/storage handler and pickup stays empty forever.
            if (handler.syncId != 0) {
                closeHandledScreenForUnstack();
                lastStatus = "close_handler_before_unstack:syncId=" + handler.syncId;
                return false;
            }

            int syncId = 0;
            int sourceScreenSlot = inventorySlotToPlayerScreenSlot(plan.sourceInventorySlot);
            int destScreenSlot = inventorySlotToPlayerScreenSlot(plan.destInventorySlot);

            if (plan.potionDrag) {
                return executePotionDragPlan(syncId, plan, sourceScreenSlot);
            }

            if (sourceScreenSlot < 0 || destScreenSlot < 0) {
                lastStatus = "bad_screen_slots:src=" + sourceScreenSlot + ":dst=" + destScreenSlot;
                return false;
            }

            // Pick up the whole source stack.
            click(syncId, sourceScreenSlot, 0, SlotActionType.PICKUP);

            if (isCursorEmpty()) {
                lastStatus = "pickup_failed:src=" + plan.sourceInventorySlot;
                return false;
            }

            // Put exactly amount items into empty slot by right-clicking it.
            for (int i = 0; i < plan.amount; i++) {
                click(syncId, destScreenSlot, 1, SlotActionType.PICKUP);
            }

            // Return the rest of the source stack back to its original slot.
            if (!isCursorEmpty()) {
                click(syncId, sourceScreenSlot, 0, SlotActionType.PICKUP);
            }

            if (!isCursorEmpty()) {
                // Last-resort safety: try to put cursor back to destination/source without dropping.
                click(syncId, sourceScreenSlot, 0, SlotActionType.PICKUP);
                if (!isCursorEmpty()) {
                    click(syncId, destScreenSlot, 0, SlotActionType.PICKUP);
                }
            }

            return true;
        } catch (Throwable throwable) {
            lastStatus = "execute_error:" + throwable.getClass().getSimpleName() + ":" + safeMsg(throwable);
            return false;
        }
    }

    /**
     * Starts a staged vanilla LMB drag distribution. Step 22.57 sent pickup/start/add/end
     * in one tick, which some SpookyTime-like servers accepted only partially: for example
     * 64 potions became 3x16 and the remaining 16 stayed on the cursor.
     *
     * Step 22.58 intentionally spreads the drag over several ticks:
     * pickup -> drag start -> one slot per small delay -> drag end -> cursor remainder recovery.
     * This is closer to a real manual LMB drag and prevents the seller loop from getting
     * stuck forever on a non-empty cursor.
     */
    private boolean executePotionDragPlan(int syncId, UnstackPlan plan, int sourceScreenSlot) {
        if (plan.dragInventorySlots == null || plan.dragInventorySlots.size() < POTION_DRAG_MIN_SLOTS) {
            lastStatus = "potion_drag_no_slots";
            return false;
        }

        if (sourceScreenSlot < 0) {
            lastStatus = "bad_potion_source_slot:" + sourceScreenSlot;
            return false;
        }

        List<Integer> dragScreenSlots = new ArrayList<Integer>(plan.dragInventorySlots.size());
        for (Integer inventorySlot : plan.dragInventorySlots) {
            if (inventorySlot == null) {
                continue;
            }
            int screenSlot = inventorySlotToPlayerScreenSlot(inventorySlot.intValue());
            if (screenSlot >= 0) {
                dragScreenSlots.add(screenSlot);
            }
        }

        if (dragScreenSlots.size() < POTION_DRAG_MIN_SLOTS) {
            lastStatus = "potion_drag_bad_slots:" + dragScreenSlots.size();
            return false;
        }

        activePotionDrag = new PotionDragState(
                plan.sourceInventorySlot,
                sourceScreenSlot,
                plan.dragInventorySlots,
                dragScreenSlots,
                plan.sourceCount,
                plan.targetLabel,
                plan.minSourceCount
        );

        continuePotionDrag(System.currentTimeMillis());
        return true;
    }

    private void continuePotionDrag(long now) {
        if (activePotionDrag == null) {
            return;
        }

        try {
            if (client.interactionManager == null || client.player == null) {
                failPotionDrag(now, "potion_drag_no_interaction_manager");
                return;
            }

            ScreenHandler handler = client.player.currentScreenHandler;
            if (handler == null) {
                failPotionDrag(now, "potion_drag_no_screen_handler");
                return;
            }

            if (handler.syncId != 0) {
                closeHandledScreenForUnstack();
                failPotionDrag(now, "potion_drag_close_handler:syncId=" + handler.syncId);
                return;
            }

            PotionDragState state = activePotionDrag;
            if (state.stage == PotionDragState.STAGE_PICKUP) {
                click(0, state.sourceScreenSlot, 0, SlotActionType.PICKUP);
                if (isCursorEmpty()) {
                    failPotionDrag(now, "potion_drag_pickup_failed:src=" + state.sourceInventorySlot);
                    return;
                }

                state.stage = PotionDragState.STAGE_START;
                nextAtMs = now + POTION_DRAG_STEP_DELAY_MS;
                lastStatus = "potion_drag_pickup:src=" + state.sourceInventorySlot
                        + ":slots=" + state.dragScreenSlots.size()
                        + ":before=" + state.sourceCount;
                return;
            }

            if (state.stage == PotionDragState.STAGE_START) {
                click(0, -999, ScreenHandler.packQuickCraftData(0, 0), SlotActionType.QUICK_CRAFT);
                state.stage = PotionDragState.STAGE_ADD;
                state.nextDragIndex = 0;
                nextAtMs = now + POTION_DRAG_STEP_DELAY_MS;
                lastStatus = "potion_drag_start:src=" + state.sourceInventorySlot
                        + ":slots=" + state.dragScreenSlots.size();
                return;
            }

            if (state.stage == PotionDragState.STAGE_ADD) {
                if (state.nextDragIndex < state.dragScreenSlots.size()) {
                    int screenSlot = state.dragScreenSlots.get(state.nextDragIndex).intValue();
                    click(0, screenSlot, ScreenHandler.packQuickCraftData(1, 0), SlotActionType.QUICK_CRAFT);
                    state.nextDragIndex++;
                    nextAtMs = now + POTION_DRAG_STEP_DELAY_MS;
                    lastStatus = "potion_drag_add:" + state.nextDragIndex + "/" + state.dragScreenSlots.size()
                            + ":screenSlot=" + screenSlot;
                    return;
                }

                state.stage = PotionDragState.STAGE_END;
                nextAtMs = now + POTION_DRAG_STEP_DELAY_MS;
                lastStatus = "potion_drag_add_done:" + state.dragScreenSlots.size();
                return;
            }

            if (state.stage == PotionDragState.STAGE_END) {
                click(0, -999, ScreenHandler.packQuickCraftData(2, 0), SlotActionType.QUICK_CRAFT);
                state.stage = PotionDragState.STAGE_PLACE_REMAINDER;
                nextAtMs = now + POTION_DRAG_SETTLE_DELAY_MS;
                lastStatus = "potion_drag_end:src=" + state.sourceInventorySlot;
                return;
            }

            if (state.stage == PotionDragState.STAGE_PLACE_REMAINDER) {
                if (isCursorEmpty()) {
                    completePotionDrag(now, "potion_drag_done");
                    return;
                }

                int placeScreenSlot = findSafeEmptyScreenSlotForPotionRemainder(state);
                if (placeScreenSlot < 0) {
                    state.placeRetries++;
                    if (state.placeRetries >= POTION_DRAG_CURSOR_PLACE_RETRIES) {
                        failPotionDragKeepCursor(now, "potion_drag_cursor_left:no_empty_slot");
                        return;
                    }

                    nextAtMs = now + POTION_DRAG_SETTLE_DELAY_MS;
                    lastStatus = "potion_drag_wait_empty_for_cursor:" + state.placeRetries;
                    return;
                }

                click(0, placeScreenSlot, 0, SlotActionType.PICKUP);
                state.placeRetries++;

                if (isCursorEmpty()) {
                    completePotionDrag(now, "potion_drag_done_with_remainder:slot=" + placeScreenSlot);
                    return;
                }

                if (state.placeRetries >= POTION_DRAG_CURSOR_PLACE_RETRIES) {
                    failPotionDragKeepCursor(now, "potion_drag_cursor_left:retries=" + state.placeRetries);
                    return;
                }

                nextAtMs = now + POTION_DRAG_SETTLE_DELAY_MS;
                lastStatus = "potion_drag_place_remainder_retry:" + state.placeRetries
                        + ":slot=" + placeScreenSlot;
            }
        } catch (Throwable throwable) {
            failPotionDrag(now, "potion_drag_error:" + throwable.getClass().getSimpleName() + ":" + safeMsg(throwable));
        }
    }

    private void completePotionDrag(long now, String status) {
        PotionDragState state = activePotionDrag;
        if (state == null) {
            return;
        }

        actionsDone++;
        lastSourceSlot = state.sourceInventorySlot;
        lastDestSlot = state.dragInventorySlots.isEmpty() ? -1 : state.dragInventorySlots.get(0).intValue();
        lastAmount = state.dragInventorySlots.size();
        lastStatus = status
                + ":" + state.targetLabel
                + ":src=" + state.sourceInventorySlot
                + ":slots=" + state.dragInventorySlots.size()
                + ":before=" + state.sourceCount
                + ":min=" + state.minSourceCount;
        activePotionDrag = null;
        // Potion drag is already staged over several ticks; after a clean finish we do not
        // need the full normal right-click unstack delay before the next potion stack.
        nextAtMs = now + POTION_DRAG_NEXT_STACK_DELAY_MS;
    }

    private void failPotionDrag(long now, String status) {
        activePotionDrag = null;
        lastStatus = status;
        nextAtMs = now + delayMs;
    }

    private void failPotionDragKeepCursor(long now, String status) {
        activePotionDrag = null;
        lastStatus = status;
        nextAtMs = now + delayMs;
    }

    private void click(int syncId, int slot, int button, SlotActionType actionType) {
        client.interactionManager.clickSlot(syncId, slot, button, actionType, client.player);
    }

    private void closeHandledScreenForUnstack() {
        try {
            if (client != null && client.player != null) {
                client.player.closeHandledScreen();
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (client != null) {
                client.setScreen(null);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isChatScreenOpen() {
        String name = safeScreenName().toLowerCase(Locale.ROOT);
        return name.contains("chatscreen") || name.contains("class_408") || name.endsWith(".chat");
    }

    private String safeScreenName() {
        try {
            return client == null || client.currentScreen == null ? "none" : client.currentScreen.getClass().getName();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private TargetItem findUnstackTarget(int inventorySlot, ItemStack stack) {
        try {
            InventoryItemSnapshot snapshot = new InventoryItemSnapshot(
                    inventorySlot,
                    safeItemId(stack),
                    safeItemName(stack),
                    stack.getCount(),
                    readTooltip(stack),
                    readNbt(stack)
            );

            // Do not require target.isEnabled() here. In Malfix config enabled/maxBuyPrice
            // means "allowed to BUY"; unstack is a SELL preparation setting and must work
            // even when buy price is 0/disabled. Old SpookyBuy also looked up the item
            // definition and then checked only isUnstack()/amount.
            for (TargetItem candidate : targets) {
                if (candidate == null || !candidate.isUnstack() || candidate.getUnstackAmount() <= 0) {
                    continue;
                }

                java.util.List<TargetItem> one = java.util.Collections.singletonList(new TargetItem(
                        candidate.getLabel(),
                        candidate.getNameContains(),
                        candidate.getItemId(),
                        candidate.getTagContains(),
                        Long.MAX_VALUE,
                        candidate.getSellUnitPrice(),
                        true,
                        candidate.isUnstack(),
                        candidate.getUnstackAmount()
                ));

                MatchResult match = matcher.match(snapshot, one);
                if (match != null && match.isMatched()) {
                    return candidate;
                }

                if (fallbackMatchesUnstackTarget(candidate, snapshot)) {
                    lastStatus = "fallback_match:" + candidate.getLabel();
                    return candidate;
                }
            }

            return null;
        } catch (Throwable throwable) {
            lastStatus = "match_error:" + throwable.getClass().getSimpleName() + ":" + safeMsg(throwable);
            return null;
        }
    }

    /**
     * The old jar delegates matching to AutoBuy.getItem(stack). In practice that
     * accepts many custom items by their visible name/NBT even when the strict
     * auction matcher cannot prove every enchant/tag rule from tooltip text.
     * For unstack this fallback is safe: it is used only for items where the user
     * explicitly enabled "Расстакивать" in GUI.
     */
    private boolean fallbackMatchesUnstackTarget(TargetItem target, InventoryItemSnapshot snapshot) {
        if (target == null || snapshot == null) {
            return false;
        }

        String itemId = normalizeId(snapshot.getItemId());
        String targetId = normalizeId(target.getItemId());
        if (!targetId.isEmpty() && !itemId.equals(targetId)) {
            return false;
        }

        StringBuilder builder = new StringBuilder();
        appendNormalized(builder, snapshot.getDisplayName());
        appendNormalized(builder, snapshot.getNbtString());
        for (String line : snapshot.getTooltipLines()) {
            appendNormalized(builder, line);
        }
        String searchable = compactSpaces(builder.toString());

        if (searchable.isEmpty()) {
            return targetId.isEmpty() || itemId.equals(targetId);
        }

        if (containsLoose(searchable, target.getLabel())) {
            return true;
        }

        for (String token : target.getNameContains()) {
            if (containsLoose(searchable, token)) {
                return true;
            }
        }

        String tag = target.getTagContains();
        if (tag != null && !tag.trim().isEmpty()) {
            String[] andParts = tag.split("&&");
            boolean anyPositivePartMatched = false;
            for (String part : andParts) {
                String rawPart = part == null ? "" : part.trim();
                if (rawPart.isEmpty() || rawPart.startsWith("!") || rawPart.startsWith("ench:") || rawPart.startsWith("enchant:")) {
                    continue;
                }
                String[] alternatives = rawPart.split("\\|\\|");
                for (String alternative : alternatives) {
                    if (containsLoose(searchable, alternative)) {
                        anyPositivePartMatched = true;
                        break;
                    }
                }
                if (anyPositivePartMatched) {
                    break;
                }
            }
            if (anyPositivePartMatched) {
                return true;
            }
        }

        // Plain vanilla targets like enchanted golden apple may have only itemId.
        return !targetId.isEmpty() && itemId.equals(targetId) && isPlainUnstackTarget(target);
    }

    private boolean isPlainUnstackTarget(TargetItem target) {
        String label = normalizeLoose(target.getLabel());
        String tag = normalizeLoose(target.getTagContains());
        if (!tag.isEmpty()) {
            return false;
        }
        if (label.contains("крушител") || label.contains("талисман") || label.contains("сфера") || label.contains("зелье")) {
            return false;
        }
        return true;
    }

    private void appendNormalized(StringBuilder builder, String value) {
        String normalized = normalizeLoose(value);
        if (!normalized.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(normalized);
        }
    }

    private boolean containsLoose(String searchable, String rawNeedle) {
        String needle = normalizeLoose(rawNeedle);
        return !needle.isEmpty() && searchable.contains(needle);
    }

    private String normalizeLoose(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace('§', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
    }

    private String compactSpaces(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        boolean lastSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean space = Character.isWhitespace(c) || c == ',' || c == ';' || c == ':' || c == '"' || c == '\'' || c == '[' || c == ']' || c == '{' || c == '}';
            if (space) {
                if (!lastSpace && out.length() > 0) {
                    out.append(' ');
                }
                lastSpace = true;
            } else {
                out.append(c);
                lastSpace = false;
            }
        }
        return out.toString().trim();
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isPotionDragUnstackTarget(TargetItem target, ItemStack stack) {
        if (target == null || !target.isUnstack()) {
            return false;
        }

        String itemId = normalizeId(target.getItemId());
        String stackId = normalizeId(safeItemId(stack));
        if (!"minecraft:potion".equals(itemId) && !"minecraft:potion".equals(stackId)) {
            return false;
        }

        String label = normalizeLoose(target.getLabel());
        String tag = normalizeLoose(target.getTagContains());
        return label.contains("несоздаваемое зелье")
                || tag.contains("effect speed 3600 2")
                || tag.contains("effect strength 3600 2")
                || target.getTagContains().contains("effect:speed:3600:2")
                || target.getTagContains().contains("effect:strength:3600:2");
    }

    private int getPotionDragMinSourceCount(TargetItem target) {
        if (target == null) {
            return 24;
        }

        int value = target.getPotionDragMinSourceCount();
        if (value < 1) {
            return 24;
        }
        if (value > 64) {
            return 64;
        }
        return value;
    }

    private List<Integer> buildPotionDragInventorySlots(int sourceInventorySlot, int sourceCount, int configuredSlots) {
        List<Integer> result = new ArrayList<Integer>();
        if (sourceInventorySlot < 0 || sourceInventorySlot >= 36 || sourceCount <= 1) {
            return result;
        }

        int desiredSlots = clampPotionDragSlots(configuredSlots);
        desiredSlots = Math.min(desiredSlots, sourceCount);
        if (desiredSlots < POTION_DRAG_MIN_SLOTS) {
            desiredSlots = POTION_DRAG_MIN_SLOTS;
        }

        // Prefer real empty destination slots first. Step 22.57 included the source
        // slot immediately after pickup; on some servers that source slot is accepted
        // unreliably during QUICK_CRAFT and causes a cursor remainder. Use it only as
        // a fallback when there are not enough other empty slots.
        appendEmptyPotionDragSlots(result, sourceInventorySlot, desiredSlots, 9, 35);
        appendEmptyPotionDragSlots(result, sourceInventorySlot, desiredSlots, 0, 8);

        if (result.size() < desiredSlots) {
            result.add(sourceInventorySlot);
        }

        return result;
    }

    private void appendEmptyPotionDragSlots(List<Integer> result, int sourceInventorySlot, int desiredSlots, int from, int to) {
        if (result.size() >= desiredSlots) {
            return;
        }
        for (int slot = from; slot <= to && result.size() < desiredSlots; slot++) {
            if (slot == sourceInventorySlot) {
                continue;
            }
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (isEmpty(stack)) {
                result.add(slot);
            }
        }
    }

    private int clampPotionDragSlots(int value) {
        if (value <= 1) {
            return POTION_DRAG_DEFAULT_SLOTS;
        }
        if (value < POTION_DRAG_MIN_SLOTS) {
            return POTION_DRAG_MIN_SLOTS;
        }
        if (value > POTION_DRAG_MAX_SLOTS) {
            return POTION_DRAG_MAX_SLOTS;
        }
        return value;
    }

    private boolean tryPlaceLooseCursorIntoEmptySlot() {
        try {
            if (client == null || client.player == null || client.interactionManager == null) {
                return false;
            }

            ScreenHandler handler = client.player.currentScreenHandler;
            if (handler == null || handler.syncId != 0) {
                return false;
            }

            int screenSlot = findEmptyScreenSlot(null);
            if (screenSlot < 0) {
                return false;
            }

            click(0, screenSlot, 0, SlotActionType.PICKUP);
            return isCursorEmpty();
        } catch (Throwable throwable) {
            lastStatus = "cursor_recover_error:" + throwable.getClass().getSimpleName() + ":" + safeMsg(throwable);
            return false;
        }
    }

    private int findSafeEmptyScreenSlotForPotionRemainder(PotionDragState state) {
        Set<Integer> excluded = new HashSet<Integer>();
        if (state != null && state.dragInventorySlots != null) {
            excluded.addAll(state.dragInventorySlots);
        }

        int screenSlot = findEmptyScreenSlot(excluded);
        if (screenSlot >= 0) {
            return screenSlot;
        }

        // Fallback: if the server ignored one of the dragged slots, it may still be empty.
        return findEmptyScreenSlot(null);
    }

    private int findEmptyScreenSlot(Set<Integer> excludedInventorySlots) {
        int inventorySlot = findEmptyInventorySlot(excludedInventorySlots, 9, 35);
        if (inventorySlot < 0) {
            inventorySlot = findEmptyInventorySlot(excludedInventorySlots, 0, 8);
        }

        return inventorySlot < 0 ? -1 : inventorySlotToPlayerScreenSlot(inventorySlot);
    }

    private int findEmptyInventorySlot(Set<Integer> excludedInventorySlots, int from, int to) {
        if (!isReady()) {
            return -1;
        }

        for (int slot = from; slot <= to; slot++) {
            if (excludedInventorySlots != null && excludedInventorySlots.contains(Integer.valueOf(slot))) {
                continue;
            }

            ItemStack stack = client.player.getInventory().getStack(slot);
            if (isEmpty(stack)) {
                return slot;
            }
        }

        return -1;
    }

    private int countEmptyInventorySlotsExcept(int ignoredSlot) {
        if (!isReady()) {
            return 0;
        }

        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (slot == ignoredSlot) {
                continue;
            }
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (isEmpty(stack)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasEnabledUnstackTargets() {
        if (targets == null || targets.isEmpty()) {
            return false;
        }

        for (TargetItem target : targets) {
            if (target != null && target.isUnstack() && target.getUnstackAmount() > 0) {
                return true;
            }
        }

        return false;
    }

    private int getEmptyInventorySlot() {
        if (!isReady()) {
            return -1;
        }

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (isEmpty(stack)) {
                return slot;
            }
        }

        return -1;
    }

    private int inventorySlotToPlayerScreenSlot(int inventorySlot) {
        if (inventorySlot >= 9 && inventorySlot <= 35) {
            return inventorySlot;
        }

        if (inventorySlot >= 0 && inventorySlot <= 8) {
            return 36 + inventorySlot;
        }

        return -1;
    }

    private boolean isReady() {
        return client != null
                && client.player != null
                && client.world != null
                && client.player.getInventory() != null;
    }

    private boolean isCursorEmpty() {
        try {
            ItemStack cursor = client.player.currentScreenHandler == null ? ItemStack.EMPTY : client.player.currentScreenHandler.getCursorStack();
            return isEmpty(cursor);
        } catch (Throwable throwable) {
            lastStatus = "cursor_error:" + throwable.getClass().getSimpleName() + ":" + safeMsg(throwable);
            return false;
        }
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    private boolean isShulker(ItemStack stack) {
        if (isEmpty(stack)) {
            return false;
        }

        try {
            String id = McItemStacks.itemId(stack).toLowerCase(Locale.ROOT);
            return id.contains("shulker_box") && !id.contains("spawn_egg");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String safeItemId(ItemStack stack) {
        try {
            return McItemStacks.itemId(stack);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String safeItemName(ItemStack stack) {
        try {
            return stack.getName().getString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String readNbt(ItemStack stack) {
        return McItemStacks.componentString(stack);
    }

    private List<String> readTooltip(ItemStack stack) {
        return McItemStacks.tooltip(stack, client);
    }

    private String safeMsg(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return "";
        }
        return throwable.getMessage().replace('\n', ' ').replace('\r', ' ');
    }

    private static final class PotionDragState {
        private static final int STAGE_PICKUP = 0;
        private static final int STAGE_START = 1;
        private static final int STAGE_ADD = 2;
        private static final int STAGE_END = 3;
        private static final int STAGE_PLACE_REMAINDER = 4;

        private final int sourceInventorySlot;
        private final int sourceScreenSlot;
        private final List<Integer> dragInventorySlots;
        private final List<Integer> dragScreenSlots;
        private final int sourceCount;
        private final String targetLabel;
        private final int minSourceCount;
        private int stage = STAGE_PICKUP;
        private int nextDragIndex = 0;
        private int placeRetries = 0;

        private PotionDragState(int sourceInventorySlot, int sourceScreenSlot, List<Integer> dragInventorySlots, List<Integer> dragScreenSlots, int sourceCount, String targetLabel, int minSourceCount) {
            this.sourceInventorySlot = sourceInventorySlot;
            this.sourceScreenSlot = sourceScreenSlot;
            this.dragInventorySlots = dragInventorySlots == null ? Collections.<Integer>emptyList() : new ArrayList<Integer>(dragInventorySlots);
            this.dragScreenSlots = dragScreenSlots == null ? Collections.<Integer>emptyList() : new ArrayList<Integer>(dragScreenSlots);
            this.sourceCount = sourceCount;
            this.targetLabel = targetLabel == null ? "" : targetLabel;
            this.minSourceCount = minSourceCount;
        }
    }

    private static final class UnstackPlan {
        private final int sourceInventorySlot;
        private final int destInventorySlot;
        private final int amount;
        private final int sourceCount;
        private final String targetLabel;
        private final int minSourceCount;
        private final boolean potionDrag;
        private final List<Integer> dragInventorySlots;

        private static UnstackPlan rightClick(int sourceInventorySlot, int destInventorySlot, int amount, int sourceCount, String targetLabel) {
            return new UnstackPlan(sourceInventorySlot, destInventorySlot, amount, sourceCount, targetLabel, 0, false, Collections.<Integer>emptyList());
        }

        private static UnstackPlan potionDrag(int sourceInventorySlot, List<Integer> dragInventorySlots, int sourceCount, String targetLabel, int minSourceCount) {
            int amount = dragInventorySlots == null ? 0 : dragInventorySlots.size();
            return new UnstackPlan(sourceInventorySlot, -1, amount, sourceCount, targetLabel, minSourceCount, true, dragInventorySlots);
        }

        private UnstackPlan(int sourceInventorySlot, int destInventorySlot, int amount, int sourceCount, String targetLabel, int minSourceCount, boolean potionDrag, List<Integer> dragInventorySlots) {
            this.sourceInventorySlot = sourceInventorySlot;
            this.destInventorySlot = destInventorySlot;
            this.amount = amount;
            this.sourceCount = sourceCount;
            this.targetLabel = targetLabel == null ? "" : targetLabel;
            this.minSourceCount = minSourceCount;
            this.potionDrag = potionDrag;
            this.dragInventorySlots = dragInventorySlots == null ? Collections.<Integer>emptyList() : new ArrayList<Integer>(dragInventorySlots);
        }
    }
}
