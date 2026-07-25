Malfix AutoBuy Step 22.55 — remove mistaken "Из руки" GUI helper

Что изменено:
- Полностью убрана кнопка "Из руки" из GUI предметов.
- Убран click-handler этой кнопки из TargetsConfigScreen.
- Убран метод MalfixClientRuntime.addHeldItemTargetFromGui().
- Убран вспомогательный findTargetIndexByLabel(), который был нужен только этой функции.
- Убран неиспользуемый import java.util.Arrays.

Что НЕ трогалось:
- Refresh/buy retry/storage drain фиксы Step 22.54 сохранены.
- Редактирование существующих targets в GUI сохранено: buy price, sell price, parser, "Расстакивать", количество расстака.
- Команда hand NBT dump не удалялась: это diagnostic tool, не функция добавления предмета из руки.

Причина:
Кнопка "Из руки" была неправильной трактовкой задачи. Для зелья SPEED III + STRENGTH III нужен конкретный target/config/catalog entry, а не универсальное добавление любого предмета из main hand.
