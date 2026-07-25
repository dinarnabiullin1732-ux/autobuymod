Step23.28 — runtime auto-upgrade, Never-style auth/autorejoin/suggestions

Изменения:
- runtime.properties теперь автоматически дополняется недостающими ключами при старте и при .mab runtime reload.
- Добавлены telegram.* ключи в существующий runtime.properties, если их не было.
- Добавлены auth.* настройки для автологина после autorejoin / при запросе пароля сервером.
- Autorejoin теперь использует настраиваемые команды hub/anarchy/auction и ждёт паузу после входа, чтобы успеть обработать /login.
- Подсказки команд после точки стали ближе к Never: список фильтруется по набранному тексту, первая строка подсвечивается, Tab подставляет первую подсказку.

Новые ключи runtime.properties:
auth.enabled=true
auth.password=
auth.loginCommand=/login {password}
auth.registerCommand=/register {password} {password}
auth.delayMs=800
auth.cooldownMs=10000

autorejoin.hubCommand=/hub
autorejoin.anarchyCommand=/an{anarchy}
autorejoin.auctionCommand=/ah
autorejoin.postLoginWaitMs=1000

Пример:
anarchy=305
auth.password=123456
autorejoin.enabled=true
autorejoin.restoreAh=true

После изменения файла в игре:
.mab runtime reload
