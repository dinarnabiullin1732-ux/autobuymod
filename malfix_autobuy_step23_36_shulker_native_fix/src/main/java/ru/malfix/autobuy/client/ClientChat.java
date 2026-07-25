package ru.malfix.autobuy.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;


import java.util.HashMap;
import java.util.Map;

public final class ClientChat {

    @SuppressWarnings("unused")
    private final MinecraftClient client;

    /**
     * Step 22.34: when 2-3 launchers sell at the same time, stdout spam becomes a real
     * FPS/CPU problem. Keep important lifecycle/errors, but throttle repetitive seller
     * progress lines that can be printed very frequently on every client.
     */
    private static final boolean LOW_LAG_LOGS = true;
    private static final long REPETITIVE_LOG_COOLDOWN_MS = 30_000L;
    private final Map<String, Long> lastVerboseLogAtMs = new HashMap<String, Long>();

    public ClientChat(MinecraftClient client) {
        this.client = client;
    }

    public void send(String message) {
        String safe = message == null ? "" : message;
        if (LOW_LAG_LOGS && shouldSuppressRepetitiveConsoleLine(safe)) {
            return;
        }

        // Step 21.7: keep mod status/debug messages out of the in-game chat.
        // They are still printed to stdout/logs so debugging remains possible.
        System.out.println("[MAB] " + safe);
    }

    private boolean shouldSuppressRepetitiveConsoleLine(String message) {
        String key = repetitiveKey(message);
        if (key == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long last = lastVerboseLogAtMs.get(key);
        if (last != null && now - last.longValue() < REPETITIVE_LOG_COOLDOWN_MS) {
            return true;
        }

        lastVerboseLogAtMs.put(key, Long.valueOf(now));
        return false;
    }

    private String repetitiveKey(String message) {
        if (message == null) {
            return null;
        }

        // Keep starts/stops/errors visible. Suppress only progress/debug spam.
        if (message.startsWith("seller-loop step:")) {
            return "seller-loop step";
        }
        if (message.startsWith("sellreal sending:")) {
            return "sellreal sending";
        }
        if (message.startsWith("sellreal hotbar select:")) {
            return "sellreal hotbar select";
        }
        if (message.startsWith("sellreal move to hotbar:")) {
            return "sellreal move to hotbar";
        }
        if (message.startsWith("seller-loop old unstack tick:")) {
            return "seller-loop old unstack tick";
        }
        if (message.startsWith("seller-loop preparing old unstack:")) {
            return "seller-loop preparing old unstack";
        }
        if (message.startsWith("sell-only storage: movedFromStorage=")) {
            return "sell-only storage moved";
        }
        if (message.startsWith("sell-only storage: open clicked=")) {
            return "sell-only storage open";
        }
        return null;
    }

    public void sendInGame(String message) {
        String safe = message == null ? "" : message;
        System.out.println("[MAB CHAT] " + safe);

        try {
            if (client != null && client.inGameHud != null) {
                client.inGameHud.getChatHud().addMessage(Text.literal(safe));
            }
        } catch (Throwable throwable) {
            System.out.println("[MAB] failed to print in-game chat: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    public void sendDebugBlock(String title, String debugText) {
        String safeTitle = title == null ? "debug:" : title;
        System.out.println("[MAB] " + safeTitle);

        if (debugText == null || debugText.trim().isEmpty()) {
            System.out.println("[MAB] empty debug");
            return;
        }

        System.out.println("[MAB DEBUG BEGIN]");
        System.out.println(debugText);
        System.out.println("[MAB DEBUG END]");
    }
    public void sendInGameBlock(String title, String debugText) {
        String safeTitle = title == null ? "debug:" : title;
        sendInGame("§dMalfix AutoBuy §7» " + safeTitle);

        if (debugText == null || debugText.trim().isEmpty()) {
            sendInGame("§dMalfix AutoBuy §7» empty debug");
            return;
        }

        String[] lines = debugText.split("\\r?\\n");
        int printed = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            sendInGame("§7" + line);
            printed++;
            if (printed >= 24) {
                sendInGame("§7...truncated, see latest.log for full output");
                break;
            }
        }

        // Keep a full copy in latest.log too.
        sendDebugBlock(safeTitle, debugText);
    }

}
