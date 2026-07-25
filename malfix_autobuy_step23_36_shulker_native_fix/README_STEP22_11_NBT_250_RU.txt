Step 22.11 — NBT dump script + auction refresh 250ms

Что изменено:
1. Обновление аукциона поставлено на 250ms:
   MalfixTimings.AB_UPDATE_MS = 250L

2. Добавлена нативная команда:
   .mab nbt
   .mab handnbt
   .mab nbt offhand

Команда пишет NBT предмета из руки в latest.log и файл:
.minecraft/malfix_autobuy/nbt_dumps/hand_nbt_<time>.txt

В файле будет:
- itemId
- name
- count
- полный nbt
- пример tagContains, который можно использовать при добавлении предмета в GUI/autobuy

3. Добавлен внешний скрипт:
ADD_TO_GAME_FOLDER/malfix_autobuy/scripts/nbt_hand.js

Как использовать скрипт:
- Скопировать ADD_TO_GAME_FOLDER/malfix_autobuy/scripts/nbt_hand.js в папку игры.
- В игре выполнить:
  .mab scripts reload
- Взять предмет в руку.
- Нажать F8.
- Смотреть latest.log или файл:
  .minecraft/malfix_autobuy/nbt_dumps/hand_nbt_script_<time>.txt

Важно:
- Для обычной проверки лучше использовать .mab nbt — это надёжнее скрипта.
- Скрипт нужен как отдельный инструмент, если хочешь держать NBT-dumper в папке scripts.
