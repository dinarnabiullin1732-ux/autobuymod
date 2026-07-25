Step 22.37 — low-lag autobuy/seller timings

База: Step 22.36.

Изменения:
- SellOnly теперь запускается каждые 30 секунд.
- Задержка продажи /ah sell: 400ms.
- Расстакивание ускорено: split tick 250ms, prepare wait 600ms вместо 1500ms.
- Сбор из хранилища ускорен: один предмет/слот каждые 250ms вместо 400ms.
- Добавлен автоматический low-lag режим: во время FullAuto/SellOnly/seller/cycle включается PotatoMode, а после остановки возвращается прежнее состояние.
- Во время активной автоматизации отключаются тяжёлые визуальные помощники: cheapest highlight, unit price tooltip append и FPS overlay, чтобы они не строили tooltip/скан каждый кадр.
- Seller дополнительно уменьшает тяжёлое чтение tooltip/NBT: full snapshot используется только когда это реально нужно для tag/NBT-target.

Не трогалось:
- NO_MONEY guard из Step 22.27.
- handler-close фикс расстакивания из Step 22.27.
- SellOnly bind из Step 22.31.
- no-false custom match из Step 22.32.
- PotatoMode из Step 22.33.
- старый Spooky storage drain из Step 22.35.
- old Spooky refresh click из Step 22.36.
