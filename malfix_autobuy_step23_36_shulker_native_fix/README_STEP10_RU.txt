Malfix AutoBuy Step 10 — Limited Safe-Loop

Это ограниченный safe-loop. Это всё ещё НЕ финальный полноценный full-auto.

Что добавлено:
- loop/LimitedAutoLoopController.java
- loop/AutoLoopResult.java

Новый бинд:
RightShift + L — включить/выключить limited-loop

Новые команды:
.mab loop
.mab loop 10
.mab loop 10 3
.mab stop

Формат:
.mab loop [maxCycles] [maxBuys]

Примеры:
.mab loop       -> 10 циклов, максимум 3 покупки
.mab loop 5     -> 5 циклов, максимум 3 покупки
.mab loop 20 5  -> 20 циклов, максимум 5 покупок

Что делает limited-loop:
1. refresh
2. wait fingerprint changed
3. scan
4. если найден лот — controlled buy click
5. ждёт BuyResult из чата
6. делает задержку ~300ms
7. повторяет, пока не достигнут лимит

Остановка:
- NO_MONEY -> stop
- INVENTORY_FULL -> stop
- maxCycles -> stop
- maxBuys -> stop
- .mab stop -> manual stop
- RightShift + L при активном loop -> stop

Продолжает после:
- NO_MATCH
- ALREADY_SOLD
- PRICE_CHANGED
- BUY_TIMEOUT
- BUY_AUCTION_CHANGED
- BUY_FAILED

Как тестить:
1. 00_FAST_BUILD_NO_REDOWLOAD.cmd
2. JAR из build\libs кинуть в mods.
3. В игре: .mab on
4. Открыть /ah.
5. Нажать RightShift + L

Или командой:
.mab loop 10 3

Важно:
- Unlimited full-auto всё ещё отключен.
- Этот loop ограничен и безопаснее для тестов.
- При NO_MONEY он должен сразу остановиться.
- Следующий шаг после проверки — настройки лимитов/задержек и защита от бессмысленного refresh/no-change.
