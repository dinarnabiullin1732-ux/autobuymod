package ru.malfix.autobuy.price;

import java.util.List;
import java.util.Locale;
import ru.malfix.autobuy.profiler.MalfixProfiler;

public final class PriceParser {

    public ParsedPrice parse(List<String> tooltipLines, int count) {
        long profStart = MalfixProfiler.start();
        try {
            return parseInternal(tooltipLines, count);
        } finally {
            MalfixProfiler.recordPriceParse(profStart);
        }
    }

    private ParsedPrice parseInternal(List<String> tooltipLines, int count) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            return ParsedPrice.missing();
        }

        ParsedPrice best = ParsedPrice.missing();

        for (String line : tooltipLines) {
            ParsedPrice parsed = parseLine(line, count);
            if (!parsed.isFound()) {
                continue;
            }

            if (!best.isFound() || parsed.getTotalPrice() > best.getTotalPrice()) {
                best = parsed;
            }
        }

        return best;
        }


    public ParsedPrice parseLine(String line, int count) {
        if (line == null || line.trim().isEmpty()) {
            return ParsedPrice.missing();
        }

        String normalized = line.toLowerCase(Locale.ROOT);

        if (!looksLikePriceLine(normalized)) {
            return ParsedPrice.missing();
        }

        long bestNumber = findBestNumber(line);

        if (bestNumber <= 0L) {
            return ParsedPrice.missing();
        }

        int safeCount = Math.max(1, count);
        long unitPrice;

        if (normalized.contains("за штуку")
                || normalized.contains("цена за 1")
                || normalized.contains("unit")) {
            unitPrice = bestNumber;
            return ParsedPrice.of(bestNumber * safeCount, unitPrice, line);
        }

        unitPrice = Math.max(1L, bestNumber / safeCount);
        return ParsedPrice.of(bestNumber, unitPrice, line);
    }

    private boolean looksLikePriceLine(String line) {
        return line.contains("$")
                || line.contains("цена")
                || line.contains("стоимость")
                || line.contains("price")
                || line.contains("cost")
                || line.contains("монет")
                || line.contains("coins")
                || line.contains("money");
    }

    private long findBestNumber(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0L;
        }

        long best = 0L;
        long current = 0L;
        boolean inNumber = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                inNumber = true;
                int digit = c - '0';
                if (current > (Long.MAX_VALUE - digit) / 10L) {
                    current = Long.MAX_VALUE;
                } else {
                    current = current * 10L + digit;
                }
                continue;
            }

            if (inNumber && (c == ' ' || c == ',' || c == '.' || c == '_' || c == '\u00a0')) {
                continue;
            }

            if (inNumber) {
                if (current > best) {
                    best = current;
                }
                current = 0L;
                inNumber = false;
            }
        }

        if (inNumber && current > best) {
            best = current;
        }

        return best;
    }
}
