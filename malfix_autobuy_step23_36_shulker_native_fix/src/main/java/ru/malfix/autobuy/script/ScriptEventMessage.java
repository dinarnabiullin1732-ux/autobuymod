package ru.malfix.autobuy.script;

public final class ScriptEventMessage {
    private final String message;
    private final boolean send;

    public ScriptEventMessage(String message, boolean send) {
        this.message = message == null ? "" : message;
        this.send = send;
    }

    public boolean isSend() {
        return send;
    }

    public String getMessage() {
        return message;
    }
}
