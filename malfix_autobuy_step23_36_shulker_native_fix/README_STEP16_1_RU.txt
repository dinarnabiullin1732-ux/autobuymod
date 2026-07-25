Malfix AutoBuy Step 16.1 — Seller total price + hand dry-run

Исправлено:
1. /ah sell теперь считается по общей цене стака:
   totalPrice = unitPriceFromGui * itemCount

Пример:
GUI price = 2500
count = 32
command = /ah sell 80000

2. selltest показывает:
   - unitPrice
   - totalPrice
   - command preview
   - hand dry-run

3. hand dry-run:
   READY_IN_HAND
   - предмет уже выбран в руке.

   CAN_SELECT_HOTBAR_<slot>
   - предмет в хотбаре, следующий шаг сможет просто выбрать слот.

   NEEDS_MOVE_TO_HOTBAR_FROM_SLOT_<slot>
   - предмет в основном инвентаре, перед реальной продажей его нужно безопасно переместить в хотбар/руку.

Важно:
- Реальной продажи всё ещё нет.
- Предметы не двигаются.
- /ah sell не отправляется.
- Это проверка перед реальным seller executor.

Команда:
.mab selltest

Следующий шаг:
Step 16.2 — безопасный выбор предмета в руку:
- если предмет уже в хотбаре, выбрать слот;
- если предмет в основном инвентаре, пока НЕ трогать или делать отдельную безопасную схему move-to-hotbar;
- проверить, что в руке именно тот предмет;
- всё ещё без /ah sell.
