Malfix AutoBuy Step 7 — Controlled Buy Click

Это первый шаг с настоящим кликом покупки, но всё ещё НЕ full-auto.

Что добавлено:
- ControlledBuyClickExecutor
- ControlledBuyClickResult
- команда .mab clickbuy / .mab realbuy / .mab click
- бинд RightShift + Ctrl + B

Оставлено:
- RightShift + B — dry-run, НЕ покупает
- RightShift + R — refresh cycle
- RightShift + D — debug

Как работает настоящий клик:
1. Проверяет, что открыт аукцион.
2. Блокирует покупку, если refresh ещё pending.
3. Запускает dry-run.
4. Если dry-run не READY_TO_BUY — не кликает.
5. Проверяет fingerprint и стабильность слота.
6. Делает ровно один клик по containerSlot.
7. Ждёт результат:
   - SCREEN_CHANGED_AFTER_CLICK — после клика открылся другой экран/подтверждение.
   - AUCTION_CHANGED_AFTER_CLICK — аукцион изменился после клика.
   - TIMEOUT_STILL_SAME — после клика ничего не изменилось.
   - CLICK_FAILED — клик не был отправлен.

Как тестить:
1. 00_FAST_BUILD_NO_REDOWLOAD.cmd
2. JAR из build\libs кинуть в mods.
3. В игре: .mab on
4. Открыть /ah.
5. Нажать RightShift + R.
6. Нажать RightShift + B и проверить READY_TO_BUY=true.
7. Только потом нажать RightShift + Ctrl + B.

Важно:
- Настоящая покупка пока только ручная.
- Автоцикла покупки нет.
- Авто refresh loop нет.
- Селлера нет.
- GUI нет.
- Если сервер после клика открывает подтверждение покупки, следующий этап будет ConfirmClickExecutor.
