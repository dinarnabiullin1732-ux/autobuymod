package ru.malfix.autobuy.script;

import java.util.function.BiConsumer;

public final class ScriptEventBus implements BiConsumer<Object, Object> {
    private final MalfixScriptManager manager;

    public ScriptEventBus(MalfixScriptManager manager) {
        this.manager = manager;
    }

    @Override
    public void accept(Object eventClassName, Object handler) {
        manager.register(eventClassName, handler);
    }
}
