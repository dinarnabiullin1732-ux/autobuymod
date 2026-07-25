Malfix AutoBuy Step 14 — Safe Auto-Run

Это первый нормальный safe-auto режим поверх уже проверенного limited-loop.

Новые команды:
.mab auto
.mab start
.mab full
.mab autorun
.mab auto 10
.mab stop

Как работает:
1. .mab auto запускает safe-auto.
2. Safe-auto запускает limited-loop партиями.
3. Когда партия дошла до maxCycles/maxBuys, safe-auto запускает следующую.
4. Так он продолжает работать до:
   - .mab stop
   - NO_MONEY
   - INVENTORY_FULL
   - auction_closed
   - maxRefreshFailStreak
   - maxTotalBuys, если указан в .mab auto <число>

Примеры:
.mab auto
- работает без лимита покупок до hard-stop/manual stop.

.mab auto 5
- остановится после 5 успешных покупок.

Безопасность:
- Step 13 safety остаётся активным.
- enabled target с maxUnitPrice=0 НЕ покупается.
- blacklist работает.
- scanMode работает.
- refreshTimeoutMs работает.
- maxRefreshFailStreak работает.
- NO_MONEY и INVENTORY_FULL стопают safe-auto.

Рекомендованный тест:
1. .mab config
2. .mab set scan TOP27
3. .mab set requirePrice true
4. .mab set allowUnlimited false
5. Настроить цены в GUI.
6. Открыть /ah.
7. Запустить:
   .mab auto 1

После проверки:
.mab auto

Важно:
Это уже auto-run, но seller/reseller/shulker/restack ещё не добавлены.
