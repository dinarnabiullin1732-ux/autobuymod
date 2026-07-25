Step 22.20 — NBT tag fix for uploaded dumps

Исправлены теги распознавания:
- Нагрудник крушителя: itemId=minecraft:netherite_chestplate, tagContains=chestplate-kryshitel
- Ботинки крушителя: itemId=minecraft:netherite_boots, tagContains=boots-kryshitel
- Элитры Крушителя: itemId=minecraft:elytra, tagContains=elytra-kryshitel
- Зачарованное золотое яблоко: itemId=minecraft:enchanted_golden_apple, tagContains пустой

Важно: Damage намеренно не используется в tagContains, потому что прочность/урон предмета меняется, и из-за Damage автобай может не распознавать корректный предмет или пропускать лоты.

Сохранено из Step 22.19:
- refresh 250ms
- расстакивание
- двигаемые GUI-окна
- FullAuto
- Anti-AFK
- spam-kick rejoin
- scripts/shalk.js
- tooltip цена за штуку
- зелёный квадратик в /ah search
- парсер по тумблеру "Парсить"
