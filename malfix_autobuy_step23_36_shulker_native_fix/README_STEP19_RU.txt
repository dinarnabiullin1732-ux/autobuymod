Malfix AutoBuy Step 19 — one-key full-auto + storage relist

Добавлено:
1. Один бинд FullAuto:
   - по умолчанию RightShift + P;
   - настраивается в меню биндов;
   - повторное нажатие останавливает full-auto.

2. Команда:
   .mab fullauto
   .mab fa
   .mab onekey

Она запускает:
   /ah -> покупка -> хранилище -> перевыставление/продажа -> /ah -> повтор

Параметры one-key по умолчанию:
   loops=50
   buyCycles=defaultLoopCycles
   buyMax=defaultLoopBuys
   sellMax=10
   sellDelayMs=900
   loopDelayMs=1500
   auctionRefresh/loopDelayMs=300

3. Система перевыставления из хранилища:
   - после buy-loop перед seller-частью мод кликает слот хранилища /ah slot 47;
   - ждёт открытия хранилища;
   - QUICK_MOVE переносит до 36 предметов из верхней части хранилища в инвентарь;
   - закрывает GUI;
   - запускает sellcycle;
   - после продажи возвращается в /ah.

4. Ручная команда для хранилища:
   .mab storagecycle [takeMax] [sellMax] [sellDelayMs]
   .mab relist
   .mab resellstorage

Пример:
   .mab storagecycle 36 10 900

Важно:
- storage slot по умолчанию = 47.
- если хранилище не открылось, цикл не ломается: storage relist пропускается и seller продолжается.
- если /ah rent / sell_limit, seller останавливается и cyclefullloop продолжает buy-cycle по правилам Step 18.3.
- полный режим всё ещё лимитированный loops=50, не бесконечный.


STEP 19.1 FIX:
- Исправлена ошибка сборки: добавлен setKeyFullAuto(int).
- FullAuto по умолчанию теперь R.
- FullAuto запускается одной клавишей без RightShift.
- Повторное нажатие этой же клавиши останавливает FullAuto.
- FullAuto не срабатывает, если открыт чат или другое GUI.
