Step 23.36 — shulker native controller fix

Исправлено:
1. JS shalk.js больше не убивает native ShulkerController каждый тик.
   В step23.33 из-за этого контроллер стартовал WAIT_OPEN, но сразу останавливался
   с reason=scripts_loaded_storage_owned_by_js. Поэтому скан открывал только первый
   шалкер/или не доходил до остальных, PUT не складывал, /ec fallback не запускался.

2. .mab shulker scan/test теперь закрывает экран чата перед правым кликом по шалкеру.
   Раньше после ввода команды currentScreen оставался ChatScreen, из-за чего right click
   не открывал контейнер стабильно.

3. Если native storage уже запущен, JS-тики не выполняются до завершения storage-операции.
   Это убирает конфликт старого shalk.js с native логикой.

4. Отключены проблемные Potato render mixins с неверными дескрипторами для Fabric Loader 0.17.2 / MC 1.21.4.
   Они давали InvalidInjectionException в latest.log при загрузке скриптов.

После установки:
.script reload
.mab shulker reset

Проверка:
.mab shulker scan
.mab shulker status
