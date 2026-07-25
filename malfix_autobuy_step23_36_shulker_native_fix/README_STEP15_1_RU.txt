Malfix AutoBuy Step 15.1 — Ctrl + LMB buy click

Исправлено:
- Покупка больше не делается обычным ЛКМ.
- ControlledBuyClickExecutor теперь вызывает ctrlLeftClickAuctionSlot(...).
- Обычный clickAuctionSlot(...) оставлен, но для автобая не используется.
- Refresh и другие обычные клики не изменены.

Технически:
- Для покупки используется SlotActionType.QUICK_MOVE с button=0.
- Это ближе к buy-shortcut клику, который нужен на твоём аукционе вместо обычного PICKUP.

Проверка:
1. Собери через 00_FAST_BUILD_NO_REDOWLOAD.cmd.
2. Кинь обычный jar из build/libs в mods.
3. Открой /ah.
4. Запусти safe-auto через RightShift+L.
5. Проверь, что покупка теперь срабатывает как Ctrl+ЛКМ.
