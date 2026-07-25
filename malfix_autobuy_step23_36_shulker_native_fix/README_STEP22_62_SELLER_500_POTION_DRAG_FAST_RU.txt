Step 22.62 — seller/autosell 500ms + faster potion drag-unstack

Изменения относительно Step 22.61:

1) Seller / AutoSell delay
- AUTOSELL_SELL_MS изменён с 1200ms на 500ms.
- Это влияет на seller-loop, sellonly, cyclefull/storagecycle sell phase и минимальный clamp sellDelayMs в командах.
- Refresh аукциона из Step 22.61 не тронут: кнопка обновления остаётся с cadence 300ms и надёжным fingerprint flow.

2) Potion drag-unstack ускорен
- POTION_DRAG_STEP_DELAY_MS: 55ms -> 45ms
- POTION_DRAG_SETTLE_DELAY_MS: 100ms -> 80ms
- POTION_DRAG_NEXT_STACK_DELAY_MS: 120ms -> 80ms

Важно:
- One-tick QUICK_CRAFT не возвращался, потому что сервер уже показал частичное принятие такого drag: 64 -> 3x16 + остаток на курсоре.
- Staged drag, cursor remainder recovery и поле GUI "Мин. стак" из Step 22.58/22.59 сохранены.

Если сервер снова начнёт оставлять остаток на курсоре или принимать только часть drag-slots, нужно откатить только POTION_DRAG_STEP_DELAY_MS с 45ms обратно на 55-70ms, а не переписывать всю систему.
