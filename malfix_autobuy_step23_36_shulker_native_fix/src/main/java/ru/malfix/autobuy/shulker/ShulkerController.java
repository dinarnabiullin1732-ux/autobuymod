package ru.malfix.autobuy.shulker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.registry.Registries;
import ru.malfix.autobuy.config.MalfixTimings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import ru.malfix.autobuy.mc.McChat;

/**
 * Step 22.5: script-style shulker restack.
 *
 * The previous native/manual PICKUP implementation opened the shulker correctly,
 * but many servers rejected the pickup/place chain and the controller looped.
 * This controller intentionally follows the uploaded shalk.js flow more closely:
 * close GUI -> select hotbar shulker -> right click -> QUICK_MOVE player slots
 * into the opened container -> close -> restore /ah.
 */
public final class ShulkerController {

    private enum State {
        IDLE,
        WAIT_SCREEN_CLOSE,
        WAIT_OPEN,
        WAIT_AFTER_MOVE,
        WAIT_CLOSE,
        WAIT_NEXT_SHULKER,
        EC_WAIT_SCREEN_CLOSE,
        EC_WAIT_OPEN,
        EC_WAIT_AFTER_MOVE,
        EC_WAIT_CLOSE,
        RESTORE_AUCTION_SEND,
        RESTORE_AUCTION_WAIT_OPEN
    }

    private enum Mode {
        PUT,
        TAKE,
        SCAN,
        EC_PUT,
        EC_TAKE
    }

    private final MinecraftClient client;

    private State state = State.IDLE;
    private long nextActionAtMs = 0L;
    private long auctionRentBlockedUntilMs = 0L;
    private long noMoveBlockedUntilMs = 0L;
    private long currentOpenStartedAtMs = 0L;
    private long lastStartedAtMs = 0L;
    private long lastFinishedAtMs = 0L;
    private long lastKnownFullRescanAtMs = 0L;
    private long lastPutFinishedAtMs = 0L;
    private long ecRetryBlockedUntilMs = 0L;
    private long ecCommandOwnedUntilMs = 0L;
    private int ecCommandTries = 0;
    private boolean ecEmptyKnown = false;
    private boolean ecFullKnown = false;

    private static final long EC_OPEN_WAIT_MS = 600L;
    private static final long EC_COMMAND_RETRY_MS = 1_200L;
    private static final long EC_RETRY_COOLDOWN_MS = 10_000L;
    private static final int EC_MAX_COMMAND_TRIES = 3;

    private final long[] shulkerCooldownUntilMs = new long[9];
    private final boolean[] knownFullShulkers = new boolean[9];
    private final boolean[] knownEmptyShulkers = new boolean[9];

    private List<Integer> candidateHotbarSlots = new ArrayList<Integer>();
    private int candidateIndex = 0;
    private int currentHotbarSlot = -1;
    private Mode currentMode = Mode.PUT;

    private boolean restoreAuctionAfter = false;
    private String lastReason = "idle";
    private int lastMoved = 0;
    private int totalMoved = 0;
    private int runs = 0;
    private int emptySlotsBeforeMove = -1;
    private int attemptedMovesInCurrentShulker = 0;

    public ShulkerController(MinecraftClient client) {
        this.client = client;
    }

    public boolean onServerChatMessage(String message, boolean shouldRestoreAuctionAfter) {
        String lower = normalize(message);
        if (lower.isEmpty()) {
            return false;
        }

        if (isAuctionRentMessage(lower)) {
            auctionRentBlockedUntilMs = now() + MalfixTimings.SHULKER_AH_RENT_BLOCK_MS;
            if (isRunning()) {
                stop("auction_rent_block");
            }
            return false;
        }

        if (!isFullInventoryMessage(lower)) {
            return false;
        }

        return startPut("full_inventory_message", shouldRestoreAuctionAfter);
    }

    public boolean startPut(String reason, boolean shouldRestoreAuctionAfter) {
        return start(Mode.PUT, reason, shouldRestoreAuctionAfter);
    }

    public boolean startTake(String reason, boolean shouldRestoreAuctionAfter) {
        return start(Mode.TAKE, reason, shouldRestoreAuctionAfter);
    }

    public boolean startScan(String reason, boolean shouldRestoreAuctionAfter) {
        return start(Mode.SCAN, reason, shouldRestoreAuctionAfter);
    }

    public boolean startEcPut(String reason, boolean shouldRestoreAuctionAfter) {
        return start(Mode.EC_PUT, reason, shouldRestoreAuctionAfter);
    }

    public boolean startEcTake(String reason, boolean shouldRestoreAuctionAfter) {
        return start(Mode.EC_TAKE, reason, shouldRestoreAuctionAfter);
    }

    public boolean shouldTakeBackFromStorage() {
        long time = now();
        return lastPutFinishedAtMs > 0L
                && time - lastPutFinishedAtMs >= 4_000L
                && countEmptyPlayerInventorySlots() > 20
                && countHotbarShulkers() > 0
                && !isRunning();
    }

    public boolean hasTakeCandidates() {
        return !findHotbarShulkersForMode(now(), Mode.TAKE).isEmpty();
    }

    private boolean start(Mode mode, String reason, boolean shouldRestoreAuctionAfter) {
        if (!isReady()) {
            lastReason = "not_ready:" + reason;
            return false;
        }
        if (isRunning()) {
            lastReason = "already_running:" + reason;
            return true;
        }

        long time = now();
        if (time < auctionRentBlockedUntilMs) {
            lastReason = "blocked_after_ah_rent";
            return false;
        }
        boolean forceFullInventoryReason = isForceFullInventoryReason(reason);
        if (mode == Mode.PUT && time < noMoveBlockedUntilMs) {
            if (forceFullInventoryReason && isPlayerInventoryFullEnough()) {
                noMoveBlockedUntilMs = 0L;
                lastReason = "force_clear_no_move_block:" + reason;
            } else {
                lastReason = "blocked_after_no_move_left=" + Math.max(0L, noMoveBlockedUntilMs - time);
                return false;
            }
        }

        if (mode == Mode.EC_PUT || mode == Mode.EC_TAKE) {
            return startEc(mode, reason, shouldRestoreAuctionAfter, time);
        }

        maybeRescanKnownFullShulkers(time, reason);
        if (mode == Mode.TAKE || mode == Mode.SCAN) {
            maybeRescanKnownEmptyShulkers(time, reason);
        }

        currentMode = mode;
        candidateHotbarSlots = findHotbarShulkersForMode(time, mode);
        candidateIndex = 0;
        currentHotbarSlot = -1;
        restoreAuctionAfter = shouldRestoreAuctionAfter;
        lastMoved = 0;
        totalMoved = 0;
        emptySlotsBeforeMove = -1;
        attemptedMovesInCurrentShulker = 0;

        if (candidateHotbarSlots.isEmpty()) {
            if (!hasAnyHotbarShulker()) {
                lastReason = "no_hotbar_shulker";
            } else if (mode == Mode.TAKE) {
                lastReason = "all_hotbar_shulkers_known_empty_or_cooldown";
                if (!ecEmptyKnown && time >= ecRetryBlockedUntilMs) {
                    return startEc(Mode.EC_TAKE, "take_no_shulker_candidates:" + reason, shouldRestoreAuctionAfter, time);
                }
            } else if (mode == Mode.PUT) {
                lastReason = "all_hotbar_shulkers_known_full_or_cooldown";
                if (!ecFullKnown && time >= ecRetryBlockedUntilMs) {
                    return startEc(Mode.EC_PUT, "put_no_shulker_candidates:" + reason, shouldRestoreAuctionAfter, time);
                }
            } else {
                lastReason = "scan_no_hotbar_shulker_candidates";
            }
            return false;
        }

        runs++;
        lastStartedAtMs = time;
        lastReason = "script_running_" + mode.name().toLowerCase(Locale.ROOT) + ":" + reason;
        log("script start mode=" + mode + ", reason=" + reason + ", shulkers=" + candidateHotbarSlots);

        if (closeChatScreenIfOpen()) {
            state = State.WAIT_SCREEN_CLOSE;
            nextActionAtMs = time + 75L;
            lastReason = "chat_closed_before_storage:" + reason;
            return true;
        }

        if (hasUserControlledScreenOpen()) {
            lastReason = "user_screen_open_skip_start:" + reason;
            return false;
        }

        if (hasClosableScreenOpen()) {
            closeCurrentScreen();
            state = State.WAIT_SCREEN_CLOSE;
            nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_AUCTION_WAIT_MS;
            return true;
        }

        openCurrentShulker();
        return true;
    }

    public void tick() {
        if (!isRunning()) {
            return;
        }
        if (!isReady()) {
            stop("not_ready_tick");
            return;
        }

        long time = now();
        if (time < nextActionAtMs) {
            return;
        }
        if (time < auctionRentBlockedUntilMs) {
            stop("auction_rent_block_tick");
            return;
        }

        if (state == State.WAIT_SCREEN_CLOSE) {
            if (closeChatScreenIfOpen()) {
                nextActionAtMs = time + 75L;
                lastReason = "chat_closed_wait_screen_close";
                return;
            }
            if (hasUserControlledScreenOpen()) {
                stop("user_screen_open_wait_close");
                return;
            }
            if (hasClosableScreenOpen()) {
                closeCurrentScreen();
                nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_AUCTION_WAIT_MS;
                return;
            }
            openCurrentShulker();
            return;
        }

        if (state == State.WAIT_OPEN) {
            if (closeChatScreenIfOpen()) {
                nextActionAtMs = time + MalfixTimings.SHULKER_OPEN_RECHECK_MS;
                lastReason = "chat_closed_wait_open_slot=" + currentHotbarSlot;
                return;
            }
            if (!isScriptContainerOpen()) {
                if (time - currentOpenStartedAtMs < MalfixTimings.SHULKER_OPEN_TIMEOUT_MS) {
                    nextActionAtMs = time + MalfixTimings.SHULKER_OPEN_RECHECK_MS;
                    lastReason = "script_wait_open_slot=" + currentHotbarSlot;
                    return;
                }
                setCooldown(currentHotbarSlot);
                lastReason = "script_open_timeout_slot=" + currentHotbarSlot;
                moveToNextShulkerOrFinish();
                return;
            }

            int moved;
            if (currentMode == Mode.SCAN) {
                int freeScan = countFreeSlotsInOpenedContainer();
                int occupiedScan = countOccupiedSlotsInOpenedContainer();
                if (occupiedScan <= 0) {
                    markCurrentShulkerEmpty();
                    lastReason = "script_scan_empty_slot=" + currentHotbarSlot;
                } else if (freeScan <= 0) {
                    markCurrentShulkerFull();
                    lastReason = "script_scan_full_slot=" + currentHotbarSlot;
                } else {
                    knownFullShulkers[currentHotbarSlot] = false;
                    knownEmptyShulkers[currentHotbarSlot] = false;
                    lastReason = "script_scan_space_slot=" + currentHotbarSlot + ", free=" + freeScan + ", occupied=" + occupiedScan;
                }
                clearCooldown(currentHotbarSlot);
                closeCurrentScreen();
                state = State.WAIT_NEXT_SHULKER;
                nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
                return;
            } else if (currentMode == Mode.TAKE) {
                int occupiedSlots = countOccupiedSlotsInOpenedContainer();
                if (occupiedSlots <= 0) {
                    markCurrentShulkerEmpty();
                    closeCurrentScreen();
                    state = State.WAIT_NEXT_SHULKER;
                    nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
                    lastReason = "script_take_shulker_empty_slot=" + currentHotbarSlot;
                    log("take slot " + currentHotbarSlot + " empty");
                    return;
                }

                int freePlayerSlots = countEmptyPlayerInventorySlots();
                if (freePlayerSlots <= 0) {
                    closeCurrentScreen();
                    stop("script_take_player_full");
                    return;
                }

                emptySlotsBeforeMove = freePlayerSlots;
                int limit = Math.min(MalfixTimings.SHULKER_MOVE_SLOTS_LIMIT, Math.min(occupiedSlots, freePlayerSlots));
                moved = moveOccupiedSlotsFromOpenedContainerToPlayer(limit);
            } else {
                int freeSlots = countFreeSlotsInOpenedContainer();
                if (freeSlots <= 0) {
                    markCurrentShulkerFull();
                    closeCurrentScreen();
                    state = State.WAIT_NEXT_SHULKER;
                    nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
                    lastReason = "script_shulker_full_slot=" + currentHotbarSlot;
                    log("slot " + currentHotbarSlot + " full");
                    return;
                }

                emptySlotsBeforeMove = countEmptyPlayerInventorySlots();
                int limit = Math.min(MalfixTimings.SHULKER_MOVE_SLOTS_LIMIT, freeSlots);
                moved = moveOccupiedSlotsFromPlayerIntoOpenedContainer(limit);
            }
            attemptedMovesInCurrentShulker = moved;
            lastMoved = moved;
            totalMoved += moved;

            if (moved > 0) {
                clearCooldown(currentHotbarSlot);
                if (currentMode == Mode.TAKE) {
                    knownEmptyShulkers[currentHotbarSlot] = false;
                    knownFullShulkers[currentHotbarSlot] = false;
                    lastReason = "script_take_quickmove_attempted=" + moved + ", slot=" + currentHotbarSlot;
                    log("take quickmove attempted=" + moved + ", slot=" + currentHotbarSlot);
                } else {
                    knownFullShulkers[currentHotbarSlot] = false;
                    knownEmptyShulkers[currentHotbarSlot] = false;
                    lastReason = "script_put_quickmove_attempted=" + moved + ", slot=" + currentHotbarSlot;
                    log("put quickmove attempted=" + moved + ", slot=" + currentHotbarSlot);
                }
                state = State.WAIT_AFTER_MOVE;
                nextActionAtMs = time + MalfixTimings.SHULKER_AFTER_MOVE_WAIT_MS;
                return;
            }

            setCooldown(currentHotbarSlot);
            closeCurrentScreen();
            state = State.WAIT_NEXT_SHULKER;
            nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
            lastReason = currentMode == Mode.TAKE
                    ? "script_take_no_movable_items_slot=" + currentHotbarSlot
                    : "script_put_no_movable_items_slot=" + currentHotbarSlot;
            log("no movable items mode=" + currentMode + ", slot=" + currentHotbarSlot);
            return;
        }

        if (state == State.WAIT_AFTER_MOVE) {
            int emptyAfter = countEmptyPlayerInventorySlots();
            if (attemptedMovesInCurrentShulker > 0 && emptySlotsBeforeMove >= 0 && emptyAfter <= emptySlotsBeforeMove) {
                // Step 23.21: while autobuy is active the client can still receive one
                // pending purchased stack during the shulker move, so the number of empty
                // inventory slots may stay at 0 even though QUICK_MOVE was accepted. The
                // previous confirmation check treated that as a hard failure and blocked
                // storage for 10 seconds, causing an endless check/retry loop. Treat sent
                // quick-moves as progress and continue; a real no-move case is handled
                // earlier when moved == 0.
                lastReason = "script_move_sent_no_empty_delta_before=" + emptySlotsBeforeMove + ", after=" + emptyAfter;
                log("quickmove sent; empty slots did not increase yet before=" + emptySlotsBeforeMove + ", after=" + emptyAfter);
            } else {
                lastReason = "script_move_confirmed_before=" + emptySlotsBeforeMove + ", after=" + emptyAfter;
            }

            closeCurrentScreen();
            state = State.WAIT_CLOSE;
            nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
            return;
        }

        if (state == State.EC_WAIT_SCREEN_CLOSE) {
            if (hasUserControlledScreenOpen()) {
                stop("ec_user_screen_open_wait_close");
                return;
            }
            if (hasClosableScreenOpen()) {
                closeCurrentScreen();
                nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_AUCTION_WAIT_MS;
                return;
            }
            openEc();
            return;
        }

        if (state == State.EC_WAIT_OPEN) {
            if (!isEcContainerOpen()) {
                if (ecCommandTries < EC_MAX_COMMAND_TRIES) {
                    openEc();
                    nextActionAtMs = time + EC_COMMAND_RETRY_MS;
                    return;
                }
                ecRetryBlockedUntilMs = time + EC_RETRY_COOLDOWN_MS;
                if (currentMode == Mode.EC_TAKE) {
                    ecEmptyKnown = true;
                }
                stop("ec_open_timeout_tries=" + ecCommandTries);
                return;
            }

            int movedEc;
            if (currentMode == Mode.EC_TAKE) {
                int occupiedEc = countOccupiedSlotsInOpenedContainer();
                if (occupiedEc <= 0) {
                    ecEmptyKnown = true;
                    closeCurrentScreen();
                    state = State.EC_WAIT_CLOSE;
                    nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
                    lastReason = "ec_take_empty";
                    return;
                }
                int freePlayer = countEmptyPlayerInventorySlots();
                if (freePlayer <= 0) {
                    closeCurrentScreen();
                    stop("ec_take_player_full");
                    return;
                }
                emptySlotsBeforeMove = freePlayer;
                movedEc = moveOccupiedSlotsFromOpenedContainerToPlayer(Math.min(MalfixTimings.SHULKER_MOVE_SLOTS_LIMIT, Math.min(occupiedEc, freePlayer)));
                if (movedEc > 0) {
                    ecEmptyKnown = false;
                    lastReason = "ec_take_quickmove_attempted=" + movedEc;
                }
            } else {
                int freeEc = countFreeSlotsInOpenedContainer();
                if (freeEc <= 0) {
                    ecFullKnown = true;
                    closeCurrentScreen();
                    state = State.EC_WAIT_CLOSE;
                    nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
                    lastReason = "ec_put_full";
                    return;
                }
                emptySlotsBeforeMove = countEmptyPlayerInventorySlots();
                movedEc = moveOccupiedSlotsFromPlayerIntoOpenedContainer(Math.min(MalfixTimings.SHULKER_MOVE_SLOTS_LIMIT, freeEc));
                if (movedEc > 0) {
                    ecFullKnown = false;
                    ecEmptyKnown = false;
                    lastReason = "ec_put_quickmove_attempted=" + movedEc;
                }
            }
            attemptedMovesInCurrentShulker = movedEc;
            lastMoved = movedEc;
            totalMoved += movedEc;

            if (movedEc <= 0) {
                ecRetryBlockedUntilMs = time + EC_RETRY_COOLDOWN_MS;
                closeCurrentScreen();
                state = State.EC_WAIT_CLOSE;
                nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
                lastReason = currentMode == Mode.EC_TAKE ? "ec_take_no_movable_items" : "ec_put_no_movable_items";
                return;
            }

            state = State.EC_WAIT_AFTER_MOVE;
            nextActionAtMs = time + MalfixTimings.SHULKER_AFTER_MOVE_WAIT_MS;
            return;
        }

        if (state == State.EC_WAIT_AFTER_MOVE) {
            closeCurrentScreen();
            state = State.EC_WAIT_CLOSE;
            nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
            return;
        }

        if (state == State.EC_WAIT_CLOSE) {
            if (isEcContainerOpen()) {
                closeCurrentScreen();
                nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
                return;
            }
            finish();
            return;
        }

        if (state == State.WAIT_NEXT_SHULKER) {
            candidateIndex++;
            if (candidateIndex >= candidateHotbarSlots.size()) {
                finish();
                return;
            }

            if (hasUserControlledScreenOpen()) {
                stop("user_screen_open_before_next_shulker");
                return;
            }
            if (hasClosableScreenOpen()) {
                closeCurrentScreen();
                nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_AUCTION_WAIT_MS;
                return;
            }

            openCurrentShulker();
            return;
        }

        if (state == State.WAIT_CLOSE) {
            if (hasNonChatScreenOpen()) {
                closeCurrentScreen();
                nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
                return;
            }
            finish();
            return;
        }

        if (state == State.RESTORE_AUCTION_SEND) {
            if (hasNonChatScreenOpen()) {
                closeCurrentScreen();
                nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
                return;
            }
            sendChatCommand("/ah");
            state = State.RESTORE_AUCTION_WAIT_OPEN;
            nextActionAtMs = time + MalfixTimings.SHULKER_RESTORE_AUCTION_WAIT_MS;
            lastReason = "script_restore_ah_sent_totalMoved=" + totalMoved;
            return;
        }

        if (state == State.RESTORE_AUCTION_WAIT_OPEN) {
            stop("script_done_restore_ah_totalMoved=" + totalMoved);
        }
    }

    public void stop(String reason) {
        try {
            if (hasNonChatScreenOpen()) {
                closeCurrentScreen();
            }
        } catch (Throwable ignored) {
        }
        state = State.IDLE;
        nextActionAtMs = 0L;
        candidateHotbarSlots = new ArrayList<Integer>();
        candidateIndex = 0;
        currentHotbarSlot = -1;
        currentMode = Mode.PUT;
        restoreAuctionAfter = false;
        emptySlotsBeforeMove = -1;
        attemptedMovesInCurrentShulker = 0;
        ecCommandTries = 0;
        lastFinishedAtMs = now();
        lastReason = reason == null ? "script_stopped" : reason;
    }

    public boolean isRunning() {
        return state != State.IDLE;
    }

    public boolean isIdle() {
        return state == State.IDLE;
    }

    public int getTotalMoved() {
        return totalMoved;
    }

    public int countEmptyPlayerInventorySlots() {
        if (client == null || client.player == null || client.player.getInventory() == null) {
            return 0;
        }
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            try {
                if (isEmpty(client.player.getInventory().getStack(i))) {
                    empty++;
                }
            } catch (Throwable ignored) {
            }
        }
        return empty;
    }

    public boolean isPlayerInventoryFullEnough() {
        return countEmptyPlayerInventorySlots() <= 0;
    }

    public int countHotbarShulkers() {
        if (client == null || client.player == null || client.player.getInventory() == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < 9; i++) {
            try {
                if (isShulker(client.player.getInventory().getStack(i))) {
                    count++;
                }
            } catch (Throwable ignored) {
            }
        }
        return count;
    }

    public void resetKnownFullShulkers() {
        Arrays.fill(knownFullShulkers, false);
        Arrays.fill(knownEmptyShulkers, false);
        Arrays.fill(shulkerCooldownUntilMs, 0L);
        noMoveBlockedUntilMs = 0L;
        ecRetryBlockedUntilMs = 0L;
        ecCommandOwnedUntilMs = 0L;
        ecCommandTries = 0;
        ecEmptyKnown = false;
        ecFullKnown = false;
        lastKnownFullRescanAtMs = now();
        lastReason = "script_known_storage_reset";
    }

    public void clearNoMoveBlock(String reason) {
        noMoveBlockedUntilMs = 0L;
        lastReason = reason == null ? "script_no_move_block_cleared" : reason;
    }

    public String compact() {
        long time = now();
        return "scriptMode=true"
                + ", running=" + isRunning()
                + ", state=" + state
                + ", mode=" + currentMode
                + ", nextInMs=" + Math.max(0L, nextActionAtMs - time)
                + ", slot=" + currentHotbarSlot
                + ", index=" + candidateIndex + "/" + (candidateHotbarSlots == null ? 0 : candidateHotbarSlots.size())
                + ", lastMoved=" + lastMoved
                + ", totalMoved=" + totalMoved
                + ", attempted=" + attemptedMovesInCurrentShulker
                + ", beforeEmpty=" + emptySlotsBeforeMove
                + ", emptyInv=" + countEmptyPlayerInventorySlots()
                + ", hotbarShulkers=" + countHotbarShulkers()
                + ", knownFull=" + countKnownFullShulkers()
                + ", knownEmpty=" + countKnownEmptyShulkers()
                + ", takeReady=" + shouldTakeBackFromStorage()
                + ", lastPutAgoMs=" + (lastPutFinishedAtMs <= 0L ? -1L : Math.max(0L, time - lastPutFinishedAtMs))
                + ", noMoveBlockLeftMs=" + Math.max(0L, noMoveBlockedUntilMs - time)
                + ", ecFull=" + ecFullKnown
                + ", ecEmpty=" + ecEmptyKnown
                + ", ecRetryLeftMs=" + Math.max(0L, ecRetryBlockedUntilMs - time)
                + ", screen=" + compactScreenName()
                + ", handler=" + compactHandlerName()
                + ", restoreAh=" + restoreAuctionAfter
                + ", runs=" + runs
                + ", lastReason=" + lastReason
                + ", lastFinishedAgoMs=" + (lastFinishedAtMs <= 0L ? -1L : Math.max(0L, time - lastFinishedAtMs));
    }

    private String compactScreenName() {
        try {
            if (client == null || client.currentScreen == null) {
                return "none";
            }
            String name = client.currentScreen.getClass().getSimpleName();
            return name == null || name.isEmpty() ? "screen" : name;
        } catch (Throwable ignored) {
            return "err";
        }
    }

    private String compactHandlerName() {
        try {
            if (client == null || client.player == null || client.player.currentScreenHandler == null) {
                return "none";
            }
            String name = client.player.currentScreenHandler.getClass().getSimpleName();
            return name == null || name.isEmpty() ? "handler" : name;
        } catch (Throwable ignored) {
            return "err";
        }
    }

    private void openCurrentShulker() {
        if (candidateIndex >= candidateHotbarSlots.size()) {
            finish();
            return;
        }

        closeChatScreenIfOpen();

        currentHotbarSlot = candidateHotbarSlots.get(candidateIndex).intValue();
        if (!selectHotbarSlot(currentHotbarSlot)) {
            setCooldown(currentHotbarSlot);
            moveToNextShulkerOrFinish();
            return;
        }

        try {
            if (client.interactionManager != null && client.player != null && client.world != null) {
                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            }
        } catch (Throwable throwable) {
            setCooldown(currentHotbarSlot);
            lastReason = "script_right_click_failed:" + throwable.getClass().getSimpleName();
        }

        currentOpenStartedAtMs = now();
        state = State.WAIT_OPEN;
        nextActionAtMs = currentOpenStartedAtMs + MalfixTimings.SHULKER_OPEN_WAIT_MS;
        lastReason = "script_opening_slot=" + currentHotbarSlot;
    }

    private void moveToNextShulkerOrFinish() {
        closeCurrentScreen();
        state = State.WAIT_NEXT_SHULKER;
        nextActionAtMs = now() + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
    }

    private void finish() {
        long finishTime = now();
        lastFinishedAtMs = finishTime;

        if ((currentMode == Mode.PUT || currentMode == Mode.EC_PUT) && totalMoved > 0) {
            lastPutFinishedAtMs = finishTime;
        }

        if (currentMode == Mode.PUT && totalMoved <= 0 && shouldFallbackToEcAfterShulkers()) {
            boolean restore = restoreAuctionAfter;
            lastReason = "script_all_shulkers_full_try_ec";
            if (startEc(Mode.EC_PUT, "all_shulkers_full", restore, finishTime)) {
                return;
            }
        }

        if (totalMoved <= 0 && currentMode == Mode.PUT && now() >= noMoveBlockedUntilMs) {
            noMoveBlockedUntilMs = finishTime + MalfixTimings.SHULKER_NO_MOVE_BLOCK_MS;
            lastReason = "script_no_move_block_scheduled";
        }
        if (restoreAuctionAfter) {
            state = State.RESTORE_AUCTION_SEND;
            nextActionAtMs = finishTime + MalfixTimings.SHULKER_CLOSE_WAIT_MS;
            lastReason = "script_restore_ah_scheduled_totalMoved=" + totalMoved;
            return;
        }
        stop("script_done_totalMoved=" + totalMoved);
    }

    private int moveOccupiedSlotsFromPlayerIntoOpenedContainer(int limit) {
        if (client == null || client.player == null || client.interactionManager == null || limit <= 0) {
            return 0;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler.slots == null) {
            return 0;
        }

        int totalSlots = handler.slots.size();
        if (totalSlots <= 0) {
            return 0;
        }

        int moved = 0;
        List<Integer> moveSlotIds = collectPlayerMoveSlotIds(handler);

        // Step 23.19: the previous implementation copied shalk.js and assumed that
        // player slots are always exactly the last 36 handler slots. Some 1.21.4
        // server containers expose a different slot layout, so the controller opened
        // the shulker correctly but scanned the wrong slot ids and found nothing to
        // quick-move. Prefer real player-inventory Slot ownership; keep range
        // fallbacks for mappings/servers where reflection cannot see the inventory.
        for (int i = 0; i < moveSlotIds.size() && moved < limit; i++) {
            int slotId = moveSlotIds.get(i).intValue();
            if (slotId < 0 || slotId >= handler.slots.size()) {
                continue;
            }

            Slot slot = handler.slots.get(slotId);
            if (slot == null) {
                continue;
            }

            ItemStack stack = slot.getStack();
            if (isEmpty(stack) || isShulker(stack)) {
                continue;
            }

            try {
                client.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.QUICK_MOVE, client.player);
                moved++;
            } catch (Throwable throwable) {
                lastReason = "script_quickmove_failed:" + throwable.getClass().getSimpleName() + ", slot=" + slotId;
            }
        }

        if (moved <= 0) {
            lastReason = "script_no_movable_items_scanned=" + moveSlotIds.size()
                    + ", handlerSlots=" + totalSlots
                    + ", containerSlots=" + getContainerSlotsCount(handler);
        }

        return moved;
    }

    private int moveOccupiedSlotsFromOpenedContainerToPlayer(int limit) {
        if (client == null || client.player == null || client.interactionManager == null || limit <= 0) {
            return 0;
        }
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler.slots == null) {
            return 0;
        }

        int containerSlots = getContainerSlotsCount(handler);
        int moved = 0;
        for (int slotId = 0; slotId < containerSlots && slotId < handler.slots.size() && moved < limit; slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (slot == null) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (isEmpty(stack) || isShulker(stack)) {
                continue;
            }
            try {
                client.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.QUICK_MOVE, client.player);
                moved++;
            } catch (Throwable throwable) {
                lastReason = "script_take_quickmove_failed:" + throwable.getClass().getSimpleName() + ", slot=" + slotId;
            }
        }
        if (moved <= 0) {
            lastReason = "script_take_no_movable_items_scanned=" + containerSlots
                    + ", handlerSlots=" + handler.slots.size();
        }
        return moved;
    }

    private List<Integer> collectPlayerMoveSlotIds(ScreenHandler handler) {
        List<Integer> result = new ArrayList<Integer>();
        if (handler == null || handler.slots == null || client == null || client.player == null) {
            return result;
        }

        for (int slotId = 0; slotId < handler.slots.size(); slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (isPlayerInventorySlot(slot)) {
                addUniqueSlotId(result, slotId);
            }
        }

        if (!result.isEmpty()) {
            return result;
        }

        // Fallback 1: classic ScreenHandler layout used by vanilla containers and
        // the uploaded shalk.js: container slots first, then 27 main inventory slots,
        // then 9 hotbar slots.
        int totalSlots = handler.slots.size();
        int classicStart = Math.max(0, totalSlots - 36);
        for (int slotId = classicStart; slotId < totalSlots; slotId++) {
            addUniqueSlotId(result, slotId);
        }

        // Fallback 2: some server menus report rows differently. Also scan from the
        // detected container slot count to the end. Duplicates are ignored.
        int containerSlots = getContainerSlotsCount(handler);
        if (containerSlots >= 0 && containerSlots < totalSlots) {
            for (int slotId = containerSlots; slotId < totalSlots; slotId++) {
                addUniqueSlotId(result, slotId);
            }
        }

        return result;
    }

    private void addUniqueSlotId(List<Integer> slotIds, int slotId) {
        if (slotId < 0) {
            return;
        }
        Integer boxed = Integer.valueOf(slotId);
        if (!slotIds.contains(boxed)) {
            slotIds.add(boxed);
        }
    }

    private boolean isPlayerInventorySlot(Slot slot) {
        if (slot == null || client == null || client.player == null || client.player.getInventory() == null) {
            return false;
        }
        Object playerInventory = client.player.getInventory();
        Class<?> type = slot.getClass();
        while (type != null) {
            java.lang.reflect.Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (Throwable ignored) {
                fields = new java.lang.reflect.Field[0];
            }
            for (java.lang.reflect.Field field : fields) {
                try {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(slot);
                    if (value == playerInventory) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private int countOccupiedSlotsInOpenedContainer() {
        if (client == null || client.player == null) {
            return 0;
        }
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler.slots == null) {
            return 0;
        }
        int containerSlots = getContainerSlotsCount(handler);
        int occupied = 0;
        for (int slotId = 0; slotId < containerSlots && slotId < handler.slots.size(); slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (slot != null && !isEmpty(slot.getStack())) {
                occupied++;
            }
        }
        return occupied;
    }

    private int countFreeSlotsInOpenedContainer() {
        if (client == null || client.player == null) {
            return 0;
        }
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler.slots == null) {
            return 0;
        }
        int containerSlots = getContainerSlotsCount(handler);
        int free = 0;
        for (int slotId = 0; slotId < containerSlots && slotId < handler.slots.size(); slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (slot == null || isEmpty(slot.getStack())) {
                free++;
            }
        }
        return free;
    }

    private int getContainerSlotsCount(ScreenHandler handler) {
        if (handler == null || handler.slots == null) {
            return 0;
        }
        try {
            if (handler instanceof GenericContainerScreenHandler) {
                return ((GenericContainerScreenHandler) handler).getRows() * 9;
            }
        } catch (Throwable ignored) {
        }
        int guessed = handler.slots.size() - 36;
        if (guessed < 0) {
            return 0;
        }
        return Math.min(guessed, handler.slots.size());
    }

    private boolean isScriptContainerOpen() {
        if (client == null || client.currentScreen == null || client.player == null) {
            return false;
        }
        if (client.currentScreen instanceof ChatScreen) {
            return false;
        }

        Screen screen = client.currentScreen;
        String lower = screen.getTitle() == null ? "" : screen.getTitle().getString().toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (lower.contains("аукцион") || lower.contains("auction") || lower.contains("/ah") || lower.equals("ah") || lower.contains("поиск")) {
            return false;
        }
        if (lower.contains("malfix") || lower.contains("настрой") || lower.contains("бинд") || lower.contains("парсер")) {
            return false;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler == client.player.playerScreenHandler || handler.slots == null) {
            return false;
        }

        int containerSlots = getContainerSlotsCount(handler);
        if (containerSlots <= 0 || containerSlots > 54) {
            return false;
        }

        String screenClass = screen.getClass().getName().toLowerCase(Locale.ROOT);
        String handlerClass = handler.getClass().getName().toLowerCase(Locale.ROOT);
        if (screenClass.contains("shulker") || handlerClass.contains("shulker")) {
            return true;
        }
        if (lower.contains("шалкер") || lower.contains("shulker")) {
            return true;
        }

        // Many servers open placed shulkers as a plain GenericContainerScreen with a
        // custom/non-shulker title. If this controller has just selected and
        // right-clicked a hotbar shulker, accept the next 27/54-slot generic
        // container as the owned shulker. This is the missing piece that made
        // scan/PUT think no shulker had opened.
        boolean controllerJustOpenedShulker = currentHotbarSlot >= 0
                && currentOpenStartedAtMs > 0L
                && now() - currentOpenStartedAtMs <= Math.max(5_000L, MalfixTimings.SHULKER_OPEN_TIMEOUT_MS + 2_000L)
                && (state == State.WAIT_OPEN || state == State.WAIT_AFTER_MOVE || state == State.WAIT_CLOSE || state == State.WAIT_NEXT_SHULKER);
        if (controllerJustOpenedShulker && (containerSlots == 27 || containerSlots == 54)) {
            return true;
        }

        return false;
    }

    private boolean startEc(Mode mode, String reason, boolean shouldRestoreAuctionAfter, long time) {
        if (mode != Mode.EC_PUT && mode != Mode.EC_TAKE) {
            return false;
        }
        if (!isReady()) {
            lastReason = "ec_not_ready:" + reason;
            return false;
        }
        if (time < ecRetryBlockedUntilMs) {
            lastReason = "ec_retry_block_left=" + Math.max(0L, ecRetryBlockedUntilMs - time);
            return false;
        }
        if (mode == Mode.EC_PUT && ecFullKnown) {
            lastReason = "ec_known_full";
            return false;
        }
        if (mode == Mode.EC_TAKE && ecEmptyKnown) {
            lastReason = "ec_known_empty";
            return false;
        }

        currentMode = mode;
        candidateHotbarSlots = new ArrayList<Integer>();
        candidateIndex = 0;
        currentHotbarSlot = -1;
        restoreAuctionAfter = shouldRestoreAuctionAfter;
        lastMoved = 0;
        totalMoved = 0;
        emptySlotsBeforeMove = -1;
        attemptedMovesInCurrentShulker = 0;
        ecCommandTries = 0;
        runs++;
        lastStartedAtMs = time;
        lastReason = "ec_start_" + mode.name().toLowerCase(Locale.ROOT) + ":" + reason;

        if (closeChatScreenIfOpen()) {
            state = State.EC_WAIT_SCREEN_CLOSE;
            nextActionAtMs = time + 75L;
            lastReason = "ec_chat_closed_before_storage:" + reason;
            return true;
        }
        if (hasUserControlledScreenOpen()) {
            lastReason = "ec_user_screen_open_skip_start:" + reason;
            return false;
        }
        if (hasClosableScreenOpen()) {
            closeCurrentScreen();
            state = State.EC_WAIT_SCREEN_CLOSE;
            nextActionAtMs = time + MalfixTimings.SHULKER_CLOSE_AUCTION_WAIT_MS;
            return true;
        }
        openEc();
        return true;
    }

    private void openEc() {
        long time = now();
        ecCommandOwnedUntilMs = time + 30_000L;
        ecCommandTries++;
        sendChatCommand("/ec");
        state = State.EC_WAIT_OPEN;
        nextActionAtMs = time + EC_OPEN_WAIT_MS;
        lastReason = "ec_command_sent_try=" + ecCommandTries;
    }

    private boolean isEcContainerOpen() {
        try {
            if (client == null || client.currentScreen == null || client.player == null || client.currentScreen instanceof ChatScreen) {
                return false;
            }
            ScreenHandler handler = client.player.currentScreenHandler;
            if (handler == null || handler == client.player.playerScreenHandler || handler.slots == null) {
                return false;
            }
            int containerSlots = getContainerSlotsCount(handler);
            if (containerSlots <= 0 || containerSlots > 54) {
                return false;
            }
            Screen screen = client.currentScreen;
            String title = screen.getTitle() == null ? "" : screen.getTitle().getString().toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (title.contains("аукцион") || title.contains("auction") || title.contains("/ah") || title.contains("поиск")
                    || title.contains("malfix") || title.contains("настрой") || title.contains("парсер") || title.contains("бинд")) {
                return false;
            }
            if (title.contains("ender") || title.contains("эндер") || title.contains("эндер-сундук") || title.contains("ec")) {
                return now() < ecCommandOwnedUntilMs || state == State.EC_WAIT_OPEN || state == State.EC_WAIT_AFTER_MOVE || state == State.EC_WAIT_CLOSE;
            }
            return now() < ecCommandOwnedUntilMs
                    && (state == State.EC_WAIT_OPEN || state == State.EC_WAIT_AFTER_MOVE || state == State.EC_WAIT_CLOSE)
                    && (containerSlots == 27 || containerSlots == 54);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldFallbackToEcAfterShulkers() {
        if (ecFullKnown || now() < ecRetryBlockedUntilMs) {
            return false;
        }
        return hasAnyHotbarShulker() && countKnownFullShulkers() >= countHotbarShulkers();
    }

    private List<Integer> findHotbarShulkers(long time) {
        return findHotbarShulkersForMode(time, Mode.PUT);
    }

    private List<Integer> findHotbarShulkersForMode(long time, Mode mode) {
        List<Integer> result = new ArrayList<Integer>();
        if (client == null || client.player == null || client.player.getInventory() == null) {
            return result;
        }
        for (int i = 0; i < 9; i++) {
            if (mode != Mode.SCAN && time < shulkerCooldownUntilMs[i]) {
                continue;
            }
            if (mode == Mode.TAKE) {
                if (knownEmptyShulkers[i]) {
                    continue;
                }
            } else if (mode == Mode.PUT && knownFullShulkers[i]) {
                continue;
            }
            ItemStack stack = client.player.getInventory().getStack(i);
            if (isShulker(stack)) {
                result.add(Integer.valueOf(i));
            }
        }
        return result;
    }

    private void maybeRescanKnownFullShulkers(long time, String reason) {
        boolean manualReason = reason != null && reason.toLowerCase(Locale.ROOT).contains("manual");
        if (!manualReason && lastKnownFullRescanAtMs > 0L && time - lastKnownFullRescanAtMs < MalfixTimings.SHULKER_RESCAN_KNOWN_FULL_MS) {
            return;
        }
        Arrays.fill(knownFullShulkers, false);
        lastKnownFullRescanAtMs = time;
    }

    private void maybeRescanKnownEmptyShulkers(long time, String reason) {
        boolean manualReason = reason != null && reason.toLowerCase(Locale.ROOT).contains("manual");
        if (!manualReason && lastKnownFullRescanAtMs > 0L && time - lastKnownFullRescanAtMs < MalfixTimings.SHULKER_RESCAN_KNOWN_FULL_MS) {
            return;
        }
        Arrays.fill(knownEmptyShulkers, false);
        lastKnownFullRescanAtMs = time;
    }

    private boolean selectHotbarSlot(int slot) {
        if (client == null || client.player == null || client.player.getInventory() == null) {
            return false;
        }
        if (slot < 0 || slot > 8) {
            return false;
        }
        client.player.getInventory().selectedSlot = slot;
        try {
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private boolean closeChatScreenIfOpen() {
        try {
            if (client != null && client.currentScreen instanceof ChatScreen) {
                client.setScreen(null);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean hasClosableScreenOpen() {
        return isAuctionScreenOpen() || isScriptContainerOpen() || isEcContainerOpen();
    }

    private boolean hasUserControlledScreenOpen() {
        if (client == null || client.currentScreen == null || client.currentScreen instanceof ChatScreen) {
            return false;
        }
        return !hasClosableScreenOpen();
    }

    private boolean hasNonChatScreenOpen() {
        // Step 23.24: keep legacy call sites compiling, but do NOT treat every
        // non-chat screen as closable. Returning hasClosableScreenOpen() preserves
        // the menu guard: only /ah and script-owned storage containers may be closed.
        return hasClosableScreenOpen();
    }

    private boolean isAuctionScreenOpen() {
        try {
            if (client == null || client.currentScreen == null) {
                return false;
            }
            Screen screen = client.currentScreen;
            String lower = screen.getTitle() == null ? "" : screen.getTitle().getString().toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
            String screenClass = screen.getClass().getName().toLowerCase(Locale.ROOT);
            return lower.contains("аукцион")
                    || lower.contains("auction")
                    || lower.contains("/ah")
                    || lower.equals("ah")
                    || lower.contains("поиск")
                    || lower.contains("search")
                    || lower.contains("результат")
                    || screenClass.contains("auction");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void closeCurrentScreen() {
        try {
            if (client == null) {
                return;
            }
            if (!hasClosableScreenOpen()) {
                lastReason = "close_skipped_user_screen=" + compactScreenName();
                return;
            }
            if (client.player != null) {
                client.player.closeHandledScreen();
            } else {
                client.setScreen(null);
            }
        } catch (Throwable ignored) {
            try {
                if (client != null && hasClosableScreenOpen()) {
                    client.setScreen(null);
                }
            } catch (Throwable ignored2) {
            }
        }
    }

    private void sendChatCommand(String command) {
        McChat.send(client, command);
    }

    private void markCurrentShulkerFull() {
        if (currentHotbarSlot >= 0 && currentHotbarSlot <= 8) {
            knownFullShulkers[currentHotbarSlot] = true;
            knownEmptyShulkers[currentHotbarSlot] = false;
        }
    }

    private void markCurrentShulkerEmpty() {
        if (currentHotbarSlot >= 0 && currentHotbarSlot <= 8) {
            knownEmptyShulkers[currentHotbarSlot] = true;
            knownFullShulkers[currentHotbarSlot] = false;
        }
    }

    private void setCooldown(int slot) {
        if (slot >= 0 && slot <= 8) {
            shulkerCooldownUntilMs[slot] = now() + MalfixTimings.SHULKER_RETRY_COOLDOWN_MS;
        }
    }

    private void clearCooldown(int slot) {
        if (slot >= 0 && slot <= 8) {
            shulkerCooldownUntilMs[slot] = 0L;
        }
    }

    private boolean hasAnyHotbarShulker() {
        return countHotbarShulkers() > 0;
    }

    private int countKnownFullShulkers() {
        int count = 0;
        for (boolean value : knownFullShulkers) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    private int countKnownEmptyShulkers() {
        int count = 0;
        for (boolean value : knownEmptyShulkers) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    private boolean isForceFullInventoryReason(String reason) {
        if (reason == null) {
            return false;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("force") || lower.contains("full_inventory") || lower.contains("inventory_full");
    }

    private boolean isReady() {
        return client != null && client.player != null && client.world != null && client.interactionManager != null;
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    private boolean isShulker(ItemStack stack) {
        if (isEmpty(stack)) {
            return false;
        }
        String id;
        try {
            id = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            id = "";
        }
        return id.contains("shulker_box") && !id.contains("spawn_egg");
    }

    private boolean isFullInventoryMessage(String lower) {
        return lower.contains("у вас полный инвентарь")
                || lower.contains("инвентарь полон")
                || lower.contains("полный инвентарь")
                || lower.contains("предмет перенесен в хранилище");
    }

    private boolean isAuctionRentMessage(String lower) {
        return lower.contains("/ah rent")
                || lower.contains("аренда слотов")
                || lower.contains("нет слотов продажи")
                || lower.contains("лимит продаж")
                || lower.contains("освободите хранилище");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "").toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }

    private void log(String message) {
        try {
            System.out.println("[MAB] shulker-script " + String.valueOf(message));
        } catch (Throwable ignored) {
        }
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
