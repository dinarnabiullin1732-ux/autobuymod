Step 22.49 — profiler/counters для поиска реальных лагов

Что добавлено:
- лёгкий MalfixProfiler без аллокаций в hot-path;
- счётчики scanner/readAuctionSlots/matcher/priceParser/tooltip/NBT;
- счётчики seller preview, seller hotbar/pick/command;
- счётчики storage open/take и refresh clicks;
- расширенный overlay рядом с FPS в окне аукциона;
- команды: .mab prof on/off/status/reset/overlay on|off;
- .mab debug теперь включает profilerBelow.

Как тестировать:
1) Запусти /ah и 30-60 секунд scan/fullauto.
2) Выполни .mab prof status и пришли блок profiler.
3) Потом отдельно включи seller/storage и снова .mab prof status.

Что смотреть:
- tooltipCalls и nbtCalls: если их много в секунду — scanner всё ещё трогает тяжёлые данные;
- scanner avg/max ms: если max скачет выше 5-10ms — будет FPS micro-stutter;
- inventoryPreview avg/max ms: если растёт при sell — seller matcher/snapshot тормозит;
- click counts: если storage/seller кликают чаще ожидаемого — есть лишние state transitions.

Этот патч почти не меняет поведение покупки/продажи. Он нужен, чтобы следующий оптимизационный патч резал конкретные горячие места, а не угадывал delay на глаз.
