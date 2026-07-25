Step 22.50 — profiler chat + cumulative counters

Исправлено:
- .mab prof status теперь выводит блок прямо в игровой чат через sendInGameBlock, а не только в latest.log/stdout.
- В profiler debug добавлен recentWindow и totalSinceReset.
- recent показывает последнюю примерно 1-секундную выборку.
- total показывает накопленные счетчики с момента .mab prof reset / .mab prof on.

Как тестировать:
1) .mab prof on
2) .mab prof reset
3) 30-60 секунд реально сканировать /ah или запускать fullauto/seller/storage.
4) .mab prof status

Важно:
Если recent нули, но total растет — это нормально: status был вызван в момент, когда scanner/seller уже не работал. Для анализа присылай total.* строки.
Если и recent, и total scanCalls/auctionReads = 0 после работы /ah — значит текущий режим не проходит через AuctionScanner/MinecraftAuctionView, и нужно будет ставить счетчики глубже в tick/fullauto path.
