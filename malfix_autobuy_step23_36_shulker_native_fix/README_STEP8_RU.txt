Malfix AutoBuy Step 8 — Buy Result Detector

Что добавлено:
- BuyResultType
- BuyResult
- BuyResultDetector
- ClientPlayNetworkHandlerMixin для входящих сообщений чата
- связка ControlledBuyClickExecutor + результат покупки из чата

Теперь мод различает:
- BUY_SUCCESS
- NO_MONEY
- ALREADY_SOLD
- PRICE_CHANGED
- INVENTORY_FULL
- BUY_FAILED
- UNKNOWN_FAIL

Проверенный пример:
[✕] Ошибка! Вам не хватает монет
должен определяться как:
NO_MONEY

Команды:
.mab result — показать последний результат покупки
.mab debug — теперь показывает lastBuyResultType / reason / message

Как тестить:
1. 00_FAST_BUILD_NO_REDOWLOAD.cmd
2. JAR из build\libs кинуть в mods.
3. В игре: .mab on
4. Открыть /ah.
5. RightShift + R
6. RightShift + B
7. Если READY_TO_BUY=true:
   RightShift + Ctrl + B
8. Смотри чат:
   server buy result: NO_MONEY / BUY_SUCCESS / ALREADY_SOLD / ...

Важно:
- Full-auto всё ещё нет.
- Настоящий клик всё ещё только вручную.
- Если результат NO_MONEY или INVENTORY_FULL, будущий автоцикл должен ставиться на паузу.
- Если ALREADY_SOLD или PRICE_CHANGED, будущий автоцикл должен делать refresh и продолжать.
