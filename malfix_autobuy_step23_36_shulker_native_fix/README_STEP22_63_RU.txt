Step 22.63 — русский чат парсера + безопаснее smart reopen

Изменения:
1) Видимые сообщения парсера в игровом/мод-чате переведены на русский:
   - "Parser" -> "Парсер";
   - start/stop/blocked/done/updated/skip сообщения теперь не пишут английский Parser.
   Команды остаются прежними: .mab parser, .mab parseall, .mab parse, .mab parserstop.

2) Защита от случайного переоткрытия аукциона после покупки стала мягче:
   - FULL_AUTO_POST_BUY_NO_CHANGE_REOPEN_STREAK: 2 -> 5.
   Теперь после покупки окно /ah переоткроется только если 5 подряд refresh-click не изменили fingerprint в post-buy stuck window.

Не тронуто:
- refresh cadence 300ms из Step 22.61;
- seller/autosell 500ms из Step 22.62;
- ускоренный staged potion drag-unstack;
- поле "Мин. стак" для зелий.
