package ru.malfix.autobuy.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import ru.malfix.autobuy.mc.McItemStacks;
import ru.malfix.autobuy.price.ParsedPrice;
import ru.malfix.autobuy.render.AutomationPerformance;
import ru.malfix.autobuy.price.PriceParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Client-only helper for /ah search pages.
 *
 * It does not click or buy anything. It only scans visible auction search item slots
 * and remembers the slot with the lowest unit price, so the screen mixin can draw
 * a light background marker on it.
 */
public final class AuctionCheapestHighlighter {

    private static final int FIRST_AUCTION_SLOT = 0;
    private static final int AUCTION_ITEM_SLOT_COUNT = 45;

    // Tooltip parsing is expensive. Do not do it from every drawSlot call.
    private static final long SCAN_INTERVAL_MS = 700L;
    private static final long SEARCH_MODE_TIMEOUT_MS = 120000L;

    private static final PriceParser PRICE_PARSER = new PriceParser();

    private static boolean searchModeActive = false;
    private static long searchModeActivatedAtMs = 0L;

    private static int cachedSyncId = Integer.MIN_VALUE;
    private static int cachedFingerprint = 0;
    private static long lastScanAtMs = 0L;
    private static int cheapestSlotId = -1;
    private static long cheapestUnitPrice = 0L;
    private static String cheapestItemName = "";

    private AuctionCheapestHighlighter() {
    }

    /**
     * Called from ClientPlayerEntityMixin for every outgoing chat message.
     * This is the safest way to distinguish /ah search from the normal /ah menu,
     * because on this server both screens may have the same title: "Аукцион".
     */
    public static void onLocalChatMessage(String message) {
        if (message == null) {
            return;
        }

        String trimmed = message.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (isAuctionSearchCommand(lower)) {
            searchModeActive = true;
            searchModeActivatedAtMs = System.currentTimeMillis();
            clearCacheOnly();
            return;
        }

        if (isPlainAuctionOpenCommand(lower) || isHubOrTeleportCommand(lower)) {
            searchModeActive = false;
            clearCacheOnly();
        }
    }

    public static boolean shouldHighlight(Slot slot) {
        if (AutomationPerformance.isLowLagActive()) {
            clearCacheOnly();
            return false;
        }
        if (slot == null) {
            return false;
        }

        updateCacheIfNeeded();
        if (cheapestSlotId < 0) {
            return false;
        }

        ScreenHandler handler = currentHandler();
        int slotId = resolveSlotId(handler, slot);
        return slotId == cheapestSlotId;
    }

    public static long getCheapestUnitPrice() {
        updateCacheIfNeeded();
        return cheapestUnitPrice;
    }

    public static String getCheapestItemName() {
        updateCacheIfNeeded();
        return cheapestItemName;
    }

    private static void updateCacheIfNeeded() {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();

        if (AutomationPerformance.isLowLagActive()) {
            clearCacheOnly();
            return;
        }

        if (client == null || client.player == null) {
            searchModeActive = false;
            clearCacheOnly();
            return;
        }

        boolean searchScreen = isAuctionSearchScreen(client.currentScreen);
        if (!searchScreen) {
            clearCacheOnly();
            return;
        }

        // Do not rely only on the outgoing /ah search command. The server search page title is
        // enough: "Поиск | Порох". This also fixes cases where search was opened by a button.
        if (!searchModeActive || now - searchModeActivatedAtMs > SEARCH_MODE_TIMEOUT_MS) {
            searchModeActive = true;
            searchModeActivatedAtMs = now;
            clearCacheOnly();
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler.slots == null || handler.slots.isEmpty()) {
            clearCacheOnly();
            return;
        }

        if (now - lastScanAtMs < SCAN_INTERVAL_MS && cachedSyncId != Integer.MIN_VALUE) {
            return;
        }
        lastScanAtMs = now;

        int syncId = handler.syncId;
        int fingerprint = fastFingerprint(handler);

        if (syncId == cachedSyncId && fingerprint == cachedFingerprint) {
            return;
        }

        cachedSyncId = syncId;
        cachedFingerprint = fingerprint;
        rescan(client, handler);
    }

    private static void rescan(MinecraftClient client, ScreenHandler handler) {
        int bestSlotId = -1;
        long bestUnit = Long.MAX_VALUE;
        String bestName = "";

        int maxSlotExclusive = Math.min(handler.slots.size(), FIRST_AUCTION_SLOT + AUCTION_ITEM_SLOT_COUNT);
        for (int slotId = FIRST_AUCTION_SLOT; slotId < maxSlotExclusive; slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (slot == null) {
                continue;
            }

            ItemStack stack = safeStack(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ParsedPrice price = PRICE_PARSER.parse(readTooltip(client, stack), stack.getCount());
            if (price == null || !price.isFound() || price.getUnitPrice() <= 0L) {
                continue;
            }

            long unit = price.getUnitPrice();
            if (unit < bestUnit || (unit == bestUnit && (bestSlotId < 0 || slotId < bestSlotId))) {
                bestUnit = unit;
                bestSlotId = slotId;
                bestName = stack.getName() == null ? "" : stack.getName().getString();
            }
        }

        if (bestSlotId < 0) {
            cheapestSlotId = -1;
            cheapestUnitPrice = 0L;
            cheapestItemName = "";
            return;
        }

        cheapestSlotId = bestSlotId;
        cheapestUnitPrice = bestUnit;
        cheapestItemName = bestName == null ? "" : bestName;
    }

    private static boolean isAuctionSearchScreen(Screen screen) {
        if (!(screen instanceof GenericContainerScreen)) {
            return false;
        }

        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        String lower = title.toLowerCase(Locale.ROOT);

        // Never highlight storage/relist pages. They may be opened from /ah and can share
        // the same container class, but they are not auction search result pages.
        if (lower.contains("хранилищ")
                || lower.contains("storage")
                || lower.contains("склад")
                || lower.contains("vault")) {
            return false;
        }

        // On SpookyTime the normal /ah title is usually just "Аукцион", while /ah search
        // opens as "Поиск | <item>". The previous version only accepted titles with
        // "Аукцион", so search pages were skipped and the main /ah could be scanned.
        if (isSearchTitle(lower)) {
            return true;
        }

        // Some SpookyTime-like servers open /ah search with the same title as the
        // ordinary auction window ("Аукцион"). Since onLocalChatMessage() arms
        // searchModeActive only after an outgoing /ah search command, this fallback
        // lets the highlighter work on those pages without scanning storage/vault.
        long now = System.currentTimeMillis();
        return searchModeActive && now - searchModeActivatedAtMs <= SEARCH_MODE_TIMEOUT_MS
                && (lower.contains("аукцион") || lower.contains("auction") || lower.contains("ah"));
    }

    private static boolean isSearchTitle(String lower) {
        if (lower == null) {
            return false;
        }
        return lower.contains("поиск")
                || lower.contains("search")
                || lower.contains("найден")
                || lower.contains("результат")
                || isPagedSearchTitle(lower);
    }

    private static boolean isPagedSearchTitle(String lower) {
        if (lower == null) {
            return false;
        }
        String s = lower.replace('ё', 'е').trim();
        boolean hasPageMarker = s.contains("[") && s.contains("/") && s.contains("]");
        boolean hasSearchPrefix = s.startsWith("☃ п:") || s.startsWith("п:") || s.contains(" п:");
        return hasPageMarker && hasSearchPrefix;
    }

    private static boolean isAuctionSearchCommand(String lower) {
        if (lower == null) {
            return false;
        }
        return lower.startsWith("/ah search ")
                || lower.startsWith("/auction search ")
                || lower.startsWith("/auc search ")
                || lower.startsWith("/аукцион search ");
    }

    private static boolean isPlainAuctionOpenCommand(String lower) {
        if (lower == null) {
            return false;
        }
        return lower.equals("/ah")
                || lower.equals("/auc")
                || lower.equals("/auction")
                || lower.equals("/аукцион");
    }

    private static boolean isHubOrTeleportCommand(String lower) {
        if (lower == null) {
            return false;
        }
        return lower.equals("/hub")
                || lower.startsWith("/hub ")
                || lower.startsWith("/an")
                || lower.startsWith("/spawn")
                || lower.startsWith("/warp");
    }

    private static ScreenHandler currentHandler() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return null;
        }
        return client.player.currentScreenHandler;
    }

    private static int resolveSlotId(ScreenHandler handler, Slot slot) {
        if (handler == null || handler.slots == null || slot == null) {
            return -1;
        }

        int max = Math.min(handler.slots.size(), FIRST_AUCTION_SLOT + AUCTION_ITEM_SLOT_COUNT);
        for (int i = FIRST_AUCTION_SLOT; i < max; i++) {
            if (handler.slots.get(i) == slot) {
                return i;
            }
        }

        return -1;
    }

    private static ItemStack safeStack(Slot slot) {
        try {
            ItemStack stack = slot.getStack();
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static List<String> readTooltip(MinecraftClient client, ItemStack stack) {
        return McItemStacks.tooltip(stack, client);
    }

    private static int fastFingerprint(ScreenHandler handler) {
        int result = 1;
        int maxSlotExclusive = Math.min(handler.slots.size(), FIRST_AUCTION_SLOT + AUCTION_ITEM_SLOT_COUNT);

        for (int slotId = FIRST_AUCTION_SLOT; slotId < maxSlotExclusive; slotId++) {
            Slot slot = handler.slots.get(slotId);
            ItemStack stack = slot == null ? ItemStack.EMPTY : safeStack(slot);
            if (stack.isEmpty()) {
                result = 31 * result + slotId;
                continue;
            }

            result = 31 * result + slotId;
            result = 31 * result + McItemStacks.itemId(stack).hashCode();
            result = 31 * result + stack.getCount();
            result = 31 * result + (stack.getName() == null ? 0 : stack.getName().getString().hashCode());
        }

        return result;
    }

    private static void clearCacheOnly() {
        cachedSyncId = Integer.MIN_VALUE;
        cachedFingerprint = 0;
        lastScanAtMs = 0L;
        cheapestSlotId = -1;
        cheapestUnitPrice = 0L;
        cheapestItemName = "";
    }
}
