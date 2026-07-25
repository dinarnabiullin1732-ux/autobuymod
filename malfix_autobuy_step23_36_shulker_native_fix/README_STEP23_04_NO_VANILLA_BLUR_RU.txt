Step23_04 — жёсткое отключение vanilla blur для GUI Malfix

Что исправлено:
- В 1.21.4 blur может включаться через Screen#blur()/applyBlur(), даже если экран сам не вызывает renderBackground().
- Для TargetsConfigScreen, ParserConfigScreen и KeybindConfigScreen добавлены no-op override методы:
  - blur()
  - applyBlur()
  - renderInGameBackground(...)
  - renderDarkening(...)
- renderBackground(...) теперь рисует только плоское затемнение Malfix, без vanilla blur.

Если после этого фон всё ещё будет размыт, значит blur применяет сторонний мод/клиентская настройка Accessibility > Menu Background Blur, а не сам Malfix Screen.
