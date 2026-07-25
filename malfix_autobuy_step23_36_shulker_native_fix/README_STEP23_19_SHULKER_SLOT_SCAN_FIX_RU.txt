Step23.19

Исправление автосклада в шалкер.

По статусу пользователя было видно: emptyInv=0, hotbarShulkers=3, но lastMoved=0 и lastReason=blocked_after_no_move_left. Это значит, что автозапуск срабатывал и шалкеры видел, но перенос не находил слоты игрока в открытом контейнере.

Что изменено:
- перенос больше не зависит только от предположения totalSlots - 36;
- контроллер сначала ищет реальные Slot, принадлежащие PlayerInventory, через reflection;
- если это не удалось, остаются fallback-диапазоны как в shalk.js;
- в статус добавлена более полезная причина вида script_no_movable_items_scanned=..., handlerSlots=..., containerSlots=... .

После установки желательно выполнить .mab shulker reset или перезапустить клиент, чтобы сбросить noMove cooldown.
