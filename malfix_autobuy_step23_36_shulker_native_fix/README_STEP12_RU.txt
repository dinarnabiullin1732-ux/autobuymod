Malfix AutoBuy Step 12 — GUI + items from your old script

Что добавлено:
- gui/TargetsConfigScreen.java
- config/ScriptItemCatalog.java
- GUI открытия через RightShift + G
- команда .mab gui
- импорт/сброс целей из твоего old JS script:
  .mab scriptimport
  .mab scriptreset

Предметы взяты из твоего скрипта:
- Порох
- Обсидиан
- Плачущий обсидиан
- Тотем бессмертия
- Звезда незера
- Незеритовый слиток
- Незеритовый лом
- Изумрудная руда
- яйца мобов
- Крабсбургер
- Пасхальное яйцо
- Формула крабсбургера
- Проклятая душа
- Опыт 15уровень
- Дезориентация
- Снежок заморозка
- Явная пыль
- Прогрузчик чанков 1x1
- Дамагер
- сигнальные огни
- Аир-дроп
- Пласт
- Пузырёк опыта

Важно:
- Все script targets по умолчанию disabled и maxUnitPrice=0.
- Это специально, чтобы мод не купил дорогой предмет до ручной настройки цены.
- В GUI выбери предмет -> впиши цену -> Set.
- Если цена > 0, предмет автоматически включается.
- Также можно включать/выключать кнопкой ON/OFF.
- Save сохраняет в .minecraft/config/malfix-autobuy.json.
- Import script добавляет недостающие предметы из скрипта.
- Reset script полностью заменяет список на предметы из скрипта.

Новая NBT-часть:
- AuctionSlot теперь хранит nbtString из ItemStack.
- TargetConfig хранит:
  itemId
  tagContains
- ItemMatcher умеет матчить не только name/id, но и NBT/tagContains.
- Это нужно для кастомных предметов из твоего скрипта.

Как тестить:
1. 00_FAST_BUILD_NO_REDOWLOAD.cmd
2. JAR из build/libs кинуть в mods.
3. В игре:
   .mab on
   .mab gui
   или RightShift + G
4. Нажми Reset script или Import script.
5. Настрой цену и включи нужные предметы.
6. Потом тестируй:
   .mab loop
