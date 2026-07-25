package ru.malfix.autobuy.mc;

import net.minecraft.client.MinecraftClient;

/** Chat/command sending split for 1.19+ signed-chat clients. */
public final class McChat {
    private McChat() {
    }

    public static void send(MinecraftClient client, String raw) {
        if (client == null || client.player == null || raw == null) {
            return;
        }
        String message = raw.trim();
        if (message.isEmpty() || client.player.networkHandler == null) {
            return;
        }
        if (message.startsWith("/")) {
            client.player.networkHandler.sendChatCommand(message.substring(1));
        } else {
            client.player.networkHandler.sendChatMessage(message);
        }
    }
}
