package ru.malfix.autobuy.script;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.Locale;

/**
 * Compatibility helpers for old NeverAPI/SpookyBuy Nashorn scripts that use
 * pre-1.21 intermediary method/field names. Scripts can keep their old logic;
 * the loader rewrites the most fragile functions to call this bridge.
 */
public final class ScriptCompatBridge {
    private static volatile long scriptEcOwnedUntilMs = 0L;
    private static volatile long scriptStorageContainerOwnedUntilMs = 0L;
    private static volatile long scriptStorageCommandAtMs = 0L;
    private static volatile int scriptLastOpenedHotbarSlot = -1;

    private final MinecraftClient client;

    public static void noteScriptCommandSent(String command) {
        String lower = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if ("/ec".equals(lower) || lower.startsWith("/ec ") || ".ec".equals(lower) || lower.contains("/enderchest")) {
            long now = System.currentTimeMillis();
            scriptStorageCommandAtMs = now;
            scriptEcOwnedUntilMs = now + 30_000L;
            scriptStorageContainerOwnedUntilMs = now + 30_000L;
        }
    }

    public static boolean isScriptEcOwnerActive() {
        return System.currentTimeMillis() < scriptEcOwnedUntilMs;
    }

    public static boolean isScriptStorageOwnerActive() {
        return System.currentTimeMillis() < scriptStorageContainerOwnedUntilMs;
    }

    private static void noteScriptStorageOpenAttempt(int hotbarSlot) {
        long now = System.currentTimeMillis();
        scriptLastOpenedHotbarSlot = hotbarSlot;
        scriptStorageContainerOwnedUntilMs = now + 30_000L;
    }

    private static void prolongScriptStorageOwner() {
        long now = System.currentTimeMillis();
        scriptStorageContainerOwnedUntilMs = now + 30_000L;
    }

    private static void clearScriptEcOwner() {
        scriptEcOwnedUntilMs = 0L;
    }

    private static void clearScriptStorageOwner() {
        scriptStorageContainerOwnedUntilMs = 0L;
        scriptLastOpenedHotbarSlot = -1;
    }

    public ScriptCompatBridge(MinecraftClient client) {
        this.client = client;
    }

    public String getItemId(Object stackObject) {
        try {
            if (!(stackObject instanceof ItemStack)) {
                return "";
            }
            ItemStack stack = (ItemStack) stackObject;
            if (stack.isEmpty()) {
                return "";
            }
            return Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        } catch (Throwable throwable) {
            return "";
        }
    }

    public boolean isShulkerBox(Object stackObject) {
        String id = getItemId(stackObject);
        return id.indexOf("spawn_egg") < 0 && id.indexOf("shulker_box") >= 0;
    }

    public void selectHotbarSlot(Object slotObject) {
        int slot = toInt(slotObject, -1);
        if (client == null || client.player == null || slot < 0 || slot > 8) {
            return;
        }
        try {
            client.player.getInventory().selectedSlot = slot;
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
        } catch (Throwable throwable) {
            System.out.println("[MAB SCRIPT] compat selectHotbarSlot failed: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    public void rightClickMainHand() {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }
        try {
            // Step 23.33: the uploaded Never shalk.js opens shulkers by selecting
            // a hotbar slot and right-clicking. On 1.21.4 the opened screen can be
            // ShulkerBoxScreen or a server GenericContainerScreen whose title is not
            // guaranteed to contain "shulker". Mark the next small handled container
            // as script-owned so scan/put/take can use it without closing user menus.
            try {
                int selected = client.player.getInventory().selectedSlot;
                ItemStack selectedStack = selected >= 0 && selected < 9 ? client.player.getInventory().getStack(selected) : ItemStack.EMPTY;
                if (isShulkerBox(selectedStack)) {
                    noteScriptStorageOpenAttempt(selected);
                }
            } catch (Throwable ignored) {
            }
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        } catch (Throwable throwable) {
            System.out.println("[MAB SCRIPT] compat rightClickMainHand failed: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    public void quickMoveSlot(Object syncIdObject, Object slotIdObject) {
        int syncId = toInt(syncIdObject, -1);
        int slotId = toInt(slotIdObject, -1);
        if (client == null || client.player == null || client.interactionManager == null || syncId < 0 || slotId < 0) {
            return;
        }
        try {
            client.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.QUICK_MOVE, client.player);
        } catch (Throwable throwable) {
            System.out.println("[MAB SCRIPT] compat quickMoveSlot failed: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    public boolean isContainerOpen() {
        try {
            return client != null
                    && client.player != null
                    && client.player.currentScreenHandler != null
                    && isLegacyBlockingScreenOpen();
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * Legacy Never/Spooky scripts treated mc.currentScreen as a container screen and
     * closed it repeatedly. In Malfix 1.21.4 that also closes/blocks config screens,
     * pause/options screens and other user UI. Return true only for screens that the
     * shulker script is allowed to own: generic handled containers such as shulkers,
     * ender chest and auction-like inventories.
     */
    public boolean isLegacyBlockingScreenOpen() {
        try {
            if (client == null) {
                return false;
            }
            Screen screen = client.currentScreen;
            return isLegacyScriptOwnedScreen(screen);
        } catch (Throwable throwable) {
            return false;
        }
    }

    public boolean isAuctionScreenOpen() {
        try {
            if (client == null || client.currentScreen == null) {
                return false;
            }
            Screen screen = client.currentScreen;
            if (!(screen instanceof GenericContainerScreen) && !(screen instanceof HandledScreen)) {
                return false;
            }
            String title = "";
            try {
                title = screen.getTitle() == null ? "" : screen.getTitle().getString();
            } catch (Throwable ignored) {
            }
            String lower = title.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
            return lower.contains("auction")
                    || lower.contains("аукцион")
                    || lower.contains("/ah")
                    || lower.equals("ah")
                    || lower.contains(" ah")
                    || lower.contains("поиск")
                    || lower.contains("search")
                    || lower.contains("найден")
                    || lower.contains("результат");
        } catch (Throwable throwable) {
            return false;
        }
    }

    public boolean isInventoryFullEnough(Object thresholdObject) {
        int threshold = toInt(thresholdObject, 0);
        return countEmptyPlayerSlots() <= threshold;
    }

    public int countEmptyPlayerSlots() {
        try {
            if (client == null || client.player == null || client.player.getInventory() == null) {
                return 36;
            }
            int empty = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (stack == null || stack.isEmpty()) {
                    empty++;
                }
            }
            return empty;
        } catch (Throwable throwable) {
            return 36;
        }
    }

    public boolean shouldLegacyStorageHandleAuction(Object thresholdObject) {
        return isAuctionScreenOpen() && isInventoryFullEnough(thresholdObject);
    }

    public void closeAuctionScreenForStorage() {
        try {
            if (client != null && client.player != null && isAuctionScreenOpen()) {
                client.player.closeHandledScreen();
            }
        } catch (Throwable throwable) {
            System.out.println("[MAB SCRIPT] compat closeAuctionScreenForStorage failed: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }


    /**
     * Return true only for container screens that the old shulker script is
     * expected to own/close by itself. A plain GenericContainerScreen check is
     * too broad: many server menus are generic containers, so the script would
     * close every menu the player opens. Title matching is intentionally narrow.
     */
    private boolean isLegacyScriptOwnedScreen(Screen screen) {
        try {
            if (screen == null || screen instanceof ChatScreen || isMalfixScreen(screen)) {
                return false;
            }
            if (!(screen instanceof GenericContainerScreen) && !(screen instanceof HandledScreen)) {
                return false;
            }
            String title = "";
            try {
                title = screen.getTitle() == null ? "" : screen.getTitle().getString();
            } catch (Throwable ignored) {
            }
            String lower = title.toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (lower.contains("shulker") || lower.contains("шалкер")) {
                prolongScriptStorageOwner();
                return true;
            }
            if (lower.contains("ender") || lower.contains("эндер") || lower.contains("эндер-сундук") || lower.contains("ender chest")) {
                if (isScriptEcOwnerActive()) {
                    prolongScriptStorageOwner();
                    return true;
                }
                return false;
            }

            // Title/class fallback for server containers. This is only active right
            // after the script itself opened a storage screen, so it does not make
            // user menus closable. It fixes SCAN/PUT/TAKE when the server exposes
            // the shulker/EC as a plain GenericContainerScreen with a custom title.
            if (isScriptStorageOwnerActive() && isSmallStorageContainerOpen()) {
                prolongScriptStorageOwner();
                return true;
            }
            return false;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private boolean isMalfixScreen(Screen screen) {
        try {
            Package pkg = screen.getClass().getPackage();
            String name = pkg == null ? "" : pkg.getName();
            return name.startsWith("ru.malfix.autobuy.gui");
        } catch (Throwable throwable) {
            return false;
        }
    }

    public int getContainerSlotsCount() {
        try {
            if (client == null || client.player == null) {
                return 0;
            }
            ScreenHandler handler = client.player.currentScreenHandler;
            if (handler == null) {
                return 0;
            }
            int total = handler.slots.size();
            return total < 36 ? 0 : total - 36;
        } catch (Throwable throwable) {
            return 0;
        }
    }

    public void closeCurrentScreen() {
        try {
            if (client != null && client.player != null && isLegacyBlockingScreenOpen()) {
                boolean ender = isEnderStorageScreen(client.currentScreen);
                client.player.closeHandledScreen();
                if (ender) {
                    clearScriptEcOwner();
                }
                clearScriptStorageOwner();
            }
        } catch (Throwable throwable) {
            System.out.println("[MAB SCRIPT] compat closeCurrentScreen failed: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    private boolean isEnderStorageScreen(Screen screen) {
        try {
            if (screen == null) {
                return false;
            }
            String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
            String lower = title.toLowerCase(Locale.ROOT).replace('ё', 'е');
            return lower.contains("ender") || lower.contains("эндер") || lower.contains("эндер-сундук") || lower.contains("ender chest");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public int countHotbarShulkers() {
        try {
            if (client == null || client.player == null || client.player.getInventory() == null) {
                return 0;
            }
            int count = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (isShulkerBox(stack)) {
                    count++;
                }
            }
            return count;
        } catch (Throwable throwable) {
            return 0;
        }
    }

    public boolean shouldUseEcPut(Object knownFullObject, Object oldThresholdObject) {
        int knownFull = toInt(knownFullObject, 0);
        int oldThreshold = Math.max(1, toInt(oldThresholdObject, 3));
        int hotbarShulkers = countHotbarShulkers();
        if (hotbarShulkers <= 0) {
            return true;
        }
        return knownFull >= oldThreshold || knownFull >= hotbarShulkers;
    }

    public boolean isSmallStorageContainerOpen() {
        try {
            if (client == null || client.player == null || client.currentScreen == null) {
                return false;
            }
            if (!(client.currentScreen instanceof HandledScreen)) {
                return false;
            }
            ScreenHandler handler = client.player.currentScreenHandler;
            if (handler == null || handler == client.player.playerScreenHandler || handler.slots == null) {
                return false;
            }
            int containerSlots = getContainerSlotsCount();
            return containerSlots == 27 || containerSlots == 54 || (containerSlots > 0 && containerSlots <= 54);
        } catch (Throwable throwable) {
            return false;
        }
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
