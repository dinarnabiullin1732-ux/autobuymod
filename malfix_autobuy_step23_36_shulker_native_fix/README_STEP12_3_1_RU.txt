Malfix AutoBuy Step 12.3.1 — sources.jar fix

Причина прошлого краша:
- В mods, скорее всего, был положен *sources.jar.
- sources.jar содержит .java исходники, но не содержит .class.
- Поэтому Mixin не нашёл ru.malfix.autobuy.mixin.MinecraftClientMixin.

Что исправлено:
- build.gradle больше не создаёт sources.jar.
- 00_FAST_BUILD_NO_REDOWLOAD.cmd удаляет старые build/libs/*.jar и запускает clean build.

Что делать:
1. Удали старые malfix-autobuy*.jar из папки mods.
2. Собери через 00_FAST_BUILD_NO_REDOWLOAD.cmd.
3. Из build/libs скопируй только обычный malfix-autobuy-*.jar.
4. Не копируй никакой *sources*.jar, если он где-то остался от старой сборки.
