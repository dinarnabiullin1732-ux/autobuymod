Step 22.78 — Сфера Афины + GUI icons для custom/script targets

Основа: Step 22.77.

Что изменено:
1) Добавлен новый target "Сфера Афины":
   - itemId: minecraft:player_head
   - tagContains: attribute-item-safina||сфера афины
   - contains: сфера афины, attribute-item-safina
   - по умолчанию disabled, buy/sell price = 0.

2) Исправлены itemId у custom/script targets, которые раньше имели itemId="" и поэтому в GUI отображались как paper:
   - Божья Аура -> minecraft:phantom_membrane
   - Божье касание -> minecraft:golden_pickaxe
   - Молот Тора -> minecraft:netherite_pickaxe
   - Трапка -> minecraft:netherite_scrap
   - Отмычка к сферам -> minecraft:tripwire_hook
   - Спавнер -> minecraft:spawner

   При загрузке старого config patchExistingTargets обновляет только itemId/tagContains, не трогая enabled/цены/unstack.

3) GUI icon fallback:
   - если у старого target всё ещё пустой itemId, TargetsConfigScreen пытается вывести понятную иконку по label/tagContains;
   - custom splash potions получают synthetic CustomPotionColor в GUI icon stack, поэтому больше не выглядят одинаково/пусто;
   - сферы fallback'ятся на player_head, талисманы — на totem_of_undying.

Не тронуто:
- Step 22.77 storage rework после /ah rent;
- Step 22.76 Anti-AFK chat trigger;
- Step 22.75 shulker/container sell guard;
- Step 22.73 buy-timeout reopen и totem/talisman seller guard;
- Step 22.71 parser paged search title fix;
- refresh 300ms, seller/autosell 500ms, potion drag-unstack.
