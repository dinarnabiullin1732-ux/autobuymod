Step23.26

Добавлено:
1) AutoRejoin в стиле Never/SpookyBuy:
   - если клиент попал на Disconnect screen, Malfix ждёт delay и пробует переподключиться к последнему серверу;
   - после подключения, если включено restoreAh, запускается /hub -> /an<анархия> -> /ah;
   - команды: .mab autorejoin status/test/stop.

2) Внешний файл настроек:
   malfix_autobuy/runtime.properties
   Там можно менять:
   - anarchy=305 / 505 / пусто для autodetect;
   - autorejoin.enabled;
   - autorejoin.delayMs;
   - autorejoin.restoreAh;
   - базовые тайминги rejoin/spam-rejoin.
   Команды: .mab runtime status/reload/dir.

3) Локальный cloud configs:
   .cloud save [name]
   .cloud load [name]
   .cloud list
   .cloud dir
   Файлы лежат в malfix_autobuy/cloud/<name>.json. Это локальные копии конфигов с ценами/targets, чтобы переносить цены между аккаунтами без нового парсинга.

4) Все отделки брони убраны из встроенного каталога и добавлены в deprecated, чтобы старые цели удалились из конфига при старте.

После установки:
.mab runtime dir
.cloud save main
.cloud load main
