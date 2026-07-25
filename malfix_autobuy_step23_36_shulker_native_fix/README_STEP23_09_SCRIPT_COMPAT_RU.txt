Step23.09 — фикс зависания старых Never/SpookyBuy JS-скриптов на 1.21.4

Что исправлено:
- добавлен ScriptCompatBridge для старых шулькер-скриптов;
- загрузчик JS теперь автоматически переписывает опасные старые функции:
  * getItemId
  * isShulkerBox
  * selectHotbarSlot
  * rightClickMainHand
  * quickMoveSlot
  * isContainerOpen
  * getContainerSlotsCount
  * closeCurrentScreen
- старый Registry class_2378 больше не ломает определение shulker_box;
- old interactItem(player, world, hand) заменяется на 1.21.4-safe вызов через bridge;
- добавлен watchdog: если legacy script поставил autobuy на паузу и не вернул управление, Malfix снимет застрявшую паузу примерно через 12 секунд на обычном экране.

Важно: сам shalk.js менять не обязательно — мод применяет совместимость при загрузке скрипта.
