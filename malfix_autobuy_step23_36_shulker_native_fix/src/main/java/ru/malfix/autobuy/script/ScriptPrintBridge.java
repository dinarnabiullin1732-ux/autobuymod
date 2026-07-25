package ru.malfix.autobuy.script;

import java.util.function.Consumer;

public final class ScriptPrintBridge implements Consumer<Object> {
    @Override
    public void accept(Object message) {
        System.out.println("[MAB SCRIPT] " + String.valueOf(message));
    }
}
