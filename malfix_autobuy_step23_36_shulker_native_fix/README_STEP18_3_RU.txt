Malfix AutoBuy Step 18.3 — sell limit handling in cyclefullloop + auction refresh 300ms

Изменено:
1. В cyclefullloop после sell_limit_detected / /ah rent:
   - seller останавливается;
   - мод возвращается в /ah;
   - включается флаг skipNextSeller;
   - следующий полный cyclefull после покупки НЕ запускает seller сразу;
   - он возвращается в /ah и продолжает следующий buy-cycle.
2. Это защищает от ситуации, когда слоты продажи забиты и мод снова сразу спамит /ah sell.
3. После одного пропуска seller будет снова проверен на следующем полном цикле.
4. Обновление/задержка buy-loop выставляется в 300ms при запуске cyclefull/cyclefullloop:
   - config.loopDelayMs = 300

Команды:
.mab cyclefull 10 3 10 900
.mab cyclefullloop 5 10 3 10 900 1500

Debug:
cycleFullLoop теперь показывает:
skipNextSeller
skipSellerReason
auctionRefreshMs в стартовом сообщении
