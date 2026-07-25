Step 22.35 — old Spooky-style storage drain

База: Step 22.34 seller_low_lag.

Изменено только поведение сбора ресурсов из хранилища для SellOnly / storagecycle / cyclefull:
- больше не используется массовый quick-move по 36 слотам хранилища;
- сбор сделан как в старом SpookyBuy resell: один storage-click за раз, примерно раз в 400ms;
- используется прямой ClickSlotC2SPacket с SlotActionType.PICKUP по первому непустому storage slot, как старый clickSilent;
- последний ряд GUI хранилища по-прежнему считается контрольным рядом и не кликается как предметы;
- когда инвентарь заполнен или нужно оставить 1 пустой слот под расстак, бот закрывает хранилище, запускает расстак/продажу, затем снова открывает хранилище и продолжает забирать оставшиеся предметы;
- если хранилище стало пустым, бот закрывает GUI и переходит к продаже/завершению цикла.

Не трогалось:
- задержка продажи 650ms;
- SellOnly interval 60s;
- SellOnly bind;
- NO_MONEY guard;
- рабочее расстакивание;
- PotatoMode;
- фикс ложного совпадения Божье касание/Спавнер;
- оптимизации Step 22.34 против лагов при продаже.

Проверка:
.potatomode on
.mab sellonly on
.mab sellonly status

В логах должны появляться статусы вида:
- took_one:slot=0
- inventory_full_continue_after_sell
- continue_storage_after_selling
- storage_empty
