Malfix AutoBuy Step 18 — cyclefull: buy -> sellcycle -> /ah

Добавлено:
.mab cyclefull [buyCycles] [buyMax] [sellMax] [sellDelayMs]

Также работают:
.mab fullcycle
.mab buyandsell

Примеры:
.mab cyclefull
.mab cyclefull 10 3 10 900

Что делает:
1. Открывает /ah.
2. Ждёт, пока окно аукциона реально откроется.
3. Запускает limited buy-loop:
   cycles = buyCycles
   buys = buyMax
4. Когда buy-loop остановился:
   - max buys/cycles;
   - inventory full;
   - были покупки;
   запускает sellcycle.
5. Sellcycle продаёт предметы безопасной цепочкой:
   - рука;
   - хотбар;
   - основной инвентарь через пустой хотбар.
6. После seller-цикла возвращается в /ah.

Важно:
- Это НЕ бесконечный автобай.
- После завершения cyclefull останавливается.
- NO_MONEY останавливает цикл без seller.
- Если buy-loop упал до покупок по ошибке, цикл останавливается и возвращает /ah.
- Цена покупки 0 по умолчанию = предмет не покупается.
- Цена продажи 0 = предмет не продаётся.

Debug:
.mab debug показывает:
cycleFull=enabled/phase/buyCycles/buyMax/sellMax/sellDelay/openAttempts/stopReason
