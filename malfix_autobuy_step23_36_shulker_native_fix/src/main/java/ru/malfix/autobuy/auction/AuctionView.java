package ru.malfix.autobuy.auction;

import java.util.List;

public interface AuctionView {

    boolean isAuctionOpen();

    void requestOpenAuction();

    void closeCurrentScreen();

    boolean clickRefresh();

    /**
     * Clicks a raw ScreenHandler/container slot.
     * Returns true only if the click was actually sent to Minecraft interactionManager.
     */
    boolean clickContainerSlot(int containerSlotId, int button);

    /**
     * Clicks an auction slot candidate by its current container slot id.
     */
    boolean clickAuctionSlot(AuctionSlot slot);

    /**
     * Buy shortcut click for servers where auction purchase is done by Ctrl + LMB.
     * Internally this is sent as the special slot action used by the container click pipeline.
     */
    boolean ctrlLeftClickAuctionSlot(AuctionSlot slot);

    List<AuctionSlot> readAuctionSlots();

    /**
     * Returns the currently visible player balance/coins if it can be read from HUD/scoreboard.
     * -1 means unknown; unknown balance must not block buying.
     */
    default long readPlayerBalance() {
        return -1L;
    }
}
