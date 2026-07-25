# Step 22.69 — Parser debug arm для /ah search GUI

Основа: Step 22.68. Step 22.67 DonatMarket-гипотеза не используется как рабочий фикс.

Проблема Step 22.68: команда `.mab parsedebug "Label"` требовала писать команду, пока открыта страница `/ah search`, но с открытым GUI поиска это неудобно/невозможно.

Добавлено:

1. Режим arm перед открытием поиска:

   `.mab parsedebug arm "Нагрудник крушителя"`
   `.mab parsedebug arm "Зачарованное золотое яблоко"`
   `.mab parsedebug arm "Череп визер-скелета"`

   После этого нужно открыть нужную страницу `/ah search`. Как только клиент увидит открытый GenericContainerScreen с непустыми слотами, dump сам запишется в `latest.log`.

2. Команды управления:

   `.mab parsedebug status` — показать, какой target сейчас armed.
   `.mab parsedebug cancel` — отменить arm.

3. Safety timeout:

   Arm действует 45 секунд. Если за это время подходящее окно не открыто, arm сам сбрасывается.

4. Key fallback:

   Если arm включён и открыт GUI контейнера, можно нажать RightShift + debug-key. Вместо общего debug будет записан parser debug dump для armed target.

Что пишется в latest.log:

   `========== [MAB PARSER DEBUG BEGIN] ==========`
   ... screenTitle, isAuctionOpen, isSearchResult, target config, query, parserCheapest, strictCheapest, slots 0..53, price parse, strict match/debug reason, tooltip, NBT ...
   `========== [MAB PARSER DEBUG END] ==========`

Не тронуто:
- refresh 300ms;
- seller/autosell 500ms;
- Anti-AFK 5 минут;
- potion staged drag-unstack;
- skull/apple itemId-only;
- Нагрудник крушителя: netherite_chestplate + chestplate-kryshitel + обязательные основные enchants, thorns optional.
