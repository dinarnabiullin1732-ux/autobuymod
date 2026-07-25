Step 22.64 — Anti-AFK timer-on-start + Step 22.63 fixes included

Изменения этого шага:

1) Anti-AFK больше не делает мгновенный перезаход при включении автобая/fullauto.
   Было плохо: FullAuto специально ставил antiAfkNextAtMs = now, поэтому при старте сразу шёл /hub -> /an -> /ah.
   Теперь при старте FullAuto/autobuy вызывается timer-arm:
      now + antiAfkIntervalMs
   То есть перезаход будет только после истечения таймера.

2) Anti-AFK interval по умолчанию теперь 5 минут:
      ANTI_AFK_INTERVAL_MS = 300_000L
   Старый сохранённый дефолт 290_000L автоматически мигрирует на 300_000L при загрузке конфига.
   Кастомные значения не трогаются, кроме старого 290_000L.

3) Если automation выключен, Anti-AFK не копит просроченный таймер.
   Когда autobuy/fullauto/sellonly не активны, timer держится как now + interval.
   Это убирает баг: игрок постоял долго без автобая -> включил автобай -> anti-afk сразу перезашёл.

4) Команда .mab antiafk on теперь тоже не запускает мгновенный rejoin.
   Она включает систему и запускает таймер с нуля.
   Для немедленного теста остались:
      .mab antiafk test
      .mab antiafk now

Также в этом архиве сохранены изменения Step 22.63:
- сообщения парсера в чате на русском: "Парсер", а не "Parser";
- post-buy защита от случайного переоткрытия аукциона стала мягче:
      FULL_AUTO_POST_BUY_NO_CHANGE_REOPEN_STREAK = 5
  вместо 2.

Не тронуто:
- refresh аукциона 300ms из Step 22.61;
- seller/autosell 500ms из Step 22.62;
- ускоренный staged potion drag-unstack;
- поле "Мин. стак" для зелий;
- удалённый same-slot buy retry.
