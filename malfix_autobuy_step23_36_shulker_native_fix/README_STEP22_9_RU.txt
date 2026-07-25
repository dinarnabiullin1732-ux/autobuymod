Step 22.9

Фикс запуска лаунчера со скриптами.

Причина: старая папка .minecraft/scripts грузила onLoad.js от старого автобая. В логе было видно: loaded scripts/onLoad.js, после чего загрузка зависала/ломалась.

Теперь автозагрузка читает только безопасную папку:
.minecraft/malfix_autobuy/scripts/

Старая папка .minecraft/scripts больше НЕ грузится автоматически. Ее можно загрузить вручную командой .mab scripts legacyreload, но это не рекомендуется, если там есть старый onLoad.js.

Скрипты загружаются асинхронно, чтобы даже плохой скрипт не блокировал запуск Minecraft.

Команды:
.mab scripts dir
.mab scripts status
.mab scripts reload
.mab scripts legacyreload

Для шалкеров скопируй ADD_TO_GAME_FOLDER/malfix_autobuy/scripts/shalk.js в папку игры, сохранив путь.
