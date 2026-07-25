Step 23.32

Исправления:
1) Telegram: при покупке больше не должен писать unknown на большинстве предметов.
   Название берется из последнего реально кликнутого AuctionSlot, затем из target/catalog.
2) Telegram по-прежнему пишет только фактические сделки: купил / продал. Источник не выводится.
3) Shulker/EC: если shalk.js загружен, он получает приоритет над native storage.
   Native ShulkerController остается fallback только когда скриптов нет.
4) /ec больше не считается экраном скрипта всегда. Эндер-сундук закрывается скриптом
   только если сам скрипт только что отправил /ec. Ручное открытие /ec не должно
   закрываться автобаем.
5) В комплекте оставлен оригинальный shalk.js из Never/SpookyBuy; он лежит в
   ADD_TO_GAME_FOLDER/malfix_autobuy/scripts/shalk.js и совместим через ScriptCompatBridge.

После установки: .script reload
Для диагностики: .mab scripts status и .mab shulker status
