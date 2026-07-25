package ru.malfix.autobuy.buy;

import java.util.Locale;

/**
 * Detects server chat responses after a buy click.
 *
 * This detector intentionally does not decide what to do next.
 * It only classifies the message. Later the auto-loop will decide whether to refresh,
 * pause, continue, open shulker/restack, etc.
 */
public final class BuyResultDetector {

    private BuyResult lastResult = BuyResult.none();

    public BuyResult detect(String rawMessage) {
        if (rawMessage == null) {
            return BuyResult.none();
        }

        String raw = stripColorCodes(rawMessage).trim();
        if (raw.isEmpty()) {
            return BuyResult.none();
        }

        String lower = raw.toLowerCase(Locale.ROOT);

        BuyResult result = classify(lower, raw);
        if (result.isDetected()) {
            lastResult = result;
        }

        return result;
    }

    public BuyResult getLastResult() {
        return lastResult;
    }

    private BuyResult classify(String lower, String raw) {
        if (containsAny(lower,
                "не хватает монет",
                "не хватает денег",
                "недостаточно монет",
                "недостаточно денег",
                "недостаточно средств",
                "у вас недостаточно")) {
            return BuyResult.of(BuyResultType.NO_MONEY, "no_money", raw);
        }

        if (containsAny(lower,
                "инвентарь полон",
                "полный инвентарь",
                "у вас полный инвентарь",
                "предмет перенесен в хранилище")) {
            return BuyResult.of(BuyResultType.INVENTORY_FULL, "inventory_full", raw);
        }

        if (containsAny(lower,
                "уже купили",
                "уже куплен",
                "уже продан",
                "лот уже",
                "предмет уже",
                "товар уже",
                "недоступен")) {
            return BuyResult.of(BuyResultType.ALREADY_SOLD, "already_sold", raw);
        }

        if (containsAny(lower,
                "цена изменилась",
                "цена была изменена",
                "цена предмета изменилась",
                "изменил цену",
                "стоимость изменилась")) {
            return BuyResult.of(BuyResultType.PRICE_CHANGED, "price_changed", raw);
        }

        if (containsAny(lower,
                "успешно куп",
                "успешная покуп",
                "вы купили",
                "вы успешно купили",
                "куплено",
                "покупка совершена",
                "покупка выполнена",
                "предмет куплен",
                "товар куплен",
                "вы приобрели",
                "успешно приобр",
                "приобретено",
                "куплен предмет",
                "предмет успешно куплен",
                "спасибо за покупку")) {
            return BuyResult.of(BuyResultType.BUY_SUCCESS, "buy_success", raw);
        }

        if (containsAny(lower,
                "ошибка",
                "не удалось купить",
                "не получилось купить",
                "покупка отменена")) {
            return BuyResult.of(BuyResultType.BUY_FAILED, "generic_buy_failed", raw);
        }

        return BuyResult.none();
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null || needles == null) {
            return false;
        }

        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && value.contains(needle)) {
                return true;
            }
        }

        return false;
    }

    private String stripColorCodes(String text) {
        if (text == null) {
            return "";
        }

        // Handles Minecraft legacy color/control codes.
        return text.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }
}
