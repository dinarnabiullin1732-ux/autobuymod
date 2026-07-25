Step23.27 — Never-style logic pack

Изменения:
1. .script reload
   - Новый верхнеуровневый алиас для перезагрузки скриптов без .mab.
   - Поддержка старых NeverAPI event classes оставлена: ru.nedan.neverapi.event.impl.EventMessage / EventPlayerTick.
   - Добавлены NeverBuy compatibility stubs: ru.nedan.neverbuy.NeverBuy и telegram facade.

2. Подсказки команд
   - При вводе строки, начинающейся с '.', ChatScreen показывает доступные команды:
     .Cloud save, .cloud load latest, .script reload, .tg status/test и основные .mab команды.

3. Telegram
   - Добавлен Never-style Telegram sender/polling module.
   - Настройка в .minecraft/malfix_autobuy/runtime.properties:
     telegram.enabled=true
     telegram.token=BOT_TOKEN
     telegram.chatId=123456789
     telegram.polling=false
   - Команды: .tg status, .tg reload, .tg test
   - Telegram polling поддерживает /status и /ping.

4. .Cloud
   - .Cloud save сохраняет полный локальный snapshot: config.json + runtime.properties.
   - .cloud load latest загружает последний snapshot.
   - Сохранения лежат в .minecraft/malfix_autobuy/cloud/latest и timestamp-папках.
   - Это переносит цены, targets, настройки парсера, runtime/anarchy/timings/telegram/autorejoin.

5. Распознавание талисманов
   - Улучшен Never-style fallback: если auction preview не отдаёт attribute component, но точная identity талисмана есть в lore/custom data, предмет допускается.
   - Attribute signatures остаются приоритетными.

6. Ender chest / EC
   - Native ShulkerController больше не считает эндер-сундук своим экраном и не закрывает /ec.
   - /ec остаётся для legacy/Never script storage logic.

7. Отделки
   - Встроенный каталог отделок остаётся удалённым; старые отделки остаются только в deprecated list для очистки старого конфига.

После установки:
.script reload
.mab runtime reload
.cloud save
.cloud load latest
.tg status
