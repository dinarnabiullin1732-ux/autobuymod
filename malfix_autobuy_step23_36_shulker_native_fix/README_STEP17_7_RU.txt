Malfix AutoBuy Step 17.7 — sellcycle: sell then return to /ah

Добавлено:
.mab sellcycle [max] [delayMs]
.mab sellreturn [max] [delayMs]
.mab sellandreturn [max] [delayMs]

Примеры:
.mab sellcycle
.mab sellcycle 10
.mab sellcycle 10 900

Что делает:
1. Запускает seller-loop.
2. Продаёт предметы через уже рабочую безопасную цепочку:
   - из руки;
   - из хотбара;
   - из основного инвентаря через пустой хотбар.
3. Когда seller-loop останавливается:
   - достигнут лимит max;
   - закончились предметы;
   - sellPrice=0;
   - нет пустого хотбар-слота;
   - пойман /ah rent;
   - другой stop reason;
   мод ждёт короткую задержку и отправляет /ah.

Важно:
- Это НЕ включает автобай сам.
- Это НЕ перезапускает seller-loop.
- Это ручной безопасный цикл: продажа -> возврат в /ah.
- Обычный .mab sellloop не изменён: он возвращается в /ah только при /ah rent, как в Step 17.6.

Debug:
.mab debug показывает sellerCycle=enabled/lastReason.
