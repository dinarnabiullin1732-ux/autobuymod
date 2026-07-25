Step 23.34 — Telegram sale parser, shulker/EC storage, .nbt

1) Telegram sale messages
- Added parser for server format:
  "У Вас купили [★] Серебро x31 за $424,855 на /ah"
- Telegram still reports only real bought/sold deals, not listing confirmations.

2) Shulker storage
- Native Never-style storage now owns full-inventory flow even when shalk.js is loaded.
- Added SCAN mode: .mab shulker scan opens hotbar shulkers and marks full/empty/has-space.
- Generic 27/54-slot container opened right after a hotbar shulker right-click is accepted as script-owned shulker.
- If all hotbar shulkers are full, storage falls back to /ec automatically.
- Manual /ec remains protected: it is closed only when the storage controller itself sent /ec.

3) NBT
- Added top-level command: .nbt
- Existing .mab nbt still works.
- .nbt offhand dumps offhand item.
- Output is printed to latest.log and saved to .minecraft/malfix_autobuy/nbt_dumps/.
- Updated ADD_TO_GAME_FOLDER/malfix_autobuy/scripts/nbt_hand.js: F8 now triggers .nbt instead of using old 1.16 Registry API.
