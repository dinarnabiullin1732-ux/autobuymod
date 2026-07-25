Malfix AutoBuy Step 16.2 — Seller markup

Проблема:
- Раньше seller использовал цену из GUI как цену продажи.
- Но эта цена в GUI — это max buy price, то есть максимальная цена покупки.
- Продавать по ней нельзя, нужно дороже.

Что изменено:
1. Добавлен глобальный seller markup:
   seller.markupPercent = 10 по умолчанию

2. Цена продажи теперь считается так:
   sellUnitPrice = buyMaxUnitPrice * (100 + markupPercent) / 100

3. Общая команда продажи:
   totalPrice = sellUnitPrice * stackCount

Пример:
buyMaxUnitPrice = 2500
sellerMarkup = 10%
sellUnitPrice = 2750
count = 32
command = /ah sell 88000

Новая команда:
.mab set sellMarkup 10
.mab set sellMarkup 20
.mab set markup 15

Проверка:
.mab config
.mab selltest

В selltest теперь должно быть:
buyMaxUnitPrice=...
sellerMarkup=...%
sellUnitPrice=...
totalPrice=...
seller command preview: /ah sell ...

Важно:
- Реальной продажи всё ещё нет.
- Это только безопасный preview.
- Следующий шаг: Step 16.3 — real sell только когда handPlan=READY_IN_HAND.
