Step 22.33 — .potatomode как в старом SpookyBuy

База: Step 22.32.

Добавлено:
- команда .potatomode on/off/toggle/status;
- алиас .potato on/off/toggle/status;
- алиас через .mab: .mab potatomode on/off/toggle/status;
- режим хранится в статическом PotatoMode и не трогает покупку/селлер/SellOnly;
- добавлены методы совместимости в ru.nedan.spookybuy.SpookyBuy: isPotatoMode/setPotatoMode.

Что отключает PotatoMode:
- рендер блоков;
- рендер жидкостей;
- рендер block entity;
- рендер entity;
- тени entity;
- частицы и emitter particles;
- renderLayer мира.

Не трогал:
- SellOnly bind;
- SellOnly 60s;
- задержку продажи 650ms;
- NO_MONEY guard;
- рабочее расстакивание;
- фикс ложного совпадения Божье касание/Спавнер.
