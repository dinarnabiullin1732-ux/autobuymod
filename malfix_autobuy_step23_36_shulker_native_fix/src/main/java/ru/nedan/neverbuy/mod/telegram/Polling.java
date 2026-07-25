package ru.nedan.neverbuy.mod.telegram;

import ru.malfix.autobuy.MalfixAutoBuyMod;
import ru.malfix.autobuy.client.MalfixClientRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Compatibility shell for scripts expecting NeverBuy telegram polling. */
public class Polling {
    private final List<Consumer<TelegramObject>> callbacks = new ArrayList<Consumer<TelegramObject>>();
    public void addCallback(Consumer<TelegramObject> callback) { if (callback != null) callbacks.add(callback); }
    public void sendMessage(String text) {
        MalfixClientRuntime runtime = MalfixAutoBuyMod.runtime();
        if (runtime != null) runtime.sendTelegramCompat(text);
    }
    public void setToken(String token) { }
    public void setChatID(long chatID) { }
    public String getToken() { return ""; }
    public long getChatID() { return 0L; }
}
