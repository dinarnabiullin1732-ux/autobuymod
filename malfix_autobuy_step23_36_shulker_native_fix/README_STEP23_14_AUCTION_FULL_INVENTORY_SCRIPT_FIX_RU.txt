Step23.14

Фикс legacy shalk.js после step23_13:
- аукцион больше не закрывается циклом, когда инвентарь не полный;
- если /ah открыт и инвентарь реально полный, JS tick снова разрешается;
- startShulkerSequence/startEcSequence теперь закрывают /ah только для перехода к шалкеру/EC при полном инвентаре;
- full-inventory сообщение не передается скрипту, если локально инвентарь уже не полный;
- добавлены compat методы isInventoryFullEnough(), closeAuctionScreenForStorage().

После обновления: .mab scripts reload или перезапуск клиента.
