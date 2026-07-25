Step 22.79 — GUI icons follow-up

- Исправлены GUI-иконки сфер: для player_head сфер теперь создаётся synthetic SkullOwner/texture NBT, чтобы в списке целей отображались не бумага/обычная голова, а настоящие sphere-head текстуры.
- Покрыты: Сфера Хаоса, Сфера Сатира/Статира, Сфера Бестии, Сфера Ареса, Сфера Гидры, Сфера Титана, Сфера Афины.
- Серебро получает GUI-only fallback icon minecraft:white_dye, но scanner/seller matching серебра остаётся tag-based и не меняет безопасную логику Step 22.75.
- Не тронуты: storage Step 22.77, Anti-AFK Step 22.76, shulker sell guard Step 22.75, totem/talisman guard Step 22.73, parser Step 22.71, refresh 300ms, seller/autosell 500ms, potion drag.
