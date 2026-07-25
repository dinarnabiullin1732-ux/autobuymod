Step 22.8 — фиксация внешних JS-скриптов под старый Spooky/Never стиль.

Что исправлено:
1) Скрипты теперь читаются из основной старой папки:
   .minecraft/scripts/

2) Дополнительно сохранена поддержка новой папки:
   .minecraft/malfix_autobuy/scripts/

3) Loader теперь ближе к старому автобаю:
   - общий Nashorn engine на все скрипты;
   - onLoad.js грузится первым;
   - поддерживаются on.accept(...), on(...), print.accept(...), print(...), chat.accept(...), chat(...);
   - добавлены minecraft/mc/player/window/keyboard/runScript/repeat bindings;
   - добавлены заглушки ru.nedan.neverapi.event.impl.EventMessage и EventPlayerTick.

4) Исправлена главная причина, почему shalk.js ломал full-auto:
   старый скрипт закрывает /ah, открывает шалкер и потом вызывает SpookyBuy.setState(true).
   В старом автобае после этого автобай сам возвращался к /ah.
   В нашем моде limitedLoop продолжал тикать без открытого аукциона и ловил auction_closed_before_refresh.
   Теперь при setState(true) мод держит паузу, отправляет /ah, ждёт окно аукциона и только потом снимает паузу.

Как ставить shalk.js:
1) Скопируй папку ADD_TO_GAME_FOLDER/scripts в папку игры.
2) Должно получиться:
   .minecraft/scripts/shalk.js
3) В игре проверь:
   .mab scripts reload
   .mab scripts status
4) В latest.log должны быть строки:
   [MAB SCRIPT] loaded scripts/shalk.js
   [MAB SCRIPT] handler registered: eventmessage
   [MAB SCRIPT] handler registered: eventplayertick

Если не работает, смотреть latest.log по строкам [MAB SCRIPT].
