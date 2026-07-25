Step 22.84 — Never recognition layer + faster sell cycle

Что изменено:
1. Каталог распознавания теперь сначала использует Never-style proof rules:
   - item id + реальные enchants для брони/оружия/кирки;
   - tooltip-сигнатуры Never для меча/кирки Крушителя;
   - PublicBukkitValues-style tags для отмычки к сферам и модификатора полёта;
   - texture hash для сфер, без доверия к видимому названию.

2. Добавлены/исправлены сферы из Never:
   - Афины
   - Хаоса
   - Сатира
   - Бестии
   - Ареса
   - Гидры
   - Икара
   - Титана
   - Эрида

3. Исправлен hash Сферы Титана:
   - раньше у Титана стоял hash Эриды;
   - теперь Титан = 81e9698458b7841c96ae4f24ec84ae01724100641c564e2a7b185f406e8ed23.

4. Добавлены Never-предметы, которые есть в 1.16.5:
   - Маяк
   - Шалкер
   - Чарка как alias для зачарованного золотого яблока.

5. Matcher получил generic attr-rule parser:
   - attr:generic.max_health=4.0
   - attr:minecraft:generic.attack_damage:7.0
   - attribute:generic.movement_speed=0.15

6. Цикл продажи ускорен ближе к Never action gate:
   - AUTOSELL_SELL_MS: 500ms -> 200ms
   - SELLER_RESULT_WAIT_TIMEOUT_MS: 2500ms -> 900ms
   - AUTOSELL_OPEN_MS: 3000ms -> 1000ms

Важно:
- Never jar, который был загружен, содержит быстрый buy/slot action gate, но отдельного полноценного /ah sell loop в нём не видно. Поэтому для Malfix применён Never-like action timing, а не буквальное копирование отсутствующего seller loop.
- Mace/Булава из Never не добавлена в текущую 1.16.5-версию, потому что vanilla minecraft:mace появился в новых версиях и не существует в 1.16.5. Её нужно добавлять уже на этапе порта 1.21.x.
