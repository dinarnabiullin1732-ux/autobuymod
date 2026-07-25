Step 22.12 — добавлены NBT-модификаторы + удалено Пасхальное яйцо

Изменения:
1. Добавлены предметы в список целей автобая:
   - Модификатор полёта
     itemId: minecraft:feather
     tagContains: spooky-item":"modifier-item-fly-day
   - Модификатор починки
     itemId: minecraft:bubble_coral
     tagContains: spooky-item":"modifier-item-fix-day

2. Удалён предмет:
   - Пасхальное яйцо

3. Добавлена миграция конфига:
   - если в старом config/malfix-autobuy.json уже был предмет "Пасхальное яйцо", мод удалит его при запуске;
   - если в старом конфиге нет новых модификаторов, мод добавит их автоматически;
   - цены у новых предметов по умолчанию 0, поэтому они НЕ покупаются, пока пользователь сам не поставит цену в GUI.

4. Остальное не трогалось:
   - refresh 250ms;
   - FullAuto;
   - shalk.js scripts folder;
   - spam-kick auto rejoin;
   - Anti-AFK;
   - tooltip цена за штуку;
   - зелёный квадратик в /ah search.
