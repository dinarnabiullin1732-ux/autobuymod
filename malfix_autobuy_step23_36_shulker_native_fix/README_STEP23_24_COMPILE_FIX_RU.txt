Step23.24

Исправлена ошибка сборки из step23.23:
- добавлен helper hasNonChatScreenOpen() в ShulkerController.

Важно:
- helper не закрывает любые пользовательские окна; он возвращает только hasClosableScreenOpen(), то есть закрывать можно только /ah или storage-контейнеры, разрешённые guard-логикой.
