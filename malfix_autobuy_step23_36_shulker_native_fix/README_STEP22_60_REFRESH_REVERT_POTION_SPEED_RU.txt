Step 22.60 — refresh rollback + faster potion drag-unstack

1) Refresh откатан обратно к поведению до Step 22.53/22.54.

Вернули старый надёжный Malfix flow:
  read fingerprint before click
  -> click refresh
  -> poll until fingerprint changed or timeout
  -> scan resulting page

Причина: one-shot refresh из Step 22.53/22.54 мог сканировать ещё старую страницу после маленького settle-window, из-за чего fullauto/limited loop иногда переставал покупать предметы после обновления.

2) Potion drag-unstack ускорен, но не возвращён к опасному one-tick режиму.

Было:
  step delay 70ms
  settle 140ms
  after clean drag: normal unstack delay, обычно 400ms

Стало:
  step delay 55ms
  settle 100ms
  after clean potion drag: 120ms до следующего potion stack

Это ускоряет расстак зелий примерно на 30-45%, но сохраняет staged QUICK_CRAFT, который исправил зависание с остатком на курсоре.

3) Остальное не тронуто:
  - min source count для зелий из Step 22.59 сохранён;
  - GUI поле "Мин. стак" сохранено;
  - обычный right-click unstack для остальных предметов не изменён;
  - same-slot buy retry по-прежнему удалён.

Если сервер снова начнёт оставлять остаток на курсоре, поднять POTION_DRAG_STEP_DELAY_MS обратно до 65-70ms.
