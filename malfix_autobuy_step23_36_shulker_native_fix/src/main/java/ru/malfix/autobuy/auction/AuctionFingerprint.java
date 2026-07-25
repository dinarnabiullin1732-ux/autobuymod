package ru.malfix.autobuy.auction;

import java.util.List;

public final class AuctionFingerprint {

    private AuctionFingerprint() {
    }

    public static int compute(List<AuctionSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return 0;
        }

        int result = 1;
        for (AuctionSlot slot : slots) {
            if (slot == null) {
                result = 31 * result;
            } else {
                result = 31 * result + slot.stableHash();
            }
        }

        return result;
    }
}
