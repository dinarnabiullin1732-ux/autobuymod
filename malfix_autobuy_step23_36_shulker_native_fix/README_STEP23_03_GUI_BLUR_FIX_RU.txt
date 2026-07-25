Malfix AutoBuy step23_03 — GUI blur fix для 1.21.4

Что изменено:
- В TargetsConfigScreen, ParserConfigScreen и KeybindConfigScreen убран вызов Screen.renderBackground(...).
- Вместо vanilla background/blur используется плоское полупрозрачное затемнение через DrawContext.fill(...).
- Это должно убрать мыло с меню после порта на 1.21.4, оставив читаемый тёмный фон за окном.

Если фон теперь слишком тёмный или слишком прозрачный, меняется только alpha в цвете 0x66000000.
Например:
- 0x44000000 — светлее
- 0x88000000 — темнее
