package ru.malfix.autobuy.price;

public final class ParsedPrice {

    private final boolean found;
    private final long totalPrice;
    private final long unitPrice;
    private final String sourceLine;

    private ParsedPrice(boolean found, long totalPrice, long unitPrice, String sourceLine) {
        this.found = found;
        this.totalPrice = Math.max(0L, totalPrice);
        this.unitPrice = Math.max(0L, unitPrice);
        this.sourceLine = sourceLine == null ? "" : sourceLine;
    }

    public static ParsedPrice missing() {
        return new ParsedPrice(false, 0L, 0L, "");
    }

    public static ParsedPrice of(long totalPrice, long unitPrice, String sourceLine) {
        return new ParsedPrice(true, totalPrice, unitPrice, sourceLine);
    }

    public boolean isFound() {
        return found;
    }

    public long getTotalPrice() {
        return totalPrice;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public String getSourceLine() {
        return sourceLine;
    }

    @Override
    public String toString() {
        if (!found) {
            return "ParsedPrice{missing}";
        }

        return "ParsedPrice{total=" + totalPrice
                + ", unit=" + unitPrice
                + ", source='" + sourceLine + "'}";
    }
}
