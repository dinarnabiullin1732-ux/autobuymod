Step 23.33 — Telegram actual sold + Never shalk.js storage fix

Что изменено:
1. Telegram: расширено распознавание реальных сообщений о продаже.
   Сообщения о выставлении на продажу по-прежнему игнорируются.
2. shalk.js: в архив добавлен загруженный пользователем скрипт как рабочий reference/copy для malfix_autobuy/scripts/shalk.js.
3. ScriptCompatBridge: экран шалкера/EC теперь считается экраном скрипта, если сам скрипт только что открыл хранилище.
   Это чинит SCAN/PUT/TAKE для серверных GenericContainerScreen без слова shulker в title.
4. EC_PUT: hard-coded >=3 shulkers заменён на проверку всех найденных hotbar-шалкеров; если все известны как full, скрипт пробует /ec.
5. Native ShulkerController отключается, когда загружены JS-скрипты, чтобы не закрывать /ec и не конфликтовать с shalk.js.

После установки:
.script reload

Проверка:
.mab scripts status
.tg status

Если продажа всё равно не приходит в Telegram, нужно скинуть точный текст серверного сообщения о реальной продаже из чата/latest.log.
