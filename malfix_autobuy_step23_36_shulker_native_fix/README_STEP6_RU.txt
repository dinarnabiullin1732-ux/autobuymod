Malfix AutoBuy Step 6 — Buy Dry-Run

Это всё ещё безопасный этап. Настоящей покупки нет.

Что добавлено:
- BuyDryRunExecutor
- BuyDryRunResult
- команда .mab buy / .mab b / .mab drybuy
- бинд RightShift + B

Что делает dry-run:
1. Проверяет, что открыт аукцион.
2. Считывает 45 слотов.
3. Сканирует лучший подходящий лот.
4. Сразу повторно проверяет fingerprint.
5. Проверяет, что выбранный слот не изменился.
6. Пишет READY_TO_BUY=true/false.
7. НЕ кликает по предмету.

Как тестить:
1. 00_FAST_BUILD_NO_REDOWLOAD.cmd
2. JAR из build\libs кинуть в mods.
3. В игре: .mab on
4. Открыть /ah.
5. Не открывая чат:
   - RightShift + R — refresh cycle
   - RightShift + B — dry-run покупки
   - RightShift + D — debug

Нормальный результат:
buy dry-run: status=READY_TO_BUY, ...
READY_TO_BUY=true, slot=..., item=...
target=..., unit=..., total=..., REAL_CLICK=false

Если будет SLOT_CHANGED:
страница изменилась между scan и verify. Для настоящей покупки такой слот трогать нельзя.

Следующий этап после успешной проверки:
Step 7 — Controlled Buy Click:
- отдельный настоящий клик по выбранному слоту;
- защита от изменения fingerprint перед кликом;
- ожидание результата покупки;
- блокировка refresh/watchdog во время покупки.
