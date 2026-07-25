Malfix AutoBuy Step 18.2 — cyclefullloop

Добавлено:
.mab cyclefullloop [loops] [buyCycles] [buyMax] [sellMax] [sellDelayMs] [loopDelayMs]

Также работают:
.mab fullcycleloop
.mab buyandsellloop

Примеры:
.mab cyclefullloop
.mab cyclefullloop 5
.mab cyclefullloop 5 10 3 10 900 1500

Параметры:
loops       — сколько полных циклов выполнить, default 5
buyCycles   — сколько refresh/buy циклов внутри покупки, default из GUI/конфига
buyMax      — максимум покупок за один cyclefull, default из GUI/конфига
sellMax     — максимум продаж за seller-часть, default 10
sellDelayMs — задержка между продажами, default 900
loopDelayMs — задержка между полными cyclefull, default 1500

Что делает:
1. Запускает cyclefull.
2. Ждёт завершения:
   покупка -> закрытие /ah -> sellcycle -> возврат /ah.
3. Ждёт loopDelayMs.
4. Запускает следующий cyclefull.
5. Повторяет до loops.

Останавливается:
- при достижении loops;
- при NO_MONEY;
- при ошибке открытия /ah;
- при buy-loop ошибке до покупок;
- при ручной остановке .mab stop / .mab cancel;
- если активировалось другое buy/seller действие.

Важно:
- Это всё ещё НЕ бесконечный режим.
- После достижения лимита loops он останавливается.
- Обычный .mab cyclefull сохранён без изменений.

Debug:
.mab debug показывает:
cycleFullLoop=enabled/waitingCycle/cycles/buyCycles/buyMax/sellMax/delay/stopReason
