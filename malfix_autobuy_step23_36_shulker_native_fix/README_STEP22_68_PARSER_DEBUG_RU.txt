Step 22.68 — диагностика парсера /ah search без гадания

Основа: Step 22.66. Step 22.67 с ДонатМаркетом НЕ используется как рабочий фикс для этой проблемы.

Зачем:
- Череп/голова визер-скелета, Зачарованное золотое яблоко и Нагрудник крушителя покупаются/продаются, если цены выставлены вручную, но не парсятся через /ah search.
- Значит основной buy/sell matcher, скорее всего, живой; ломается именно parser/highlighter path: чтение слотов, цена из tooltip, strict matcher, search-screen check или старый target config.

Что добавлено:
1) Новая команда:
   .mab parsedebug "Нагрудник крушителя"
   .mab parsedebug "Зачарованное золотое яблоко"
   .mab parsedebug "Череп визер-скелета"

   Команду надо вызывать, когда открыта нужная страница /ah search с предметами.
   В чат выводится короткий summary, полный блок пишется в latest.log.

2) Автоматический dump при провале auto-parser:
   Если parser после retry пропускает цель из-за no search screen или no priced lot, он пишет полный блок:
   ========== [MAB PARSER DEBUG BEGIN] ==========
   ...
   ========== [MAB PARSER DEBUG END] ==========

3) В latest.log теперь выводится по текущей странице:
   - screenTitle, isAuctionOpen, isSearchResult, parser phase;
   - target config compact: itemId, tagContains, contains, enabled/parserEnabled;
   - query, который parser использует для /ah search;
   - parserCheapest: самый дешевый лот по логике parser-а, без strict matcher;
   - strictCheapest: самый дешевый лот по scanner strict matcher;
   - по слотам 0..53:
     itemId, name, count, price parse result, source price line,
     strict match true/false, match reason,
     debugMatch: expectedId/actualId/idOk/fastCandidate/tagExplain,
     compact tooltip и NBT/SNBT.

Как тестировать:
1. Открыть проблемную страницу вручную:
   /ah search Нагрудник крушителя
2. Когда предметы видны, написать:
   .mab parsedebug "Нагрудник крушителя"
3. Скинуть блок latest.log между:
   [MAB PARSER DEBUG BEGIN]
   [MAB PARSER DEBUG END]

Как читать результат:
- slots=<empty> или isAuctionOpen=false -> parser не видит GUI/слоты.
- nonEmpty>0, priced=0 -> PriceParser не распознаёт цену из tooltip этих предметов.
- priced>0, parserCheapest=none -> проблема в первом pass поиска цены.
- parserCheapest есть, но strictCheapest=none -> strict matcher отбрасывает target; смотреть debugMatch/tagExplain.
- actualId != expectedId -> itemId отличается от target config.
- tagExplain=fail(missing_group: ...) -> не найден нужный NBT/enchant/tag token.

Не тронуто:
- refresh 300ms из Step 22.61;
- seller/autosell 500ms из Step 22.62;
- Anti-AFK из Step 22.64;
- Нагрудник крушителя из Step 22.66: minecraft:netherite_chestplate + chestplate-kryshitel + обязательные основные чары, thorns optional;
- skull/apple itemId-only;
- potion staged drag-unstack.
