Step 19.4 — stable SpookyBuy timings import

Тайминги перенесены из загруженного stable SpookyBuy jar:
- ab.update = 300ms
- ab.buy = 1ms
- ab.resellItem = 300ms
- ab.resell = 90000ms
- ab.auctionRefresh = 604800000ms (обычный периодический reopen выключен, оставлен smart-reopen подход)
- autosell.open = 3000ms
- autosell.unstack = 250ms
- autosell.sell = 300ms

Smart reopen timings из jar:
- tick = 250ms
- refresh no-change check = 1200ms
- min screen age = 1500ms
- frozen slots = 4500ms
- required same checks = 4
- reopen cooldown = 9000ms

Что изменено в Malfix AutoBuy:
- добавлен общий класс ru.malfix.autobuy.config.MalfixTimings;
- delay покупки/обновления в цикле = 300ms;
- refresh timeout = 1200ms;
- max refresh fail streak = 4;
- buy click poll/min wait = 1ms;
- buy result timeout = 1500ms;
- sellloop/sellcycle default delay = 300ms;
- storage/relist open/take waits = 300ms;
- возврат seller в /ah = 3000ms;
- full-auto loopDelay по умолчанию = 3000ms;
- FullAuto bind на одну кнопку R сохранён;
- фикс хранилища с пропуском последней строки сохранён.

Проверка:
.mab fullauto
.mab storagecycle 36 10 300
.mab sellcycle 10 300
.mab debug
