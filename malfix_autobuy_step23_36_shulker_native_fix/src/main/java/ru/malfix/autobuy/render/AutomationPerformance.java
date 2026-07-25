package ru.malfix.autobuy.render;

/**
 * Shared low-lag flag for render/UI helpers.
 * When an automation mode is running we do not need visual auction helpers that
 * rebuild tooltips or scan screen slots on every render call.
 */
public final class AutomationPerformance {
    private static volatile boolean automationActive = false;

    private AutomationPerformance() {
    }

    public static boolean isAutomationActive() {
        return automationActive;
    }

    public static boolean isLowLagActive() {
        return automationActive || PotatoMode.isEnabled();
    }

    public static void setAutomationActive(boolean value) {
        automationActive = value;
    }
}
