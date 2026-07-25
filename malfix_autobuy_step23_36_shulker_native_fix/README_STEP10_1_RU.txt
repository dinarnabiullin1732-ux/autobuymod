Malfix AutoBuy Step 10.1 — Cleanup / Stability Patch

Это не новый функциональный этап, а стабилизация Step 10.

Что исправлено:
1. Двойная обработка серверных сообщений:
   - добавлен ChatResultDeduplicator;
   - одинаковый buy-result в коротком окне больше не применяется два раза.

2. Потоки обработки чата:
   - входящие серверные сообщения теперь переводятся на main client thread через MinecraftClient.execute(...);
   - меньше риска, что chat HUD/loop state обновятся из Netty-потока.

3. Pending debug:
   - server buy result теперь пишет:
     manualPending=
     oneCyclePending=
     limitedLoopPending=
     source=
   - больше не будет путаницы, когда limited-loop ждёт результат, а manual pending=false.

4. Observer spam:
   - observer продолжает обновлять fingerprint/scan state;
   - но во время active automation/loop он не спамит чат строками observer best.

5. Debug:
   - добавлены:
     lastBuyResultSource
     lastServerBuyMessageDuplicate
     dedupDuplicateCount
     manualBuyPending
     oneCycleBuyPending
     limitedLoopBuyPending

Как тестить:
1. 00_FAST_BUILD_NO_REDOWLOAD.cmd
2. JAR из build\libs кинуть в mods.
3. В игре: .mab on
4. Открыть /ah
5. Запустить:
   .mab loop 10 3
   или RightShift + L

Ожидаемо:
- server buy result должен появиться один раз;
- source должен быть limited-loop;
- limitedLoopPending должен быть true перед применением результата;
- при NO_MONEY loop должен остановиться;
- observer не должен спамить во время активного limited-loop.
