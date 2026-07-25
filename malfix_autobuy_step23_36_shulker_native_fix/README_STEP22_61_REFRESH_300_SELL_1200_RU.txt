Step 22.61 — refresh 300ms + seller /sell 1200ms

Изменения:
1. AB_UPDATE_MS изменён с 400ms на 300ms. Это целевая минимальная задержка между нажатиями кнопки обновления аукциона.
2. LimitedAutoLoopController теперь планирует следующий refresh от времени предыдущего refresh-click, а не от времени завершения fingerprint polling/scan.
   Это даёт фактический cadence около 300ms между кликами, но сохраняет надёжный Step 22.60 flow:
   read fingerprint before click -> click refresh -> poll until changed/timeout -> scan.
3. FULL_AUTO_REFRESH_TIMEOUT_MS изменён с 150ms на 300ms, чтобы fullauto/cyclefull не закрывали no-change refresh слишком рано.
4. AUTOSELL_SELL_MS изменён с 300ms на 1200ms. Seller теперь по умолчанию и как minimum clamp отправляет /ah sell примерно раз в 1200ms.

Не тронуто:
- potion staged drag-unstack;
- potion min source count GUI;
- удалённый same-slot buy retry;
- storage drain timings.
5. Старый loopDelayMs=400 из Step 22.60 при загрузке конфига мигрирует в 300ms. Если в конфиге стояло значение выше 400, оно сохраняется.
6. loopDelayMs теперь clamp'ится снизу до AB_UPDATE_MS=300, чтобы случайно не поставить 100/150ms и не попасть в серверный лимит refresh.
