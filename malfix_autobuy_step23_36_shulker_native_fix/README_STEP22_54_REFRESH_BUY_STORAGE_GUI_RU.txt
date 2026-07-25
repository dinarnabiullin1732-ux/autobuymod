Step 22.54 — refresh micro-freeze / buy retry / slower storage drain

Base: Step 22.53/22.52 profiler diagnostics.

1) Auction refresh compared with spookybuy-2.5.4-ForkNeverMods.jar
- Old SpookyBuy sends refresh as a raw ClickSlotC2SPacket to slot 49 with button=0 and SlotActionType.PICKUP.
- Malfix already used the same low-level refresh packet path in MinecraftAuctionView.clickRefreshLikeOldSpooky().
- The remaining visible micro-freeze was more likely caused by RefreshCycleController doing page read/fingerprint work around refresh.
- Refresh flow kept as old-style: click refresh -> tiny settle -> read/scan once, not heavy pre-read/poll on the click tick.

2) Buy click reliability
- ControlledBuyClickExecutor now remembers the clicked auction slot identity.
- If Ctrl+LMB buy packet was sent but the same lot is still visible in the same slot, it retries the buy click before the loop can refresh away.
- Defaults:
  MalfixTimings.BUY_RETRY_SAME_SLOT_MS = 170L
  MalfixTimings.BUY_RETRY_MAX = 2
  MalfixTimings.CONTROLLED_BUY_RESULT_TIMEOUT_MS = 900L
- This is intended to prevent the bot from skipping an item that stayed on the page after a missed/ignored buy packet.

3) Storage drain slower and more stable
- Storage one-by-one drain delay increased:
  STORAGE_OPEN_WAIT_MS = 220L
  STORAGE_TAKE_WAIT_MS = 180L
  STORAGE_ONE_TAKE_MS = 260L
- Empty-storage confirmation is slower and stricter:
  STORAGE_EMPTY_RECHECK_MS = 420L
  STORAGE_EMPTY_RECHECKS = 3
- Goal: avoid false "storage empty" detection when the server has not shifted the next stored item into the first slot yet.

4) Item GUI
- The mistaken add-from-hand helper was removed in Step 22.55.
- Item GUI still supports normal editing of configured targets: buy price, sell price, parser, "Расстакивать", and stack amount.

Important:
- No GUI button now creates targets from the player's hand.
- Concrete special items must be added through config/catalog/script logic, not by a universal hand-import helper.
