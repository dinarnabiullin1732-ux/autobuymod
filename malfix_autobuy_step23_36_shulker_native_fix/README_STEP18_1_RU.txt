Malfix AutoBuy Step 18.1 — cyclefull close auction before seller

Исправлено:
В Step 18 после buy-loop мод сразу пытался запускать sellcycle, пока окно /ah ещё было открыто.
Из-за этого seller писал:
seller-loop blocked: close current GUI first
и cyclefull просто возвращался в /ah.

Теперь логика такая:
1. cyclefull покупает через limited-loop.
2. После завершения buy-loop переходит в фазу prepare_sell.
3. Закрывает окно аукциона.
4. Ждёт 650ms.
5. Проверяет, что блокирующий GUI закрыт.
6. Только после этого запускает sellcycle.
7. После seller-цикла возвращается в /ah.

Команда:
.mab cyclefull 10 3 10 900
