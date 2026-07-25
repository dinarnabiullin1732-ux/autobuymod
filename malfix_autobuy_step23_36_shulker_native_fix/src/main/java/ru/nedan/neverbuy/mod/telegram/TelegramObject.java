package ru.nedan.neverbuy.mod.telegram;

public final class TelegramObject {
    private final String text;
    public TelegramObject(String text) { this.text = text == null ? "" : text; }
    public String text() { return text; }
    public String getText() { return text; }
    @Override public String toString() { return text; }
}
