package ru.malfix.autobuy.buy;

import ru.malfix.autobuy.auction.AuctionFingerprint;
import ru.malfix.autobuy.auction.AuctionSlot;
import ru.malfix.autobuy.auction.AuctionView;
import ru.malfix.autobuy.scanner.AuctionScanner;
import ru.malfix.autobuy.scanner.ScanCandidate;
import ru.malfix.autobuy.scanner.ScanResult;

import java.util.Collections;
import java.util.List;

/**
 * Step 6 dry-run buy validator.
 *
 * This class does NOT click anything.
 * It only checks that the current auction page has a stable best candidate.
 */
public final class BuyDryRunExecutor {

    private final AuctionView auctionView;
    private final AuctionScanner scanner;

    private BuyDryRunResult lastResult = BuyDryRunResult.idle();

    public BuyDryRunExecutor(AuctionView auctionView, AuctionScanner scanner) {
        this.auctionView = auctionView;
        this.scanner = scanner;
    }

    public BuyDryRunResult dryRun() {
        try {
            if (auctionView == null) {
                lastResult = BuyDryRunResult.error("auction_view_missing");
                return lastResult;
            }

            if (!auctionView.isAuctionOpen()) {
                lastResult = BuyDryRunResult.auctionNotOpen();
                return lastResult;
            }

            List<AuctionSlot> beforeSlots = safeReadSlots();
            int beforeFingerprint = AuctionFingerprint.compute(beforeSlots);

            ScanResult scanResult = scanner == null
                    ? ScanResult.error("scanner_missing")
                    : scanner.scan(beforeSlots);

            if (scanResult == null || !scanResult.hasBestCandidate()) {
                int checked = scanResult == null ? beforeSlots.size() : scanResult.getCheckedSlots();
                lastResult = BuyDryRunResult.noMatch(beforeFingerprint, checked, scanResult);
                return lastResult;
            }

            ScanCandidate candidate = scanResult.getBestCandidate();

            List<AuctionSlot> verifySlots = safeReadSlots();
            int afterFingerprint = AuctionFingerprint.compute(verifySlots);

            if (beforeFingerprint != afterFingerprint) {
                lastResult = BuyDryRunResult.slotChanged(
                        beforeFingerprint,
                        afterFingerprint,
                        verifySlots.size(),
                        scanResult,
                        candidate,
                        "auction_changed_between_scan_and_verify"
                );
                return lastResult;
            }

            if (!sameSlotStillThere(candidate, verifySlots)) {
                lastResult = BuyDryRunResult.slotChanged(
                        beforeFingerprint,
                        afterFingerprint,
                        verifySlots.size(),
                        scanResult,
                        candidate,
                        "candidate_slot_changed"
                );
                return lastResult;
            }

            lastResult = BuyDryRunResult.ready(beforeFingerprint, beforeSlots.size(), scanResult, candidate);
            return lastResult;
        } catch (Throwable throwable) {
            lastResult = BuyDryRunResult.error(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            return lastResult;
        }
    }

    public BuyDryRunResult getLastResult() {
        return lastResult;
    }

    private boolean sameSlotStillThere(ScanCandidate candidate, List<AuctionSlot> slots) {
        if (candidate == null || candidate.getAuctionSlot() == null || slots == null) {
            return false;
        }

        AuctionSlot original = candidate.getAuctionSlot();
        int index = original.getAuctionIndex();

        if (index < 0 || index >= slots.size()) {
            return false;
        }

        AuctionSlot current = slots.get(index);
        if (current == null || current.isEmpty()) {
            return false;
        }

        return original.getContainerSlotId() == current.getContainerSlotId()
                && original.getItemId().equals(current.getItemId())
                && original.getDisplayName().equals(current.getDisplayName())
                && original.getCount() == current.getCount()
                && original.stableHash() == current.stableHash();
    }

    private List<AuctionSlot> safeReadSlots() {
        try {
            List<AuctionSlot> slots = auctionView.readAuctionSlots();
            return slots == null ? Collections.<AuctionSlot>emptyList() : slots;
        } catch (Throwable throwable) {
            return Collections.emptyList();
        }
    }
}
