Malfix AutoBuy Step 13 — Safety filters + scan settings

Что добавлено:
1. Режимы скана:
   - TOP9
   - TOP18
   - TOP27
   - ALL45

2. Защита цены:
   - requireMaxPrice=true
   - allowUnlimitedPrice=false

Главное правило:
если target включён, но maxUnitPrice=0, автобай НЕ покупает его.
Покупка цели без цены возможна только если явно включить:
.mab set requirePrice false
.mab set allowUnlimited true

3. Refresh timeout:
   - refreshTimeoutMs=900 по умолчанию
   - применяется к ручному refresh, one-cycle и limited-loop

4. Защита от зависшего refresh:
   - maxRefreshFailStreak=3
   - limited-loop останавливается, если refresh несколько раз подряд не меняет fingerprint

5. Blacklist keywords:
   - проверяет displayName, itemId, tooltip, NBT
   - если слово найдено, слот пропускается

Команды:
.mab config
.mab set scan TOP27
.mab set scan ALL45
.mab set requirePrice true
.mab set allowUnlimited false
.mab set refreshTimeout 900
.mab set maxRefreshFails 3

.mab blacklist list
.mab blacklist add "сломанный"
.mab blacklist remove "сломанный"
.mab blacklist clear

Рекомендуемые безопасные настройки:
.mab set scan TOP27
.mab set requirePrice true
.mab set allowUnlimited false
.mab set refreshTimeout 900
.mab set maxRefreshFails 3

Важно:
- GUI цен остаётся из Step 12.5.
- Предмет без цены теперь не будет покупаться даже если случайно включён.
- Это последний важный safety-слой перед нормальным full-auto.
