Malfix AutoBuy Step 16 — Seller Base / Preview

Это НЕ реальный селлер. Это безопасная база селлера.

Добавлено:
seller/
 ├─ InventoryItemSnapshot.java
 ├─ InventoryItemMatcher.java
 ├─ SellCommandBuilder.java
 ├─ SellerResult.java
 └─ SellerController.java

Новая команда:
.mab selltest
.mab sellpreview
.mab seller

Что делает selltest:
1. Сканирует 36 слотов инвентаря.
2. Ищет предметы, которые включены в GUI.
3. Проверяет, что у цели есть цена больше 0.
4. Матчит itemId / name / tooltip / NBT.
5. Показывает, какой предмет был бы продан.
6. Показывает команду:
   /ah sell <price>

Важно:
- selltest НЕ двигает предметы.
- selltest НЕ берёт предмет в руку.
- selltest НЕ отправляет /ah sell.
- это только preview.

Пример вывода:
seller preview: status=FOUND_PREVIEW ...
seller found: slot=..., item=..., count=..., target=..., price=...
seller command preview: /ah sell 12000000

Следующий шаг:
Step 16.1 — move-to-hand dry-run / проверка выбранного слота.
Step 16.2 — реальная отправка /ah sell только после проверки, что предмет точно в руке.
