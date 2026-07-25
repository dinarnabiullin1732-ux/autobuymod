Malfix AutoBuy Step 12.3 — scroll + keybind GUI cleanup

Изменения по просьбе:
1. Список предметов теперь листается колесиком мыши.
   - Кнопки Назад/Вперёд убраны.
   - Также работают стрелки Up/Down.
   - Справа есть тонкий индикатор прокрутки.

2. Верхняя кнопка HUD убрана.

3. Кнопки Импорт JS / Сброс JS убраны из GUI.
   - Предметы из твоего скрипта уже зашиты в ScriptItemCatalog.
   - После первичной настройки они больше не нужны.
   - Старые команды .mab scriptimport / .mab scriptreset пока оставлены как резерв.

4. Убраны символы в строках предметов:
   - плюс
   - шестерёнка
   Теперь в строке только предмет, цена и ВКЛ/ВЫКЛ.

5. Добавлена отдельная кнопка "Бинды".
   - Открывает новое меню KeybindConfigScreen.
   - Там можно настроить клавиши для:
     Debug
     Fingerprint
     Scan
     Observer
     Refresh
     Buy
     One cycle
     Loop
     GUI

6. Бинды сохраняются в config/malfix-autobuy.json:
   keybinds: {
     debug,
     fingerprint,
     scan,
     observer,
     refresh,
     buy,
     oneCycle,
     limitedLoop,
     gui
   }

Важно:
- Модификатор остаётся RightShift.
- Для реального клика покупки используется RightShift + Ctrl + клавиша Buy.
- Остальные действия: RightShift + назначенная клавиша.

Открыть:
.mab gui
или текущий GUI-бинд.

Открыть бинды командой:
.mab binds
.mab keybinds
.mab keys
