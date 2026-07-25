package ru.malfix.autobuy.script;

import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;
import ru.malfix.autobuy.mc.McChat;

public final class ScriptChatBridge implements Consumer<Object> {
    private final MinecraftClient client;

    public ScriptChatBridge(MinecraftClient client) {
        this.client = client;
    }

    @Override
    public void accept(Object message) {
        send(message);
    }

    public void call(Object message) {
        send(message);
    }

    private void send(Object message) {
        if (client == null || client.player == null || message == null) {
            return;
        }
        String command = String.valueOf(message).trim();
        if (command.isEmpty()) {
            return;
        }
        try {
            ScriptCompatBridge.noteScriptCommandSent(command);
            McChat.send(client, command);
        } catch (Throwable throwable) {
            System.out.println("[MAB SCRIPT] chat send failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }
}
