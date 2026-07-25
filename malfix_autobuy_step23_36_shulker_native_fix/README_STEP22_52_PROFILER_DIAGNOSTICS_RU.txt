Step 22.52 — improved profiler diagnostics / chat-log system

Что изменено:
- .mab prof status теперь выводит в игровой чат короткий summary, а полный блок пишет в latest.log.
- .mab prof full выводит полный profiler block в чат, если нужно вручную скопировать всё.
- .mab prof log пишет полный блок только в latest.log и не спамит чат.
- Overlay профайлера теперь выключен по умолчанию, чтобы сам overlay не портил FPS при тесте.
- Overlay-строки кэшируются и пересобираются не каждый кадр, а примерно раз в 250ms.
- Добавлены scanner detail counters:
  empty, blacklistFast, prefilterReject, prefilterPass, matchReject, matchPass,
  blacklistFull, priceMissing, priceRejected, accepted.
- По этим счетчикам теперь видно, где именно уходит время: prefilter слишком широкий,
  tooltip/NBT горячие, price parser дорогой, matcher дорогой, auction read дорогой,
  seller inventory preview/take item дорогой.
- Scanner стал меньше мусорить в GC: priority order теперь строится через int[], без ArrayList<Integer>;
  candidate buffers переиспользуются; ItemMatcher больше не создает ArrayList usablePhrases на каждый target/slot;
  PriceParser больше не создает regex Matcher для каждой price line.

Как тестировать для ChatGPT:
1) .mab prof on
2) .mab prof reset
3) 30-60 секунд реального /ah scan/fullauto или отдельно seller/storage.
4) .mab prof status
5) Пришли блок из игрового чата + при сильном лаге кусок latest.log между [MAB DEBUG BEGIN] и [MAB DEBUG END].

Главные строки:
- recent.scan avg/max и total.scan avg/max — общий scanner cost.
- recent.heavy tooltip/nbt/price/matcher — тяжелые участки.
- recent.reject и total.scan preReject/pass — качество prefilter.
- bottleneck=... — автоматическая первичная гипотеза, не абсолютная правда.

Важно:
Если recent пустой, но total заполнен — это нормально: status вызван после активности.
Для анализа важнее total.*, но recent показывает текущий спайк.
