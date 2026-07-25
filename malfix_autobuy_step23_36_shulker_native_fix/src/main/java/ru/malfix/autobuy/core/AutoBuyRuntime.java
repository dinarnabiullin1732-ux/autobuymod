package ru.malfix.autobuy.core;

import ru.malfix.autobuy.auction.AuctionView;
import ru.malfix.autobuy.scanner.AuctionScanner;

public final class AutoBuyRuntime {

    private final AutoBuyController controller;

    public AutoBuyRuntime() {
        this.controller = new AutoBuyController(null, null);
    }

    public AutoBuyRuntime(AuctionView auctionView, AuctionScanner scanner) {
        this.controller = new AutoBuyController(auctionView, scanner);
    }

    public void enable() {
        controller.enable();
    }

    public void disable() {
        controller.disable();
    }

    public void tick() {
        controller.tick();
    }

    public String debug() {
        return controller.debug();
    }

    public AutoBuyController controller() {
        return controller;
    }
}
