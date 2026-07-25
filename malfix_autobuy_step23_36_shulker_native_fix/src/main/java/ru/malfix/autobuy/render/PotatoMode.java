package ru.malfix.autobuy.render;

/**
 * Minimal FPS mode copied by behavior from the old SpookyBuy potato mode.
 * It is intentionally static: render mixins must be able to check it without
 * touching the autobuy cycle/seller state.
 */
public final class PotatoMode {
    private static volatile boolean enabled = false;

    private PotatoMode() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }
}
