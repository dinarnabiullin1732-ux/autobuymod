Step 22.86 — Never-style movement lock

Добавлена всегда включенная блокировка движения во время активного автобая/продажи/цикла.

Что блокируется каждый client tick, пока активна автоматизация:
- W / S / A / D
- Space / Jump
- Shift / Sneak
- Sprint
- текущий ClientPlayerEntity.input movementForward/movementSideways

Зачем:
- чтобы случайно не отойти от NPC/аукциона во время автобая;
- чтобы движения игрока не мешали server GUI кликам;
- поведение ближе к Never: функция без отдельного тумблера, активна автоматически.

Не трогает:
- чат;
- GUI Malfix;
- бинды запуска/остановки;
- клики мыши по аукциону.

Точка подключения:
src/main/java/ru/malfix/autobuy/client/MovementLock.java
src/main/java/ru/malfix/autobuy/client/MalfixClientRuntime.java
