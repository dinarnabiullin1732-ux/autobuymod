Step 23.35 — SpongeBob currency NBT patch

Исправлены встроенные цели автобая по реальным NBT-дампам:

1) Крабсбургер
   itemId: minecraft:pumpkin_pie
   tagContains: spookystash:currency":"burger

2) Формула крабсбургера
   itemId: minecraft:guster_banner_pattern
   tagContains: spookystash:currency":"formula

Важно:
- Старые конфиги автоматически патчатся при запуске/mergeInto.
- Цены пользователя не сбрасываются.
- Это не меняет NBT серверного предмета, а меняет правила распознавания в Malfix AutoBuy.
