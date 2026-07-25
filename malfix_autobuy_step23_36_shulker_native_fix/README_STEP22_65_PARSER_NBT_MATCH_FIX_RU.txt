Step 22.65 — parser/highlighter NBT-match fix for skull/apple/crusher chestplate

Что исправлено:
1) Череп/голова визер-скелета и зачарованное золотое яблоко считаются vanilla no-NBT предметами.
   Для них catalog patch принудительно ставит itemId и очищает tagContains.
   Добавлены aliases "голова визер-скелета" / "голова визер скелета".

2) Нагрудник крушителя теперь матчится по стабильному серверному NBT identity:
   spooky-item / chestplate-kryshitel.
   Полный список обязательных зачарований больше не нужен для определения именно этого предмета,
   поэтому вариант с Thorns III и вариант без Thorns III оба валидны.

3) ItemMatcher теперь нормализует item ids без namespace:
   wither_skeleton_skull -> minecraft:wither_skeleton_skull,
   enchanted_golden_apple -> minecraft:enchanted_golden_apple,
   netherite_chestplate -> minecraft:netherite_chestplate.

4) Enchant rule теперь принимает уровень >= требуемого, а не только точное текстовое совпадение.
   Лишние/опциональные чары, включая thorns 3, не блокируют match.

5) Highlighter и parser теперь считают окно после /ah search валидной search-страницей,
   даже если title у сервера обычный "Аукцион", но storage/vault по-прежнему отсекаются.

Не тронуто:
- refresh 300ms из Step 22.61;
- seller/autosell 500ms из Step 22.62;
- Anti-AFK timer из Step 22.64;
- potion staged drag-unstack и поле "Мин. стак".
