package ru.malfix.autobuy.core;

public enum AutoBuyState {
    DISABLED,
    IDLE,
    OPEN_AUCTION,
    WAIT_AUCTION,
    REFRESH_AUCTION,
    WAIT_REFRESH,
    SCAN,
    TRY_BUY,
    WAIT_BUY_RESULT,
    PAUSED,
    ERROR_RECOVERY
}
