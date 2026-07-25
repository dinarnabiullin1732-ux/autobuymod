Step 22.56 — встроенное Несоздаваемое зелье SPEED III + STRENGTH III

Что изменено:
1) Убрана неправильная идея с добавлением предмета "Из руки" из Step 22.54/22.55 — она не нужна для этой задачи.
2) Встроен конкретный target из Malfix_only_potions_effectdata_autoreg.js:
   - label: Несоздаваемое зелье
   - itemId: minecraft:potion
   - effects: SPEED III 180s + STRENGTH III 180s
   - buy price: 0
   - sell price: 0
   - enabled: false
   - unstack: true
   - unstackAmount: 1

Как матчится:
- Не по точной строке NBT.
- Не по порядку эффектов в NBT.
- Используется новый tag-rule формат:
  effect:speed:3600:2&&effect:strength:3600:2

Это соответствует старому SpookyBuy PotionItem + EffectData:
- EffectData.of(StatusEffects.SPEED, 180, 2)
- EffectData.of(StatusEffects.STRENGTH, 180, 2)

Почему так:
Старый SpookyBuy проверял эффекты как список StatusEffectInstance и не зависел от порядка эффектов.
Malfix теперь делает такую же order-independent проверку через tooltip/SNBT search.

GUI:
Предмет появится в списке targets. В GUI можно менять:
- enabled
- buy price
- sell price
- unstack
- unstackAmount

Важно:
Существующий config автоматически получит этот target при запуске через catalog patch/merge.
Если target уже есть, патч обновит itemId/tagContains, но не будет насильно перетирать цену и enabled.
