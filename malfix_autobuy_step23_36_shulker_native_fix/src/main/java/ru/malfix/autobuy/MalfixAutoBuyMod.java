package ru.malfix.autobuy;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import ru.malfix.autobuy.client.MalfixClientRuntime;

/**
 * Step 8 entrypoint.
 *
 * This version is observer/debug plus manual refresh-cycle test. It never buys
 * and never starts the automatic auction loop from .mab on. It is meant to test
 * auction detection, fingerprints, refresh button clicks and scanner accuracy.
 */
public final class MalfixAutoBuyMod implements ClientModInitializer {

    private static MalfixClientRuntime runtime;

    @Override
    public void onInitializeClient() {
        runtime = new MalfixClientRuntime(MinecraftClient.getInstance());
        System.out.println("[MalfixAutoBuy] Step 22.52 profiler diagnostics + scanner low-garbage counters loaded.");
        System.out.println("[MalfixAutoBuy] Commands: .mab help, .mab gui, .mab binds, .mab config, .mab set scan, .mab blacklist, .mab target, .mab loop, .mab stop");
        System.out.println("[MalfixAutoBuy] Keys: configurable in GUI -> Бинды. Default auto: RightShift+L");
    }

    public static void onClientTick(MinecraftClient client) {
        MalfixClientRuntime current = runtime;
        if (current != null) {
            current.onClientTick(client);
        }
    }

    public static boolean handleClientChatMessage(String message) {
        MalfixClientRuntime current = runtime;
        return current != null && current.handleClientChatMessage(message);
    }


    public static void handleServerChatMessage(String message) {
        final String safeMessage = message == null ? "" : message;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            MalfixClientRuntime current = runtime;
            if (current != null) {
                current.handleServerChatMessage(safeMessage);
            }
            return;
        }

        client.execute(new Runnable() {
            @Override
            public void run() {
                MalfixClientRuntime current = runtime;
                if (current != null) {
                    current.handleServerChatMessage(safeMessage);
                }
            }
        });
    }

    public static MalfixClientRuntime runtime() {
        return runtime;
    }
}
