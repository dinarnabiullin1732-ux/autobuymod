Malfix AutoBuy Step 9 — One-Cycle AutoBuy

Это первый автоматический цикл, но НЕ бесконечный автобай.

Что добавлено:
- cycle/OneCycleAutoBuyController.java
- cycle/OneCycleResult.java

Новый бинд:
RightShift + A

Новые команды:
.mab cycle
.mab once
.mab one
.mab cancel

Что делает один цикл:
1. Проверяет, что открыт /ah.
2. Нажимает refresh.
3. Ждёт изменения fingerprint.
4. Сканирует 45 слотов.
5. Если найден подходящий лот — запускает controlled buy click.
6. Ждёт результат покупки из чата.
7. Останавливается.

Статусы:
- REFRESHING
- NO_MATCH
- BUY_CLICKED
- BUY_SUCCESS
- NO_MONEY_STOP
- INVENTORY_FULL_STOP
- ALREADY_SOLD_REFRESH_NEEDED
- PRICE_CHANGED_REFRESH_NEEDED
- BUY_TIMEOUT
- BUY_AUCTION_CHANGED
- BUY_SCREEN_CHANGED

Как тестить:
1. 00_FAST_BUILD_NO_REDOWLOAD.cmd
2. JAR из build\libs кинуть в mods.
3. В игре: .mab on
4. Открыть /ah.
5. Нажать RightShift + A

Или командой:
.mab cycle

Важно:
- Full-auto всё ещё выключен.
- Цикл выполняется один раз и останавливается.
- Если NO_MONEY_STOP или INVENTORY_FULL_STOP — будущий full-auto обязан ставиться на паузу.
- Если ALREADY_SOLD_REFRESH_NEEDED или PRICE_CHANGED_REFRESH_NEEDED — будущий full-auto должен делать refresh и продолжать.
