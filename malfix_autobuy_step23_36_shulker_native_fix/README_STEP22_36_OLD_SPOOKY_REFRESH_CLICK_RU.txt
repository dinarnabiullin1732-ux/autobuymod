Step 22.36 — old Spooky-style refresh click

База: Step 22.35.

Исправление:
- Клик по кнопке обновления аукциона больше не идёт через interactionManager.clickSlot.
- Refresh теперь отправляется как в старом SpookyBuy: прямой ClickSlotC2SPacket на slot 49, button 0, SlotActionType.PICKUP, ItemStack.EMPTY, actionId из handler.getNextActionId(...).
- RefreshCycle теперь считает старт неуспешным, если пакет клика не был отправлен: refresh_click_not_sent.

Зачем:
- Старый автобай не симулировал локальный клик контейнера для обновления, а отправлял тихий пакет.
- На серверном аукционе это стабильнее: меньше десинка курсора/контейнера и меньше "кривых" refresh-кликов при 2-3 лаунчерах.

Не тронуто:
- SellOnly 60s.
- Задержка продажи 650ms.
- Бинд SellOnly.
- NO_MONEY guard.
- Рабочий unstack handler-close fix.
- PotatoMode.
- Old Spooky storage drain.
