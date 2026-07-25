STEP 22.6 — ПАПКА СКРИПТОВ

Что изменено:
1. Native-шалкер авто-режим отключён для автоматического срабатывания.
2. Добавлен загрузчик .js скриптов как в старом автобае.
3. Скрипты кладутся в папку игры:
   .minecraft/malfix_autobuy/scripts/

Команды:
.mab scripts dir       — показать путь к папке скриптов
.mab scripts status    — статус загрузчика
.mab scripts reload    — перезагрузить .js скрипты
.mab scripts pauseoff  — снять паузу, если скрипт завис и не вернул автобай

Как поставить shalk.js:
1. Запусти игру с модом один раз.
2. Напиши .mab scripts dir
3. Открой показанную папку.
4. Закинь туда shalk.js.
5. Напиши .mab scripts reload или перезапусти игру.

В этом архиве также есть готовая папка:
ADD_TO_GAME_FOLDER/malfix_autobuy/scripts/shalk.js
Её содержимое можно скопировать в папку игры.

Важно:
- Для работы скриптов в jar добавлен Nashorn runtime.
- Старый скрипт использует on.accept(...), print.accept(...), chat.accept(...), Java.type(...).
- Добавлен compatibility-класс ru.nedan.spookybuy.SpookyBuy, чтобы старый shalk.js мог временно ставить автобай на паузу и возвращать его обратно.
- FullAuto не должен больше падать с auction_closed_before_refresh, когда скрипт закрывает /ah для открытия шалкера.
