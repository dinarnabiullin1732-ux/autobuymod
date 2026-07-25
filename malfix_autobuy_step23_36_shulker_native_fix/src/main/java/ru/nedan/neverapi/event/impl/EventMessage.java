package ru.nedan.neverapi.event.impl;

public class EventMessage {
    private final String message;
    private final boolean send;

    public EventMessage() {
        this("", false);
    }

    public EventMessage(String message, boolean send) {
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
