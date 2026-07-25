Malfix AutoBuy Step 4.1 - Observer + Key Debug

Что изменилось:
- .mab on больше НЕ запускает автоматический цикл открытия/обновления/покупки.
- .mab on теперь включает только observer-mode.
- Покупки всё ещё нет.
- Авто-refresh тоже отключён.
- Добавлены бинды для проверки аукциона без открытия чата:
  RightShift + D = debug
  RightShift + F = fingerprint текущего GUI
  RightShift + S = scan текущего GUI
  RightShift + O = включить/выключить observer

Как тестить:
1) Собери через 00_SET_JAVA21_AND_BUILD.cmd
2) Кинь jar из build\libs в mods.
3) Зайди на сервер.
4) Напиши .mab on
5) Открой /ah
6) Не открывая чат, нажми:
   RightShift + F
   RightShift + S
   RightShift + D

Что нужно проверить:
- auctionOpen=true, когда открыт аукцион.
- screen содержит GenericContainerScreen и нормальный title.
- slots=45.
- fingerprint меняется после ручного обновления аукциона.
- scan не крашит игру.

Если auctionOpen=false при открытом аукционе:
Скинь строку screen=... из RightShift+D. Значит надо расширить распознавание названия/типа GUI.
