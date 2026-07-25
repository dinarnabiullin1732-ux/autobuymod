Malfix AutoBuy Step 17.5 — Seller-loop stop on sell limit chat message

Добавлено:
seller-loop теперь отслеживает сообщения сервера о лимите продажи.

Если в чате появляется сообщение с признаками:
- /ah rent
- аренда слотов
- лимит продаж
- нет слотов продажи
- нельзя выставить больше предметов
- достигнут максимум продаж

то seller-loop:
1. сразу останавливается;
2. не продолжает спамить /ah sell;
3. пишет причину:
   sell_limit_detected:<reason>

Команды не изменились:
.mab sellloop [max] [delayMs]
.mab sellstop
.mab debug

В debug добавлено:
lastSellLimitDetected
lastSellLimitReason
lastSellLimitAtMs
lastSellLimitMessage

Важно:
- Остановка работает именно по серверному сообщению.
- Логика продажи из руки/хотбара/инвентаря не менялась.
