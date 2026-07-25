package ru.malfix.autobuy.client;

import java.util.Locale;

/**
 * Prevents duplicated handling of the same incoming server chat message.
 *
 * Some servers/clients can surface the same GameMessage packet around Netty/main-thread timing.
 * A buy result must never be applied twice to the same pending buy/loop.
 */
public final class ChatResultDeduplicator {

    private static final long DEFAULT_WINDOW_MS = 650L;

    private final long windowMs;

    private String lastKey = "";
    private long lastAtMs = 0L;
    private int duplicateCount = 0;

    public ChatResultDeduplicator() {
        this(DEFAULT_WINDOW_MS);
    }

    public ChatResultDeduplicator(long windowMs) {
        if (windowMs < 100L) {
            this.windowMs = 100L;
        } else if (windowMs > 5000L) {
            this.windowMs = 5000L;
        } else {
            this.windowMs = windowMs;
        }
    }

    public boolean isDuplicate(String message, String classifier) {
        long now = System.currentTimeMillis();
        String key = makeKey(message, classifier);

        if (!key.isEmpty() && key.equals(lastKey) && now - lastAtMs <= windowMs) {
            duplicateCount++;
            lastAtMs = now;
            return true;
        }

        lastKey = key;
        lastAtMs = now;
        return false;
    }

    public String getLastKey() {
        return lastKey;
    }

    public long getLastAtMs() {
        return lastAtMs;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public long getWindowMs() {
        return windowMs;
    }

    private String makeKey(String message, String classifier) {
        String cls = classifier == null ? "" : classifier.trim().toLowerCase(Locale.ROOT);
        String msg = normalize(message);
        if (cls.isEmpty() && msg.isEmpty()) {
            return "";
        }
        return cls + "|" + msg;
    }

    private String normalize(String message) {
        if (message == null) {
            return "";
        }

        String value = stripColorCodes(message)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();

        if (value.length() > 220) {
            value = value.substring(0, 220);
        }

        return value;
    }

    private String stripColorCodes(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }
}
