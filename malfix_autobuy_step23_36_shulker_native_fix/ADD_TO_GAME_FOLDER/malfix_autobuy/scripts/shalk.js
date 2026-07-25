var MinecraftClient = Java.type("net.minecraft.class_310");
var Hand = Java.type("net.minecraft.class_1268");
var SlotActionType = Java.type("net.minecraft.class_1713");
var UpdateSelectedSlotC2SPacket = Java.type("net.minecraft.class_2868");
var SpookyBuy = Java.type("ru.nedan.spookybuy.SpookyBuy");
var System = Java.type("java.lang.System");
var Registry = Java.type("net.minecraft.class_2378");

var mc = MinecraftClient.method_1551();

var EMPTY_SLOTS_THRESHOLD = 0;
var TAKE_BACK_IF_FREE_SLOTS_GT = 20;
var MOVE_SLOTS_LIMIT = 9;

var OPEN_AUCTION_CLOSE_WAIT_MS = 50;
var OPEN_WAIT_MS = 300;
var AFTER_MOVE_WAIT_MS = 150;
var CLOSE_WAIT_MS = 150;
var TAKE_COOLDOWN_MS = 4000;
var RESCAN_SHULKERS_EVERY_MS = 5 * 60 * 1000;

var MODE_PUT = "PUT";
var MODE_TAKE = "TAKE";
var MODE_SCAN = "SCAN";
var MODE_EC_PUT = "EC_PUT";
var MODE_EC_TAKE = "EC_TAKE";

var EC_COMMAND = "/ec";
var EC_OPEN_WAIT_MS = 500;
var EC_COMMAND_RETRY_MS = 1200;
var EC_MAX_COMMAND_TRIES = 3;
var EC_RETRY_COOLDOWN_MS = 10000;

var SHULKER_RETRY_COOLDOWN_MS = 3000;
var AUCTION_RENT_BLOCK_MS = 5000;
var auctionRentBlockedUntil = 0;

var state = "IDLE";
var nextActionAt = 0;
var wasAutoBuyEnabled = false;
var forceByFullInventoryMessage = false;

var shulkerSlots = [];
var currentShulkerIndex = 0;
var currentShulkerSlot = -1;
var currentMode = MODE_PUT;
var lastPutFinishedAt = 0;
var lastRescanAt = 0;

var ecCommandTries = 0;
var ecEmptyKnown = false;
var ecFullKnown = false;
var ecRetryBlockedUntil = 0;

var knownFullShulkers = {};
var knownEmptyShulkers = {};
var shulkerRetryCooldowns = {};

function now() {
    return System.currentTimeMillis();
}

function log(msg) {
    try {
        print.accept(String(msg));
    } catch (e) {
    }
}

function isReady() {
    return mc != null && mc.field_1724 != null && mc.field_1687 != null && mc.field_1761 != null;
}

function playerObj() {
    return mc.field_1724;
}

function invObj() {
    return playerObj().field_7514;
}

function isEmptyStack(stack) {
    return stack == null || stack.method_7960();
}

function getItemId(stack) {
    try {
        if (isEmptyStack(stack)) return "";
        var item = stack.method_7909();
        if (item == null) return "";
        var id = Registry.field_11142.method_10221(item);
        if (id == null) return "";
        return String(id).toLowerCase();
    } catch (e) {
        return "";
    }
}

function isShulkerBox(stack) {
    var s = getItemId(stack);
    if (s.indexOf("spawn_egg") !== -1) return false;
    return s.indexOf("shulker_box") !== -1;
}

function isShulker(stack) {
    return isShulkerBox(stack);
}

function isShulkerOnCooldown(slot) {
    var until = shulkerRetryCooldowns[slot];
    if (until == null) return false;
    return now() < until;
}

function setShulkerCooldown(slot) {
    shulkerRetryCooldowns[slot] = now() + SHULKER_RETRY_COOLDOWN_MS;
}

function clearShulkerCooldown(slot) {
    delete shulkerRetryCooldowns[slot];
}

function countEmptySlots() {
    var inv = invObj();
    var empty = 0;

    for (var i = 0; i < 36; i++) {
        var stack = inv.method_5438(i);
        if (isEmptyStack(stack)) empty++;
    }

    return empty;
}

function inventoryIsFullEnough() {
    return countEmptySlots() <= EMPTY_SLOTS_THRESHOLD;
}

function shouldTakeBackFromStorage() {
    return now() - lastPutFinishedAt >= TAKE_COOLDOWN_MS &&
           countEmptySlots() > TAKE_BACK_IF_FREE_SLOTS_GT;
}

function pauseAutoBuy() {
    try {
        var sb = SpookyBuy.getInstance();
        wasAutoBuyEnabled = sb.isState();
        if (wasAutoBuyEnabled) sb.setState(false);
    } catch (e) {
        wasAutoBuyEnabled = false;
    }
}

function resumeAutoBuy() {
    try {
        if (wasAutoBuyEnabled) {
            SpookyBuy.getInstance().setState(true);
        }
    } catch (e) {
    }
    wasAutoBuyEnabled = false;
}

function selectHotbarSlot(slot) {
    var p = playerObj();
    p.field_7514.field_7545 = slot;
    p.field_3944.method_2883(new UpdateSelectedSlotC2SPacket(slot));
}

function rightClickMainHand() {
    mc.field_1761.method_2919(playerObj(), mc.field_1687, Hand.field_5808);
}

function isContainerOpen() {
    return mc.field_1755 != null && playerObj() != null && playerObj().field_7512 != null;
}

function closeCurrentScreen() {
    if (playerObj() != null) {
        playerObj().method_7346();
    }
}

function quickMoveSlot(syncId, slotId) {
    var quickMove = SlotActionType.values()[1];
    mc.field_1761.method_2906(syncId, slotId, 0, quickMove, playerObj());
}

function getContainerSlotsCount() {
    var handler = playerObj().field_7512;
    if (handler == null) return 0;

    var totalSlots = handler.field_7761.size();
    if (totalSlots < 36) return 0;

    return totalSlots - 36;
}

function countFreeSlotsInOpenedContainer() {
    var handler = playerObj().field_7512;
    if (handler == null) return 0;

    var containerSlots = getContainerSlotsCount();
    if (containerSlots <= 0) return 0;

    var free = 0;
    for (var slotId = 0; slotId < containerSlots; slotId++) {
        var slotObj = handler.method_7611(slotId);
        if (slotObj == null) continue;

        var stack = slotObj.method_7677();
        if (isEmptyStack(stack)) free++;
    }

    return free;
}

function countOccupiedSlotsInOpenedContainer() {
    var handler = playerObj().field_7512;
    if (handler == null) return 0;

    var containerSlots = getContainerSlotsCount();
    if (containerSlots <= 0) return 0;

    var occupied = 0;
    for (var slotId = 0; slotId < containerSlots; slotId++) {
        var slotObj = handler.method_7611(slotId);
        if (slotObj == null) continue;

        var stack = slotObj.method_7677();
        if (!isEmptyStack(stack)) occupied++;
    }

    return occupied;
}

function moveOccupiedSlotsFromPlayerIntoOpenedContainer(limit) {
    var p = playerObj();
    var handler = p.field_7512;
    if (handler == null) return 0;

    var totalSlots = handler.field_7761.size();
    if (totalSlots < 36) return 0;

    var playerStart = totalSlots - 36;
    var playerEnd = totalSlots - 1;
    var hotbarStart = totalSlots - 9;

    var selectedHotbarIndex = p.field_7514.field_7545;
    var selectedContainerSlot = hotbarStart + selectedHotbarIndex;

    var moved = 0;

    for (var slotId = playerStart; slotId <= playerEnd; slotId++) {
        if (moved >= limit) break;
        if (slotId == selectedContainerSlot) continue;

        var slotObj = handler.method_7611(slotId);
        if (slotObj == null) continue;

        var stack = slotObj.method_7677();
        if (isEmptyStack(stack)) continue;
        if (isShulker(stack)) continue;

        try {
            quickMoveSlot(handler.field_7763, slotId);
            moved++;
        } catch (e) {
        }
    }

    return moved;
}

function moveOccupiedSlotsFromOpenedContainerToPlayer(limit) {
    var p = playerObj();
    var handler = p.field_7512;
    if (handler == null) return 0;

    var containerSlots = getContainerSlotsCount();
    if (containerSlots <= 0) return 0;

    var moved = 0;

    for (var slotId = 0; slotId < containerSlots; slotId++) {
        if (moved >= limit) break;

        var slotObj = handler.method_7611(slotId);
        if (slotObj == null) continue;

        var stack = slotObj.method_7677();
        if (isEmptyStack(stack)) continue;
        if (isShulker(stack)) continue;

        try {
            quickMoveSlot(handler.field_7763, slotId);
            moved++;
        } catch (e) {
        }
    }

    return moved;
}

function findShulkersInHotbarForMode(mode) {
    var inv = invObj();
    var result = [];

    for (var i = 0; i < 9; i++) {
        var stack = inv.method_5438(i);
        if (!isShulker(stack)) continue;
        if (isShulkerOnCooldown(i)) continue;

        if (mode === MODE_PUT && knownFullShulkers[i] === true) continue;
        if (mode === MODE_TAKE && knownEmptyShulkers[i] === true) continue;

        result.push(i);
    }

    return result;
}

function countKnownFullShulkersInHotbar() {
    var count = 0;
    for (var i = 0; i < 9; i++) {
        if (knownFullShulkers[i] === true) count++;
    }
    return count;
}

function markShulkerFull(slot) {
    knownFullShulkers[slot] = true;
    delete knownEmptyShulkers[slot];
}

function markShulkerEmpty(slot) {
    knownEmptyShulkers[slot] = true;
    delete knownFullShulkers[slot];
}

function markShulkerHasSpace(slot) {
    delete knownFullShulkers[slot];
}

function markShulkerHasItems(slot) {
    delete knownEmptyShulkers[slot];
}

function isEcBlocked() {
    return now() < ecRetryBlockedUntil;
}

function markEcEmpty() {
    ecEmptyKnown = true;
    ecRetryBlockedUntil = now() + EC_RETRY_COOLDOWN_MS;
}

function markEcHasItems() {
    ecEmptyKnown = false;
}

function markEcFull() {
    ecFullKnown = true;
}

function markEcHasSpace() {
    ecFullKnown = false;
}

function resetState() {
    state = "IDLE";
    nextActionAt = 0;
    forceByFullInventoryMessage = false;
    shulkerSlots = [];
    currentShulkerIndex = 0;
    currentShulkerSlot = -1;
    currentMode = MODE_PUT;
    ecCommandTries = 0;
}

function failAndRestore(msg) {
    try {
        if (isContainerOpen()) closeCurrentScreen();
    } catch (e) {
    }
    resumeAutoBuy();
    resetState();
    if (msg != null) log(msg);
}

function finishNoSuitableStorage() {
    if (currentMode === MODE_SCAN) {
        lastRescanAt = now();
    }
    resumeAutoBuy();
    resetState();
}

function openCurrentShulker() {
    if (currentShulkerIndex >= shulkerSlots.length) {
        finishNoSuitableStorage();
        return;
    }

    currentShulkerSlot = shulkerSlots[currentShulkerIndex];
    setShulkerCooldown(currentShulkerSlot);
    selectHotbarSlot(currentShulkerSlot);
    rightClickMainHand();

    state = "WAIT_OPEN";
    nextActionAt = now() + OPEN_WAIT_MS;
}

function startShulkerSequence(mode) {
    if (!isReady()) return;
    if (state !== "IDLE") return;
    if (now() < auctionRentBlockedUntil) return;

    shulkerSlots = findShulkersInHotbarForMode(mode);
    currentShulkerIndex = 0;
    currentShulkerSlot = -1;
    currentMode = mode;

    if (shulkerSlots.length === 0) return;

    pauseAutoBuy();

    if (mc.field_1755 != null) {
        closeCurrentScreen();
        state = "WAIT_SCREEN_CLOSE";
        nextActionAt = now() + OPEN_AUCTION_CLOSE_WAIT_MS;
        return;
    }

    openCurrentShulker();
}

function openEc() {
    try {
        chat.accept(EC_COMMAND);
    } catch (e) {
        try {
            chat(EC_COMMAND);
        } catch (e2) {
        }
    }
}

function startEcSequence(mode) {
    if (!isReady()) return;
    if (state !== "IDLE") return;
    if (isEcBlocked()) return;
    if (now() < auctionRentBlockedUntil) return;
    if (mode === MODE_EC_TAKE && ecEmptyKnown) return;

    currentMode = mode;
    ecCommandTries = 0;

    pauseAutoBuy();

    if (mc.field_1755 != null) {
        closeCurrentScreen();
        state = "EC_WAIT_SCREEN_CLOSE";
        nextActionAt = now() + OPEN_AUCTION_CLOSE_WAIT_MS;
        return;
    }

    openEc();
    ecCommandTries++;
    state = "EC_WAIT_OPEN";
    nextActionAt = now() + EC_OPEN_WAIT_MS;
}

on.accept("ru.nedan.neverapi.event.impl.EventMessage", function (e) {
    try {
        if (e.isSend()) return;

        var msg = String(e.getMessage()).toLowerCase();

        var isAuctionRentMessage =
            msg.indexOf("/ah rent") !== -1 ||
            msg.indexOf("не удалось выставить") !== -1 ||
            msg.indexOf("освободите хранилище") !== -1;

        if (isAuctionRentMessage) {
            auctionRentBlockedUntil = now() + AUCTION_RENT_BLOCK_MS;
            forceByFullInventoryMessage = false;

            if (state !== "IDLE") {
                failAndRestore(null);
            }
            return;
        }

        if (msg.indexOf("у вас полный инвентарь") !== -1) {
            forceByFullInventoryMessage = true;

            if (state !== "IDLE" && currentMode !== MODE_PUT && currentMode !== MODE_EC_PUT) {
                failAndRestore(null);
            }

            if (state === "IDLE") {
                if (countKnownFullShulkersInHotbar() >= 3 && !isEcBlocked()) {
                    startEcSequence(MODE_EC_PUT);
                } else {
                    startShulkerSequence(MODE_PUT);
                }
            }
        }
    } catch (err) {
    }
});

on.accept("ru.nedan.neverapi.event.impl.EventPlayerTick", function (e) {
    try {
        if (!isReady()) {
            resetState();
            return;
        }

        var tickNow = now();

        if (tickNow < auctionRentBlockedUntil) {
            forceByFullInventoryMessage = false;
            if (state !== "IDLE") {
                failAndRestore(null);
            }
            return;
        }

        if (tickNow < nextActionAt) return;

        if (state === "IDLE") {
            var invFullNow = forceByFullInventoryMessage || inventoryIsFullEnough();

            if (invFullNow) {
                if (countKnownFullShulkersInHotbar() >= 3 && !isEcBlocked()) {
                    startEcSequence(MODE_EC_PUT);
                } else {
                    startShulkerSequence(MODE_PUT);
                }
                return;
            }

            if (mc.field_1755 != null) return;

            if (tickNow - lastRescanAt >= RESCAN_SHULKERS_EVERY_MS) {
                startShulkerSequence(MODE_SCAN);
                return;
            }

            if (shouldTakeBackFromStorage()) {
                if (findShulkersInHotbarForMode(MODE_TAKE).length > 0) {
                    startShulkerSequence(MODE_TAKE);
                } else if (!ecEmptyKnown && !isEcBlocked()) {
                    startEcSequence(MODE_EC_TAKE);
                }
                return;
            }

            return;
        }

        if (state === "WAIT_SCREEN_CLOSE") {
            if (mc.field_1755 != null) {
                closeCurrentScreen();
                nextActionAt = tickNow + OPEN_AUCTION_CLOSE_WAIT_MS;
                return;
            }

            openCurrentShulker();
            return;
        }

        if (state === "EC_WAIT_SCREEN_CLOSE") {
            if (mc.field_1755 != null) {
                closeCurrentScreen();
                nextActionAt = tickNow + OPEN_AUCTION_CLOSE_WAIT_MS;
                return;
            }

            openEc();
            ecCommandTries++;
            state = "EC_WAIT_OPEN";
            nextActionAt = tickNow + EC_OPEN_WAIT_MS;
            return;
        }

        if (state === "WAIT_OPEN") {
            if (!isContainerOpen()) {
                if (currentShulkerSlot !== -1) {
                    setShulkerCooldown(currentShulkerSlot);
                }

                currentShulkerIndex++;
                if (currentShulkerIndex >= shulkerSlots.length) {
                    finishNoSuitableStorage();
                    return;
                }
                openCurrentShulker();
                return;
            }

            if (currentMode === MODE_PUT) {
                var freeSlots = countFreeSlotsInOpenedContainer();

                if (freeSlots <= 0) {
                    markShulkerFull(currentShulkerSlot);
                    closeCurrentScreen();
                    state = "WAIT_NEXT_SHULKER";
                    nextActionAt = tickNow + CLOSE_WAIT_MS;
                    return;
                }

                var moveLimit = MOVE_SLOTS_LIMIT;
                if (freeSlots < moveLimit) moveLimit = freeSlots;

                var movedPut = moveOccupiedSlotsFromPlayerIntoOpenedContainer(moveLimit);

                if (movedPut > 0) {
                    markShulkerHasSpace(currentShulkerSlot);
                    markShulkerHasItems(currentShulkerSlot);
                    clearShulkerCooldown(currentShulkerSlot);
                } else {
                    setShulkerCooldown(currentShulkerSlot);
                }
            } else if (currentMode === MODE_SCAN) {
                var freeScan = countFreeSlotsInOpenedContainer();
                var occupiedScan = countOccupiedSlotsInOpenedContainer();

                if (occupiedScan <= 0) {
                    markShulkerEmpty(currentShulkerSlot);
                } else if (freeScan <= 0) {
                    markShulkerFull(currentShulkerSlot);
                } else {
                    markShulkerHasSpace(currentShulkerSlot);
                    markShulkerHasItems(currentShulkerSlot);
                }

                clearShulkerCooldown(currentShulkerSlot);

                closeCurrentScreen();
                state = "WAIT_NEXT_SHULKER";
                nextActionAt = tickNow + CLOSE_WAIT_MS;
                return;
            } else {
                var occupiedSlots = countOccupiedSlotsInOpenedContainer();

                if (occupiedSlots <= 0) {
                    markShulkerEmpty(currentShulkerSlot);
                    closeCurrentScreen();
                    state = "WAIT_NEXT_SHULKER";
                    nextActionAt = tickNow + CLOSE_WAIT_MS;
                    return;
                }

                var takeLimit = MOVE_SLOTS_LIMIT;
                if (occupiedSlots < takeLimit) takeLimit = occupiedSlots;

                var movedTake = moveOccupiedSlotsFromOpenedContainerToPlayer(takeLimit);

                if (movedTake > 0) {
                    markShulkerHasItems(currentShulkerSlot);
                    markShulkerHasSpace(currentShulkerSlot);
                    clearShulkerCooldown(currentShulkerSlot);
                } else {
                    setShulkerCooldown(currentShulkerSlot);
                }
            }

            state = "WAIT_AFTER_MOVE";
            nextActionAt = tickNow + AFTER_MOVE_WAIT_MS;
            return;
        }

        if (state === "EC_WAIT_OPEN") {
            if (!isContainerOpen()) {
                if (ecCommandTries < EC_MAX_COMMAND_TRIES) {
                    openEc();
                    ecCommandTries++;
                    nextActionAt = tickNow + EC_COMMAND_RETRY_MS;
                    return;
                }

                markEcEmpty();
                finishNoSuitableStorage();
                return;
            }

            if (currentMode === MODE_EC_PUT) {
                var freeSlotsEc = countFreeSlotsInOpenedContainer();

                if (freeSlotsEc <= 0) {
                    markEcFull();
                    closeCurrentScreen();
                    state = "EC_WAIT_CLOSE";
                    nextActionAt = tickNow + CLOSE_WAIT_MS;
                    return;
                }

                var moveLimitEcPut = MOVE_SLOTS_LIMIT;
                if (freeSlotsEc < moveLimitEcPut) moveLimitEcPut = freeSlotsEc;

                var movedEcPut = moveOccupiedSlotsFromPlayerIntoOpenedContainer(moveLimitEcPut);

                if (movedEcPut > 0) {
                    markEcHasSpace();
                    ecEmptyKnown = false;
                }

                state = "EC_WAIT_AFTER_MOVE";
                nextActionAt = tickNow + AFTER_MOVE_WAIT_MS;
                return;
            }

            if (currentMode === MODE_EC_TAKE) {
                var occupiedSlotsEc = countOccupiedSlotsInOpenedContainer();

                if (occupiedSlotsEc <= 0) {
                    markEcEmpty();
                    closeCurrentScreen();
                    state = "EC_WAIT_CLOSE";
                    nextActionAt = tickNow + CLOSE_WAIT_MS;
                    return;
                }

                var moveLimitEcTake = MOVE_SLOTS_LIMIT;
                if (occupiedSlotsEc < moveLimitEcTake) moveLimitEcTake = occupiedSlotsEc;

                var movedEcTake = moveOccupiedSlotsFromOpenedContainerToPlayer(moveLimitEcTake);

                if (movedEcTake > 0) {
                    markEcHasItems();
                } else {
                    markEcEmpty();
                    closeCurrentScreen();
                    state = "EC_WAIT_CLOSE";
                    nextActionAt = tickNow + CLOSE_WAIT_MS;
                    return;
                }

                state = "EC_WAIT_AFTER_MOVE";
                nextActionAt = tickNow + AFTER_MOVE_WAIT_MS;
                return;
            }

            return;
        }

        if (state === "WAIT_NEXT_SHULKER") {
            currentShulkerIndex++;
            if (currentShulkerIndex >= shulkerSlots.length) {
                finishNoSuitableStorage();
                return;
            }

            if (mc.field_1755 != null) {
                closeCurrentScreen();
                nextActionAt = tickNow + OPEN_AUCTION_CLOSE_WAIT_MS;
                return;
            }

            openCurrentShulker();
            return;
        }

        if (state === "WAIT_AFTER_MOVE") {
            state = "WAIT_CLOSE";
            nextActionAt = tickNow + CLOSE_WAIT_MS;
            return;
        }

        if (state === "EC_WAIT_AFTER_MOVE") {
            state = "EC_WAIT_CLOSE";
            nextActionAt = tickNow + CLOSE_WAIT_MS;
            return;
        }

        if (state === "EC_WAIT_CLOSE") {
            if (isContainerOpen()) {
                closeCurrentScreen();
            }

            if (currentMode === MODE_EC_PUT) {
                lastPutFinishedAt = tickNow;
            }

            resumeAutoBuy();
            resetState();
            return;
        }

        if (state === "WAIT_CLOSE") {
            if (isContainerOpen()) {
                closeCurrentScreen();
            }

            if (currentMode === MODE_PUT) {
                lastPutFinishedAt = tickNow;
            }

            if (currentMode === MODE_SCAN) {
                lastRescanAt = tickNow;
            }

            resumeAutoBuy();
            resetState();
            return;
        }
    } catch (err) {
        failAndRestore("Shulker script error: " + err);
    }
});