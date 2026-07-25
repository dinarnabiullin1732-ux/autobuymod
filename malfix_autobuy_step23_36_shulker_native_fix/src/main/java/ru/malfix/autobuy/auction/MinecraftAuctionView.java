package ru.malfix.autobuy.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import ru.malfix.autobuy.mc.McItemStacks;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import ru.malfix.autobuy.profiler.MalfixProfiler;
import ru.malfix.autobuy.mc.McChat;

public final class MinecraftAuctionView implements AuctionView {

    private final MinecraftClient client;
    private final AuctionScreenConfig config;

    public MinecraftAuctionView(MinecraftClient client, AuctionScreenConfig config) {
        this.client = client;
        this.config = config == null ? AuctionScreenConfig.defaultConfig() : config;
    }

    @Override
    public boolean isAuctionOpen() {
        if (client == null || client.currentScreen == null) {
            return false;
        }

        Screen screen = client.currentScreen;

        if (!(screen instanceof GenericContainerScreen)) {
            return false;
        }

        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        String lower = title.toLowerCase(Locale.ROOT);

        return lower.contains("auction")
                || lower.contains("аукцион")
                || lower.contains("ah")
                || lower.contains("поиск")
                || lower.contains("search")
                || lower.contains("найден")
                || lower.contains("результат")
                || isPagedSearchTitle(lower);
    }

    private boolean isPagedSearchTitle(String lower) {
        if (lower == null) {
            return false;
        }
        String s = lower.replace('ё', 'е').trim();
        boolean hasPageMarker = s.contains("[") && s.contains("/") && s.contains("]");
        boolean hasSearchPrefix = s.startsWith("☃ п:") || s.startsWith("п:") || s.contains(" п:");
        return hasPageMarker && hasSearchPrefix;
    }

    @Override
    public void requestOpenAuction() {
        if (client == null || client.player == null) {
            return;
        }

        McChat.send(client, "/ah");
    }

    @Override
    public void closeCurrentScreen() {
        if (client != null && client.player != null) {
            client.player.closeHandledScreen();
        }
    }

    @Override
    public boolean clickRefresh() {
        return clickRefreshLikeOldSpooky();
    }

    @Override
    public boolean clickContainerSlot(int containerSlotId, int button) {
        return doClickContainerSlot(containerSlotId, button, SlotActionType.PICKUP);
    }

    @Override
    public boolean clickAuctionSlot(AuctionSlot slot) {
        if (slot == null || slot.isEmpty()) {
            return false;
        }
        return clickContainerSlot(slot.getContainerSlotId(), 0);
    }

    @Override
    public boolean ctrlLeftClickAuctionSlot(AuctionSlot slot) {
        if (slot == null || slot.isEmpty()) {
            return false;
        }

        // Auction buy shortcut: Ctrl + LMB equivalent for the server-side container action.
        // Regular PICKUP is kept for normal clicks/refresh, but autobuy purchase uses this method.
        return doClickContainerSlot(slot.getContainerSlotId(), 0, SlotActionType.QUICK_MOVE);
    }

    @Override
    public List<AuctionSlot> readAuctionSlots() {
        long profStart = MalfixProfiler.start();
        List<AuctionSlot> slots = readAuctionSlotsInternal();
        MalfixProfiler.recordAuctionRead(profStart, slots == null ? 0 : slots.size());
        return slots;
    }

    private List<AuctionSlot> readAuctionSlotsInternal() {
        if (client == null || client.player == null || !isAuctionOpen()) {
            return Collections.emptyList();
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler.slots == null) {
            return Collections.emptyList();
        }

        List<AuctionSlot> result = new ArrayList<AuctionSlot>(config.getAuctionSlotCount());

        for (int i = 0; i < config.getAuctionSlotCount(); i++) {
            int containerSlot = config.getFirstAuctionSlot() + i;

            if (containerSlot < 0 || containerSlot >= handler.slots.size()) {
                result.add(AuctionSlot.empty(i, containerSlot));
                continue;
            }

            Slot slot = handler.slots.get(containerSlot);
            ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getStack();

            if (stack == null || stack.isEmpty()) {
                result.add(AuctionSlot.empty(i, containerSlot));
                continue;
            }

            final ItemStack capturedStack = stack;
            result.add(new AuctionSlot(
                    i,
                    containerSlot,
                    false,
                    McItemStacks.itemId(capturedStack),
                    capturedStack.getName().getString(),
                    capturedStack.getCount(),
                    new java.util.function.Supplier<List<String>>() {
                        @Override
                        public List<String> get() {
                            return readTooltip(capturedStack);
                        }
                    },
                    new java.util.function.Supplier<String>() {
                        @Override
                        public String get() {
                            return readNbt(capturedStack);
                        }
                    }
            ));
        }

        return result;
        }


    @Override
    public long readPlayerBalance() {
        if (client == null || client.world == null) {
            return -1L;
        }

        long best = -1L;

        try {
            Scoreboard scoreboard = client.world.getScoreboard();
            if (scoreboard == null) {
                return -1L;
            }

            for (ScoreboardDisplaySlot slot : ScoreboardDisplaySlot.values()) {
                ScoreboardObjective objective = scoreboard.getObjectiveForSlot(slot);
                if (objective == null) {
                    continue;
                }

                if (objective.getDisplayName() != null) {
                    best = Math.max(best, parseBalanceLine(objective.getDisplayName().getString()));
                }

                for (ScoreboardEntry score : scoreboard.getScoreboardEntries(objective)) {
                    if (score == null) {
                        continue;
                    }

                    String entry = score.owner();
                    best = Math.max(best, parseBalanceLine(entry));
                    try {
                        if (score.name() != null) {
                            best = Math.max(best, parseBalanceLine(score.name().getString()));
                        }
                        if (score.display() != null) {
                            best = Math.max(best, parseBalanceLine(score.display().getString()));
                        }
                    } catch (Throwable ignored) {
                    }

                    try {
                        Team team = scoreboard.getScoreHolderTeam(entry);
                        if (team != null) {
                            String prefix = team.getPrefix() == null ? "" : team.getPrefix().getString();
                            String suffix = team.getSuffix() == null ? "" : team.getSuffix().getString();
                            best = Math.max(best, parseBalanceLine(prefix));
                            best = Math.max(best, parseBalanceLine(suffix));
                            best = Math.max(best, parseBalanceLine(prefix + entry + suffix));
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
            return best;
        }

        return best;
    }

    private long parseBalanceLine(String raw) {
        if (raw == null) {
            return -1L;
        }

        String text = stripColor(raw).toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (text.isEmpty()) {
            return -1L;
        }

        boolean looksLikeMoney = text.contains("баланс")
                || text.contains("бал")
                || text.contains("монет")
                || text.contains("монеты")
                || text.contains("коин")
                || text.contains("coins")
                || text.contains("coin")
                || text.contains("money")
                || text.contains("dollars")
                || text.contains("$")
                || text.contains("₽")
                || text.contains("руб");

        if (!looksLikeMoney) {
            return -1L;
        }

        long best = -1L;
        String[] chunks = text.split("[^0-9.,кkmmbb]+") ;
        for (String chunk : chunks) {
            long value = parseMoneyNumber(chunk);
            if (value > best) {
                best = value;
            }
        }

        return best;
    }

    private long parseMoneyNumber(String token) {
        if (token == null) {
            return -1L;
        }

        String s = token.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return -1L;
        }

        long multiplier = 1L;
        if (s.endsWith("к") || s.endsWith("k")) {
            multiplier = 1_000L;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("м") || s.endsWith("m")) {
            multiplier = 1_000_000L;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("b") || s.endsWith("б")) {
            multiplier = 1_000_000_000L;
            s = s.substring(0, s.length() - 1);
        }

        String cleaned = s.replace(",", "").replace(".", "");
        if (cleaned.isEmpty()) {
            return -1L;
        }

        long value = 0L;
        boolean hasDigit = false;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c < '0' || c > '9') {
                continue;
            }
            hasDigit = true;
            int digit = c - '0';
            if (value > (Long.MAX_VALUE - digit) / 10L) {
                return Long.MAX_VALUE;
            }
            value = value * 10L + digit;
        }

        if (!hasDigit) {
            return -1L;
        }

        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private String stripColor(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder out = new StringBuilder(text.length());
        boolean skip = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skip) {
                skip = false;
                continue;
            }
            if (c == '\u00a7') {
                skip = true;
                continue;
            }
            if (!Character.isISOControl(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Old SpookyBuy does not refresh the auction through interactionManager.clickSlot().
     * It sends a raw ClickSlotC2SPacket to slot 49 with PICKUP and an EMPTY carried stack.
     *
     * On this server that is more stable for the refresh button: the local client does not
     * try to simulate/container-update the click first, so the bot does not get a cursor
     * desync and does not "miss" refresh when several clients are running.
     */
    private boolean clickRefreshLikeOldSpooky() {
        // 1.21.4 ClickSlotC2SPacket carries revision/changed-stack data. Use the
        // client interaction manager, same final server action, but mapped safely.
        return doClickContainerSlot(config.getRefreshSlot(), 0, SlotActionType.PICKUP);
    }

    private boolean doClickContainerSlot(int slotId, int button, SlotActionType actionType) {
        if (client == null || client.player == null || client.interactionManager == null) {
            return false;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null) {
            return false;
        }

        if (slotId < 0 || slotId >= handler.slots.size()) {
            return false;
        }

        client.interactionManager.clickSlot(
                handler.syncId,
                slotId,
                button,
                actionType,
                client.player
        );

        MalfixProfiler.recordClick("container_slot_" + slotId);
        return true;
    }

    private String readNbt(ItemStack stack) {
        return McItemStacks.componentString(stack);
    }

    private List<String> readTooltip(ItemStack stack) {
        return McItemStacks.tooltip(stack, client);
    }
}
