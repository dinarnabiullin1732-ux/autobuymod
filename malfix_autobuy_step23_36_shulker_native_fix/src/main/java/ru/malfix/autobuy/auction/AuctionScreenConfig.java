package ru.malfix.autobuy.auction;

public final class AuctionScreenConfig {

    private final int firstAuctionSlot;
    private final int auctionSlotCount;
    private final int refreshSlot;

    public AuctionScreenConfig(int firstAuctionSlot, int auctionSlotCount, int refreshSlot) {
        this.firstAuctionSlot = firstAuctionSlot;
        this.auctionSlotCount = auctionSlotCount;
        this.refreshSlot = refreshSlot;
    }

    public static AuctionScreenConfig defaultConfig() {
        return new AuctionScreenConfig(0, 45, 49);
    }

    public int getFirstAuctionSlot() {
        return firstAuctionSlot;
    }

    public int getAuctionSlotCount() {
        return auctionSlotCount;
    }

    public int getRefreshSlot() {
        return refreshSlot;
    }
}
