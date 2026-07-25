Malfix AutoBuy Step 4 - Runtime + Debug

What changed:
- The mod now has client tick integration through Mixin.
- The mod now has local chat commands.
- It can read auction slots in-game.
- It can compute fingerprint of the current auction page.
- It can manually scan current auction slots and show the best matching lot.

IMPORTANT:
This version still DOES NOT BUY anything.
BuyExecutor is intentionally not connected yet.

Commands in Minecraft chat:
.mab help     - show commands
.mab on       - enable scan/debug loop, no buying
.mab off      - disable
.mab debug    - print state to chat and full console/log
.mab scan     - scan current open auction once
.mab fp       - show auctionOpen, slot count and fingerprint
.mab open     - send /ah
.mab close    - close current screen

Recommended test:
1) Build with 00_SET_JAVA21_AND_BUILD.cmd
2) Put normal jar from build/libs into mods.
3) Start Minecraft 1.16.5 Fabric.
4) Join your test server/world.
5) Type: .mab help
6) Open auction: /ah or .mab open
7) Type: .mab fp
8) Type: .mab scan

Do not use .mab on for long tests yet. This step is for reading/scanning/debugging only.
