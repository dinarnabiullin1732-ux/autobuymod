Step 22.66 — Нагрудник крушителя: обязательные чары + optional Шипы III

Что исправлено после Step 22.65:
- В Step 22.65 Нагрудник крушителя был временно упрощён до проверки только `chestplate-kryshitel`.
- Это небезопасно: пользователь уточнил, что основные чары ОБЯЗАТЕЛЬНЫ.
- При этом зачарование `minecraft:thorns` / "Шипы III" может быть, а может отсутствовать; оба варианта валидны.

Новая логика target:
- itemId: `minecraft:netherite_chestplate`
- tagContains:
  `chestplate-kryshitel&&ench:minecraft:protection=5&&ench:minecraft:blast_protection=5&&ench:minecraft:fire_protection=5&&ench:minecraft:projectile_protection=5&&ench:minecraft:mending=1&&ench:minecraft:unbreaking=5`

Что НЕ требуется:
- `ench:minecraft:thorns=3` не добавлен в обязательные условия.
- Нет `!thorns` запрета.

Итог:
- Нагрудник крушителя без Шипов III проходит, если есть все обязательные чары.
- Нагрудник крушителя с Шипами III тоже проходит, потому что extra enchants игнорируются.
- Нагрудник с `chestplate-kryshitel`, но без одного из обязательных чар, должен отбрасываться.
- Damage/durability не проверяется, потому что прочность у брони меняется.

Остальное не тронуто:
- refresh 300ms;
- seller/autosell 500ms;
- Anti-AFK 5 минут без мгновенного перезахода;
- staged potion drag-unstack;
- skull/apple NBT=null itemId-only фиксы из Step 22.65.
