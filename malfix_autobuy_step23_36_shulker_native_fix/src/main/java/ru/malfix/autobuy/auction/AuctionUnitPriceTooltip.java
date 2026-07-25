package ru.malfix.autobuy.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

import net.minecraft.text.Text;
import ru.malfix.autobuy.price.ParsedPrice;
import ru.malfix.autobuy.render.AutomationPerformance;
import ru.malfix.autobuy.price.PriceParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adds a lightweight "unit price" line to auction lot tooltips.
 *
 * The mixin calls this only while Minecraft is building the tooltip; this helper
 * keeps all auction/title/price checks out of the mixin itself.
 */
public final class AuctionUnitPriceTooltip {

    private static final PriceParser PRICE_PARSER = new PriceParser();

    private AuctionUnitPriceTooltip() {
    }

    public static void appendIfNeeded(List<Text> tooltip, int stackCount) {
        if (AutomationPerformance.isLowLagActive()) {
            return;
        }
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }

        if (!isAuctionLotTooltipContext()) {
            return;
        }

        if (alreadyHasUnitPrice(tooltip)) {
            return;
        }

        List<String> lines = toPlainLines(tooltip);
        ParsedPrice price = PRICE_PARSER.parse(lines, stackCount);
        if (price == null || !price.isFound() || price.getUnitPrice() <= 0L) {
            return;
        }

        int insertIndex = findPriceLineIndex(lines, price.getSourceLine());
        if (insertIndex < 0) {
            insertIndex = Math.min(tooltip.size(), 2);
        } else {
            insertIndex = Math.min(tooltip.size(), insertIndex + 1);
        }

        Text line = Text.literal("§aЦена за штуку: §f$" + formatMoney(price.getUnitPrice()));

        try {
            tooltip.add(insertIndex, line);
        } catch (UnsupportedOperationException ignored) {
            // Very unlikely for vanilla 1.16.5, but never let tooltip rendering crash the client.
        } catch (Throwable ignored) {
        }
    }

    private static boolean isAuctionLotTooltipContext() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }

        Screen screen = client.currentScreen;
        if (!(screen instanceof GenericContainerScreen)) {
            return false;
        }

        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        String lower = title.toLowerCase(Locale.ROOT);

        if (lower.contains("хранилищ")
                || lower.contains("storage")
                || lower.contains("склад")
                || lower.contains("vault")) {
            return false;
        }

        return lower.contains("аукцион")
                || lower.contains("поиск")
                || lower.contains("search")
                || lower.contains("auction")
                || isPagedSearchTitle(lower);
    }

    private static boolean isPagedSearchTitle(String lower) {
        if (lower == null) {
            return false;
        }
        String s = lower.replace('ё', 'е').trim();
        boolean hasPageMarker = s.contains("[") && s.contains("/") && s.contains("]");
        boolean hasSearchPrefix = s.startsWith("☃ п:") || s.startsWith("п:") || s.contains(" п:");
        return hasPageMarker && hasSearchPrefix;
    }

    private static boolean alreadyHasUnitPrice(List<Text> tooltip) {
        for (Text text : tooltip) {
            if (text == null) {
                continue;
            }

            String line = text.getString();
            if (line == null) {
                continue;
            }

            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("цена за штуку")
                    || lower.contains("цена за 1")
                    || lower.contains("за штуку")
                    || lower.contains("unit price")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> toPlainLines(List<Text> tooltip) {
        List<String> lines = new ArrayList<String>(tooltip.size());
        for (Text text : tooltip) {
            lines.add(text == null ? "" : text.getString());
        }
        return lines;
    }

    private static int findPriceLineIndex(List<String> lines, String sourceLine) {
        if (lines == null || lines.isEmpty()) {
            return -1;
        }

        if (sourceLine != null && !sourceLine.isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (sourceLine.equals(line)) {
                    return i;
                }
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) {
                continue;
            }

            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("цена") || lower.contains("$") || lower.contains("price")) {
                return i;
            }
        }

        return -1;
    }

    private static String formatMoney(long value) {
        if (value <= 0L) {
            return "0";
        }

        String raw = Long.toString(value);
        StringBuilder out = new StringBuilder(raw.length() + raw.length() / 3);
        int firstGroup = raw.length() % 3;
        if (firstGroup == 0) {
            firstGroup = 3;
        }

        out.append(raw, 0, firstGroup);
        for (int i = firstGroup; i < raw.length(); i += 3) {
            out.append(',').append(raw, i, i + 3);
        }
        return out.toString();
    }
}
