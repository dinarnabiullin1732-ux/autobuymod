Malfix AutoBuy Step 11 — Config + Targets

Это этап конфигурации. Теперь цели, цены, задержка и лимиты loop не зашиты только в код.

Файл конфига создаётся тут:
.minecraft/config/malfix-autobuy.json

Новые команды:
.mab config
.mab set delay <ms>
.mab set cycles <count>
.mab set buys <count>
.mab targets
.mab target list
.mab target add "Label" <maxUnitPrice> "contains text"
.mab target price "Label" <maxUnitPrice>
.mab target enable "Label"
.mab target disable "Label"
.mab target remove "Label"
.mab target reset
.mab save
.mab reload

Примеры:
.mab config
.mab set delay 300
.mab set cycles 10
.mab set buys 3

.mab targets
.mab target add "Talisman Yarosti Cheap" 12000000 "талисман ярости"
.mab target price "Talisman Yarosti Cheap" 11000000
.mab target disable "Talisman Yarosti Cheap"
.mab target enable "Talisman Yarosti Cheap"
.mab target remove "Talisman Yarosti Cheap"

Важно:
- maxUnitPrice=0 означает без ограничения цены.
- Scanner теперь берёт цели из конфига.
- .mab loop без аргументов использует defaultLoopCycles/defaultLoopBuys из конфига.
- loopDelayMs из конфига применяется к limited-loop.
- Команды с кавычками поддерживаются: "Талисман Ярости".

Как тестить:
1. 00_FAST_BUILD_NO_REDOWLOAD.cmd
2. JAR из build\libs кинуть в mods.
3. В игре:
   .mab on
   .mab config
   .mab targets
4. Открыть /ah и запустить:
   .mab loop
   или RightShift+L

Следующий шаг после проверки:
Step 12 — более нормальная настройка профилей и режимов скана:
- первые ряды / все 45 слотов;
- min/max цена;
- blacklist;
- cooldown на repeated NO_MATCH;
- настройка refresh timeout.
