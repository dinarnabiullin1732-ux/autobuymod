Step 23.29 — fixes for chat suggestions, Telegram diagnostics and lowercase .cloud

1. Подсказки при вводе точки больше не рисуются отдельным overlay поверх истории чата.
   Теперь они добавляются в обычный ChatHud как строки чата и Tab подставляет первую подсказку.

2. .Cloud переименован в .cloud в подсказках и help-тексте. Команды:
   .cloud save
   .cloud load latest
   .cloud list
   .cloud dir

3. Telegram:
   - telegram.chatId теперь строка, поддерживает отрицательные id вида -100... и @channel.
   - telegram.autoEnableWhenConfigured=true: если token/chatId заполнены, мод сам включает Telegram даже если telegram.enabled=false.
   - .tg test теперь пишет, почему сообщение не отправлено, а не просто "queued".
   - HTTP переключён на HTTP/1.1 и в статус добавляется короткий ответ Telegram API.

Настройка Telegram в malfix_autobuy/runtime.properties:
telegram.enabled=false
telegram.autoEnableWhenConfigured=true
telegram.token=123456:ABCDEF
telegram.chatId=123456789
telegram.polling=false

После изменения файла: .mab runtime reload, затем .tg status и .tg test.
