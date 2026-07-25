package ru.malfix.autobuy.seller;

import ru.malfix.autobuy.auction.AuctionSlot;
import ru.malfix.autobuy.scanner.ItemMatcher;
import ru.malfix.autobuy.scanner.MatchResult;
import ru.malfix.autobuy.scanner.TargetItem;

import java.util.List;

public final class InventoryItemMatcher {

    private final ItemMatcher itemMatcher;

    public InventoryItemMatcher() {
        this(new ItemMatcher());
    }

    public InventoryItemMatcher(ItemMatcher itemMatcher) {
        this.itemMatcher = itemMatcher == null ? new ItemMatcher() : itemMatcher;
    }

    public MatchResult match(InventoryItemSnapshot item, List<TargetItem> targets) {
        if (item == null) {
            return MatchResult.no("null_inventory_item");
        }

        AuctionSlot fakeSlot = new AuctionSlot(
                item.getInventorySlot(),
                item.getInventorySlot(),
                false,
                item.getItemId(),
                item.getDisplayName(),
                item.getCount(),
                item.getTooltipLines(),
                item.getNbtString()
        );

        return itemMatcher.match(fakeSlot, targets);
    }
}
