package ru.malfix.autobuy.buy;

public enum BuyResultType {
    NONE,
    BUY_SUCCESS,
    NO_MONEY,
    ALREADY_SOLD,
    PRICE_CHANGED,
    INVENTORY_FULL,
    BUY_FAILED,
    UNKNOWN_FAIL
}
