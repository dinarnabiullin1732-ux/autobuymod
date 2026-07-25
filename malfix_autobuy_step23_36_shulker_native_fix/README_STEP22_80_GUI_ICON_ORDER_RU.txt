Step 22.80 — GUI icons/order follow-up after Step 22.79

Что исправлено:
- Сфера Афины теперь не остаётся в конце списка после merge старого config: встроенные targets переупорядочиваются по каталогу, пользовательские custom targets остаются после них в своём старом порядке.
- GUI icon fallback теперь не доверяет placeholder itemId=paper/minecraft:paper, если label/tag явно указывает на сферу/серебро/зелье/кастомный предмет. Это закрывает старые configs, где custom предметы уже были сохранены как paper.
- Добавлены alias/texture matching для названий со скринов: «Сфера Сатир» и «Сфера Бестий» помимо старых «Сфера Сатира»/«Сфера Бестии».
- Step 22.79 synthetic SkullOwner texture для сфер сохранён: Сфера Хаоса, Сатир/Сатира, Бестий/Бестии, Ареса, Гидры, Титана, Афины.

Не тронуто: storage Step 22.77, Anti-AFK Step 22.76, shulker guard Step 22.75, buy-timeout/totem-talisman Step 22.73, parser Step 22.71, refresh 300ms, seller 500ms, potion drag.
