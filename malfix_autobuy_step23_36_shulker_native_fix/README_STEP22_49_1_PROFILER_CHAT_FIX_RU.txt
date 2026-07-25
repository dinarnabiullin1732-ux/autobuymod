Step 22.49.1

Исправление profiler status:
- .mab prof status теперь печатает profiler block прямо в игровой чат, а не только в latest.log/stdout.
- .mab prof on/off/reset/overlay тоже дают короткий ответ в игровой чат.
- Полный profiler block всё равно дублируется в latest.log между [MAB DEBUG BEGIN]/[MAB DEBUG END].

Причина бага step22.49:
ClientChat.send() специально печатает служебные сообщения только в stdout/log, чтобы не спамить чат. Profiler status случайно использовал этот путь через sendDebugBlock().
