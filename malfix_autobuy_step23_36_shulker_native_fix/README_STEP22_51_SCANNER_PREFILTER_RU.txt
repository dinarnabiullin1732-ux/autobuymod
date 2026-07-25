Step 22.51 — scanner fast prefilter

Что сделано:
- Добавлен быстрый prefilter перед строгим ItemMatcher.
- Tooltip/NBT больше не читаются для слотов, которые явно не подходят ни под один включённый target.
- Для talisman/custom предметов prefilter только допускает слот к строгой проверке, но не принимает покупку.
- Талисманы всё ещё требуют AttributeModifiers/NBT proof и не покупаются по имени.
- Blacklist теперь сначала проверяется по displayName/itemId, а полный tooltip/NBT blacklist — только после найденного match-кандидата.
- PriceParser больше не использует replaceAll для чисел, меньше мусора для GC.

Ожидаемый эффект по profiler:
- total.tooltipCalls должен заметно упасть.
- total.nbtCalls должен заметно упасть.
- total.scanCalls avgMs должен уйти ниже старых ~64ms.

После теста:
.mab prof reset
30-60 секунд scan/fullauto
.mab prof status
