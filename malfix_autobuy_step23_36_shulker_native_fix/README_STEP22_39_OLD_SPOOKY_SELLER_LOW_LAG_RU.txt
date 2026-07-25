Step 22.39 — old Spooky-style seller low-lag patch

Изменения от Step 22.38:
- продажа сделана ближе к старому SpookyBuy: один проход по инвентарю -> выбрать/переложить -> сразу /ah sell;
- убран второй previewNextSell() после выбора hotbar и после SWAP из main inventory;
- во время продажи отключены per-item stdout строки seller-loop/sellreal;
- fallback matcher продажи больше не строит тяжёлый tooltip snapshot, использует displayName + itemId + NBT;
- full tooltip/NBT для продажи не читается на каждом предмете, что уменьшает лаг при 2-3 лаунчерах.

Сохранено:
- SellOnly: 30 секунд;
- продажа: 400ms;
- storage drain: 250ms за один слот;
- расстакивание: 350ms;
- NO_MONEY guard;
- closeHandledScreen fix для расстака;
- old Spooky refresh click;
- PotatoMode/low-lag автоматизация.
