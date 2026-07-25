Step 19.7 — FullAuto smart reopen при зависании аукциона после покупки

Что исправлено:
- Если после refresh окно аукциона не меняет слоты несколько раз подряд, это больше не считается нормальной страницей бесконечно.
- После покупки включается более строгий режим: если 2 refresh подряд дают тот же fingerprint, автобай закрывает аукцион и заново отправляет /ah.
- В обычном режиме, без недавней покупки, переоткрытие срабатывает после 8 одинаковых refresh подряд.
- После переоткрытия buy-loop не останавливается: он продолжает обновлять/сканировать/покупать.
- Добавлены debug-поля:
  limitedLoopNoChangeStreak
  limitedLoopSmartReopenReason
  limitedLoopSmartReopenAgoMs

Тайминги:
- post-buy stuck window: 12000ms
- post-buy no-change streak для reopen: 2
- general no-change streak для reopen: 8
- reopen cooldown: 2500ms
- close wait перед /ah: 250ms
- wait открытия /ah: 700ms

Проверка:
1) Запусти .mab fullauto или нажми R.
2) После покупки, если аукцион залип и слоты не меняются, должен появиться event:
   smart_reopen_close_auction: post_buy_no_change_streak=2
   затем smart_reopen_send_ah
3) Потом он должен продолжить buy-loop без ухода в хранилище/продажу раньше лимитов.

Если будет слишком часто переоткрывать при обычной одинаковой странице — увеличивать FULL_AUTO_GENERAL_NO_CHANGE_REOPEN_STREAK.
Если после покупки всё ещё долго ждёт — уменьшать FULL_AUTO_POST_BUY_NO_CHANGE_REOPEN_STREAK нельзя ниже 2, лучше уменьшить FULL_AUTO_REOPEN_COOLDOWN_MS.
