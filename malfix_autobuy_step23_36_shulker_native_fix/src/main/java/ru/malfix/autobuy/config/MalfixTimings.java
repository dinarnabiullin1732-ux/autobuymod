package ru.malfix.autobuy.config;

/**
 * Timings are aligned with the Never/SpookyBuy action gate where possible.
 * Names are intentionally close to the old timing keys so the mapping stays obvious.
 */
public final class MalfixTimings {
    private MalfixTimings() {
    }

    public static final long AB_UPDATE_MS = 300L;
    public static final long AB_BUY_MS = 1L;
    public static final long AB_RESELL_ITEM_MS = 300L;
    public static final long AB_RESELL_MS = 90_000L;
    public static final long AB_AUCTION_REFRESH_DISABLED_MS = 604_800_000L;

    public static final long AUTOSELL_OPEN_MS = 1_000L;
    public static final long AUTOSELL_UNSTACK_MS = 200L;
    public static final long UNSTACK_SELL_SPLIT_MS = 350L;
    public static final long UNSTACK_PREPARE_WAIT_MS = 600L;
    // NeverBuy uses a 200ms action gate for slot actions. Use the same base cadence
    // for the sell cycle; keep a short result timeout so one missed chat message does
    // not freeze the loop for several seconds.
    public static final long AUTOSELL_SELL_MS = 200L;
    public static final long SELLER_RESULT_WAIT_TIMEOUT_MS = 900L;

    // Step 22.77: rollback the global Step 22.74 storage time-block.
    // /ah rent should stop the current sell/storage continuation, not disable storage
    // draining for the next buy/storage round. Keep the constant as 0 for debug/status
    // compatibility so getSellLimitStorageBlockLeftMs() always reports no active block.
    public static final long SELL_LIMIT_STORAGE_RECHECK_BLOCK_MS = 0L;

    public static final long SMART_REOPEN_TICK_MS = 250L;
    public static final long SMART_REOPEN_REFRESH_FAIL_MS = 1_200L;
    public static final long SMART_REOPEN_MIN_SCREEN_AGE_MS = 1_500L;
    public static final long SMART_REOPEN_STUCK_MS = 4_500L;
    public static final int SMART_REOPEN_REQUIRED_SAME_CHECKS = 4;
    public static final long SMART_REOPEN_COOLDOWN_MS = 9_000L;

    public static final long STORAGE_OPEN_WAIT_MS = 220L;
    public static final long STORAGE_TAKE_WAIT_MS = 180L;
    public static final long STORAGE_ONE_TAKE_MS = 260L;
    public static final long STORAGE_EMPTY_RECHECK_MS = 420L;
    public static final int STORAGE_EMPTY_RECHECKS = 3;
    public static final long SELLER_RETURN_AUCTION_MS = 900L;

    // FullAuto timed mode: no cycle/buy-count stop.
    // One round is: 90 seconds auction refresh+buy -> storage relist -> sell until /ah rent or no matching items -> /ah -> repeat.
    public static final long FULL_AUTO_BUY_TIME_MS = 90_000L;
    public static final int FULL_AUTO_STORAGE_TAKE_MAX = 36;
    public static final int FULL_AUTO_SELL_MAX = 0; // 0 means sell until /ah rent / no matching items.
    public static final long FULL_AUTO_LOOP_DELAY_MS = 300L;

    // SellOnly mode: stand idle with auction closed, every 30s open storage, take items and sell only.
    public static final long SELL_ONLY_INTERVAL_MS = 30_000L;
    public static final int SELL_ONLY_STORAGE_TAKE_MAX = 36;

    // Kept only for manual old commands/backward compatibility. FullAuto key no longer uses these counters.
    public static final int FULL_AUTO_LOOPS = 120;
    public static final int FULL_AUTO_BUY_CYCLES = 300;
    public static final int FULL_AUTO_BUY_MAX = 64;

    // The old jar used 1200ms as a smart-reopen/no-change guard. In this new loop it was
    // being used as an active refresh wait, which looked like a freeze. Keep no-change short.
    public static final long FULL_AUTO_REFRESH_TIMEOUT_MS = 300L;
    public static final int FULL_AUTO_MAX_REFRESH_FAIL_STREAK = 20;

    // If a buy click does not produce chat/fingerprint confirmation, do not freeze refresh
    // for the old 1500ms guard. Continue the auction loop quickly and let the next
    // refresh decide the current page state.
    public static final long CONTROLLED_BUY_RESULT_TIMEOUT_MS = 900L;

    // Step 22.61: auction refresh cadence is 300ms, the minimum stable server delay.
    // Step 22.84: seller/autosell command cadence follows Never's 200ms action gate.
    // Step 22.60 restored reliable fingerprint-based refresh; keep that reliability and
    // pace the next refresh click from the previous click time, not from scan completion.
    public static final long AUCTION_REFRESH_SETTLE_MS = 45L; // kept for compatibility; not used by Step 22.60/22.61 refresh flow

    // Step 22.57: same-slot buy retry was removed. Keep only the poll interval for
    // chat/fingerprint confirmation after the single real buy click.
    public static final long BUY_RESULT_POLL_MS = 45L;

    // FullAuto anti-freeze: after a buy the server may keep the old auction window
    // alive and stop applying refresh results. If several refresh clicks keep the
    // exact same fingerprint, close / reopen /ah and continue the buy loop. Step 22.63 raises the post-buy threshold from 2 to 5 refresh clicks to avoid accidental reopen.
    public static final long FULL_AUTO_REOPEN_CLOSE_WAIT_MS = 250L;
    public static final long FULL_AUTO_REOPEN_OPEN_WAIT_MS = 700L;
    public static final long FULL_AUTO_REOPEN_COOLDOWN_MS = 2_500L;
    public static final long FULL_AUTO_POST_BUY_STUCK_WINDOW_MS = 12_000L;
    public static final int FULL_AUTO_POST_BUY_NO_CHANGE_REOPEN_STREAK = 5;
    public static final int FULL_AUTO_GENERAL_NO_CHANGE_REOPEN_STREAK = 8;

    // Step 22.73: any real buy click that times out while the auction page is still
    // unchanged means this client window is stale/frozen for buying. Do not click the
    // same profitable-looking lot again; close/reopen /ah and continue the loop.
    // Keep the old constant names for compact debug/status compatibility.
    public static final int FULL_AUTO_BUY_TIMEOUT_REOPEN_STREAK = 1;
    public static final long FULL_AUTO_BUY_TIMEOUT_REOPEN_WINDOW_MS = 8_000L;

    public static final int FULL_AUTO_REOPEN_MAX_ATTEMPTS = 4;


    // Parser defaults from the old AutoSetup idea: search item, read current min price,
    // then set buy/sell prices as percentages of that unit price.
    public static final int PARSER_BUY_PERCENT = 80;
    public static final int PARSER_SELL_PERCENT = 90;
    public static final long PARSER_OPEN_WAIT_MS = 650L;
    public static final long PARSER_BETWEEN_ITEMS_MS = 350L;
    public static final int PARSER_MAX_RETRIES = 2;

    // Anti-AFK/rejoin from the old autobuy: /hub -> /an<current> -> /ah.
    // Step 22.64: enabling autobuy/fullauto only arms a fresh 5-minute timer.
    // It must not immediately /hub -> /an -> /ah on start; immediate rejoin is only for manual `.mab antiafk test/now`.
    // Step 22.76: the real AFK state is detected from server chat messages like
    // "Данная команда недоступна в режиме AFK" / "Недопустимо нажимать в режиме AFK".
    // On that message Anti-AFK immediately pauses automation ticks and performs rejoin.
    public static final long ANTI_AFK_INTERVAL_MS = 300_000L;
    public static final long ANTI_AFK_CHAT_TRIGGER_COOLDOWN_MS = 12_000L;
    public static final long ANTI_AFK_HUB_WAIT_MS = 900L;
    public static final long ANTI_AFK_JOIN_WAIT_MS = 1_300L;
    public static final long ANTI_AFK_AUCTION_RESTORE_WAIT_MS = 350L;


    // Shulker restack from the uploaded shalk.js idea: when inventory is full,
    // close auction, open a hotbar shulker, move up to 9 inventory slots into it,
    // close it, restore /ah and resume FullAuto safely.
    public static final int SHULKER_MOVE_SLOTS_LIMIT = 9;
    public static final long SHULKER_CLOSE_AUCTION_WAIT_MS = 50L;
    public static final long SHULKER_OPEN_WAIT_MS = 450L;
    public static final long SHULKER_OPEN_RECHECK_MS = 100L;
    public static final long SHULKER_OPEN_TIMEOUT_MS = 1_500L;
    public static final long SHULKER_AFTER_MOVE_WAIT_MS = 200L;
    public static final long SHULKER_BETWEEN_MOVE_MS = 90L;
    public static final long SHULKER_CLOSE_WAIT_MS = 150L;
    public static final long SHULKER_RESTORE_AUCTION_WAIT_MS = 700L;
    public static final long SHULKER_RETRY_COOLDOWN_MS = 3_000L;
    public static final long SHULKER_AH_RENT_BLOCK_MS = 5_000L;
    public static final long SHULKER_AUTO_CHECK_MS = 250L;
    public static final long SHULKER_RESCAN_KNOWN_FULL_MS = 60_000L;
    public static final long SHULKER_NO_MOVE_BLOCK_MS = 10_000L;

    public static final int DEFAULT_MAX_REFRESH_FAIL_STREAK = SMART_REOPEN_REQUIRED_SAME_CHECKS;
    public static final long SPAM_KICK_INITIAL_WAIT_MS = 800L;
    public static final long SPAM_KICK_JOIN_WAIT_MS = 2_000L;
    public static final long SPAM_KICK_AH_WAIT_MS = 500L;
    public static final long SPAM_KICK_AH_RETRY_MS = 900L;
    public static final int SPAM_KICK_AH_MAX_ATTEMPTS = 5;
    public static final long SPAM_KICK_TOTAL_TIMEOUT_MS = 18_000L;

}
