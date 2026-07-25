package ru.malfix.autobuy.seller;

public final class SellCommandBuilder {

    public String build(long price) {
        long safePrice = Math.max(0L, price);
        return "/ah sell " + safePrice;
    }
}
