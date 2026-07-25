Malfix AutoBuy Step 12.1 — GUI close compile fix

Исправлено:
- В TargetsConfigScreen.java вызов closeScreen() заменён на closeToParent().
- closeScreen() отсутствует в используемых Yarn/Minecraft 1.16.5 mappings, из-за этого compileJava падал.
- Закрытие GUI теперь делает:
  runtime.applyConfigToRuntime()
  runtime.saveConfig()
  client.openScreen(parent)

Собирать:
00_FAST_BUILD_NO_REDOWLOAD.cmd
