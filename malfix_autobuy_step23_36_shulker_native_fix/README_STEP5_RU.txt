Malfix AutoBuy — Step 5: refresh/fingerprint cycle

Цель шага:
- НЕ покупать предметы;
- НЕ запускать полноценный автоцикл;
- безопасно проверить клик по кнопке refresh;
- после refresh дождаться изменения fingerprint;
- после изменения/таймаута просканировать текущую страницу.

Сборка:
1) Распакуй архив.
2) Запусти 00_SET_JAVA21_AND_BUILD.cmd
3) Готовый JAR бери из build\libs
4) В mods кидай JAR без sources/dev в названии.

Команды:
.mab help      - помощь
.mab on        - observer-mode, без покупки и без автоцикла
.mab off       - выключить observer и core-loop
.mab fp        - fingerprint текущего GUI
.mab scan      - одиночный скан текущей страницы
.mab refresh   - клик refresh + ожидание fingerprint change + scan
.mab debug     - debug observer/core/refresh
.mab open      - отправить /ah
.mab close     - закрыть текущий GUI

Бинды:
RightShift + D - debug
RightShift + F - fingerprint
RightShift + S - scan
RightShift + R - refresh cycle
RightShift + O - observer toggle

Как тестить:
1) Зайди на сервер.
2) Напиши .mab on
3) Открой /ah
4) Не открывая чат, нажми RightShift+R.

Нормальный результат:
refresh started: beforeFp=..., checked=45, timeoutMs=900
refresh done: status=success_changed, pending=false, elapsedMs=..., beforeFp=..., afterFp=..., changed=true, checked=45, msg=fingerprint_changed
refresh best: slot=..., item=..., target=..., unit=..., total=...

Если будет timeout_no_change:
- кнопка refresh могла не нажаться;
- слот refresh может быть не 49;
- сервер не обновил страницу;
- текущая страница визуально не изменилась.

Важно:
Покупки на этом шаге нет. Это специально. Сначала нужно доказать, что refresh и fingerprint работают стабильно.
