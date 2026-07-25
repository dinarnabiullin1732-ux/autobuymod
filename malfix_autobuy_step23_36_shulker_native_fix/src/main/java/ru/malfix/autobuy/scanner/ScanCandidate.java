package ru.malfix.autobuy.scanner;

import ru.malfix.autobuy.auction.AuctionSlot;
import ru.malfix.autobuy.price.ParsedPrice;

public final class ScanCandidate {

    private final AuctionSlot auctionSlot;
    private final TargetItem target;
    private final ParsedPrice price;

    public ScanCandidate(AuctionSlot auctionSlot, TargetItem target, ParsedPrice price) {
        this.auctionSlot = auctionSlot;
        this.target = target;
        this.price = price;
    }

    public AuctionSlot getAuctionSlot() {
        return auctionSlot;
    }

    public TargetItem getTarget() {
        return target;
    }

    public ParsedPrice getPrice() {
        return price;
    }

    public boolean isBetterThan(ScanCandidate other) {
        if (other == null) {
            return true;
        }

        long thisUnit = price == null ? Long.MAX_VALUE : price.getUnitPrice();
        long otherUnit = other.price == null ? Long.MAX_VALUE : other.price.getUnitPrice();

        if (thisUnit != otherUnit) {
            return thisUnit < otherUnit;
        }

        return auctionSlot.getAuctionIndex() < other.auctionSlot.getAuctionIndex();
    }
}
