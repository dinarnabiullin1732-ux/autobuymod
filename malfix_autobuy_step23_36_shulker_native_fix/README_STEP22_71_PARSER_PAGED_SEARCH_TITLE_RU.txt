Step 22.71 — fix parser/highlighter for paged /ah search titles

Причина по логам Step 22.70:
- debug raw-container видел предметы и цены;
- strictMatched был >0;
- но обычный parser видел isAuctionOpen=false / isSearchResult=false;
- title страницы был формата: "☃ П: Нагрудник Крушителя [1/4]", "☃ П: Зачарованное золотое яблоко [1/11]", "☃ П: Череп визер-скелета [1/1]".

Исправлено:
1. Auto-parser теперь распознаёт paged-search title формата "☃ П: ... [x/y]" как страницу /ah search.
2. В wait_auction parser больше не требует auctionView.isAuctionOpen(), если открыт GenericContainerScreen с priced auction lots после нашей команды /ah search.
3. Для parser чтение search-страницы теперь fallback'ится на raw container slots, если обычный auctionView не распознал title.
4. Cheapest highlighter теперь тоже принимает "☃ П: ... [x/y]" и должен отмечать cheapest lot на таких страницах.
5. Unit-price tooltip и FPS overlay также считают такой title auction/search-like.

Не тронуто:
- Step 22.66 matcher Нагрудника крушителя: netherite_chestplate + chestplate-kryshitel + обязательные основные enchants, thorns optional;
- skull/apple itemId-only;
- refresh 300ms;
- seller/autosell 500ms;
- Anti-AFK 5 минут;
- potion staged drag-unstack.
