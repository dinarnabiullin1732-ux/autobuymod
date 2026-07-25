package ru.malfix.autobuy.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;
import ru.malfix.autobuy.client.MalfixClientRuntime;

public final class ParserConfigScreen extends Screen {

    private enum FocusField {
        NONE,
        BUY_PERCENT,
        SELL_PERCENT,
        OPEN_WAIT
    }

    private static final int PANEL_W = 330;
    private static final int PANEL_H = 184;

    private static int rememberedPanelX = Integer.MIN_VALUE;
    private static int rememberedPanelY = Integer.MIN_VALUE;

    private final MalfixClientRuntime runtime;
    private final Screen parent;

    private int panelX;
    private int panelY;

    private String buyPercentText = "80";
    private String sellPercentText = "90";
    private String openWaitText = "650";
    private FocusField focusField = FocusField.NONE;
    private boolean replaceFieldOnNextTyping = false;
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public ParserConfigScreen(MalfixClientRuntime runtime, Screen parent) {
        super(Text.literal("MalfixAutoBuy — Парсер"));
        this.runtime = runtime;
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (rememberedPanelX != Integer.MIN_VALUE && rememberedPanelY != Integer.MIN_VALUE) {
            panelX = clamp(rememberedPanelX, 4, Math.max(4, this.width - PANEL_W - 4));
            panelY = clamp(rememberedPanelY, 4, Math.max(4, this.height - PANEL_H - 4));
        } else {
            panelX = (this.width - PANEL_W) / 2;
            panelY = (this.height - PANEL_H) / 2;
        }
        loadFields();
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        drawFlatDimBackground(matrices);
        drawPanel(matrices, mouseX, mouseY);
        super.render(matrices, mouseX, mouseY, delta);
    }


    @Override
    public void blur() {
        // 1.21.x applies framebuffer menu blur through Screen#blur().
        // Malfix uses its own flat dim layer, so vanilla blur must stay disabled.
    }

    @Override
    protected void applyBlur() {
        // Disable vanilla menu blur for this screen.
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        drawFlatDimBackground(context);
    }

    @Override
    public void renderInGameBackground(DrawContext context) {
        drawFlatDimBackground(context);
    }

    @Override
    protected void renderDarkening(DrawContext context) {
        // No vanilla darkening/blur path.
    }

    @Override
    protected void renderDarkening(DrawContext context, int x, int y, int width, int height) {
        // No vanilla darkening/blur path.
    }

    private void drawFlatDimBackground(DrawContext matrices) {
        matrices.fill(0, 0, this.width, this.height, 0x66000000);
    }

    private void drawPanel(DrawContext matrices, int mouseX, int mouseY) {
        matrices.fill(panelX - 8, panelY - 8, panelX + PANEL_W + 8, panelY + PANEL_H + 8, 0x52000000);
        matrices.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xE6121631);
        matrices.fill(panelX + 8, panelY + 8, panelX + PANEL_W - 8, panelY + PANEL_H - 8, 0x8A181D3F);
        drawBorder(matrices, panelX, panelY, PANEL_W, PANEL_H, 0xA93B46A3);
        drawText(matrices, "⚛", panelX + 18, panelY + 16, 0x8A7D89FF);
        drawText(matrices, "⚛", panelX + PANEL_W - 27, panelY + 16, 0x8A7D89FF);
        matrices.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Настройка парсера"), panelX + PANEL_W / 2, panelY + 16, 0xFFE8EAFF);

        int x = panelX + 22;
        int y = panelY + 43;
        int labelW = 96;
        int fieldX = x + labelW;
        int fieldW = 78;

        drawText(matrices, "Покупка %", x, y + 5, 0xFFE8EAFF);
        drawField(matrices, fieldX, y, fieldW, 18, buyPercentText, focusField == FocusField.BUY_PERCENT, "80");
        drawText(matrices, "от минималки", fieldX + fieldW + 8, y + 5, 0xFF9CA7FF);

        y += 26;
        drawText(matrices, "Продажа %", x, y + 5, 0xFFE8EAFF);
        drawField(matrices, fieldX, y, fieldW, 18, sellPercentText, focusField == FocusField.SELL_PERCENT, "90");
        drawText(matrices, "от минималки", fieldX + fieldW + 8, y + 5, 0xFF9CA7FF);

        y += 26;
        drawText(matrices, "Ожидание", x, y + 5, 0xFFE8EAFF);
        drawField(matrices, fieldX, y, fieldW, 18, openWaitText, focusField == FocusField.OPEN_WAIT, "650");
        drawText(matrices, "ms после /ah search", fieldX + fieldW + 8, y + 5, 0xFF9CA7FF);

        y += 34;
        drawButton(matrices, panelX + 24, y, 84, 21, "Парсить", mouseX, mouseY);
        drawButton(matrices, panelX + 122, y, 80, 21, "Сохранить", mouseX, mouseY);
        drawButton(matrices, panelX + 216, y, 80, 21, "Назад", mouseX, mouseY);

    }

    @Override
    public boolean mouseClicked(double mouseXRaw, double mouseYRaw, int button) {
        int mouseX = (int) mouseXRaw;
        int mouseY = (int) mouseYRaw;

        if (button == 0 && isDragArea(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            return true;
        }

        int x = panelX + 22;
        int y = panelY + 43;
        int fieldX = x + 96;

        if (inside(mouseX, mouseY, fieldX, y, 78, 18)) {
            focusField = FocusField.BUY_PERCENT;
            replaceFieldOnNextTyping = true;
            return true;
        }

        if (inside(mouseX, mouseY, fieldX, y + 26, 78, 18)) {
            focusField = FocusField.SELL_PERCENT;
            replaceFieldOnNextTyping = true;
            return true;
        }

        if (inside(mouseX, mouseY, fieldX, y + 52, 78, 18)) {
            focusField = FocusField.OPEN_WAIT;
            replaceFieldOnNextTyping = true;
            return true;
        }

        int buttonY = y + 86;
        if (inside(mouseX, mouseY, panelX + 24, buttonY, 84, 21)) {
            saveFields();
            closeScreenOnly();
            runtime.startAutoParserForAll();
            return true;
        }

        if (inside(mouseX, mouseY, panelX + 122, buttonY, 80, 21)) {
            saveFields();
            return true;
        }

        if (inside(mouseX, mouseY, panelX + 216, buttonY, 80, 21)) {
            closeToParent();
            return true;
        }

        focusField = FocusField.NONE;
        return super.mouseClicked(mouseXRaw, mouseYRaw, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 0) {
            panelX = clamp((int) mouseX - dragOffsetX, 4, Math.max(4, this.width - PANEL_W - 4));
            panelY = clamp((int) mouseY - dragOffsetY, 4, Math.max(4, this.height - PANEL_H - 4));
            rememberState();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private boolean isDragArea(int mouseX, int mouseY) {
        return inside(mouseX, mouseY, panelX + 10, panelY + 8, PANEL_W - 20, 32);
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focusField == FocusField.NONE) {
            return super.charTyped(chr, modifiers);
        }

        if (Character.isDigit(chr)) {
            if (replaceFieldOnNextTyping) {
                setActiveFieldValue("");
                replaceFieldOnNextTyping = false;
            }

            if (getActiveFieldValue().length() < 5) {
                setActiveFieldValue(getActiveFieldValue() + chr);
                saveFields();
            }
            return true;
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeToParent();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            saveFields();
            focusField = FocusField.NONE;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && focusField != FocusField.NONE) {
            String current = getActiveFieldValue();
            if (replaceFieldOnNextTyping) {
                setActiveFieldValue("");
                replaceFieldOnNextTyping = false;
            } else if (!current.isEmpty()) {
                setActiveFieldValue(current.substring(0, current.length() - 1));
            }
            saveFields();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE && focusField != FocusField.NONE) {
            setActiveFieldValue("");
            replaceFieldOnNextTyping = false;
            saveFields();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void loadFields() {
        if (runtime == null || runtime.getConfig() == null) {
            return;
        }

        buyPercentText = String.valueOf(runtime.getConfig().getParserBuyPercent());
        sellPercentText = String.valueOf(runtime.getConfig().getParserSellPercent());
        openWaitText = String.valueOf(runtime.getConfig().getParserOpenWaitMs());
    }

    private void saveFields() {
        if (runtime == null || runtime.getConfig() == null) {
            return;
        }

        runtime.getConfig().setParserBuyPercent(parseInt(buyPercentText, runtime.getConfig().getParserBuyPercent()));
        runtime.getConfig().setParserSellPercent(parseInt(sellPercentText, runtime.getConfig().getParserSellPercent()));
        runtime.getConfig().setParserOpenWaitMs(parseLong(openWaitText, runtime.getConfig().getParserOpenWaitMs()));
        runtime.saveConfig();
    }

    private int parseInt(String raw, int fallback) {
        try {
            if (raw == null || raw.trim().isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(raw.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private long parseLong(String raw, long fallback) {
        try {
            if (raw == null || raw.trim().isEmpty()) {
                return fallback;
            }
            return Long.parseLong(raw.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String getActiveFieldValue() {
        if (focusField == FocusField.BUY_PERCENT) {
            return buyPercentText;
        }
        if (focusField == FocusField.SELL_PERCENT) {
            return sellPercentText;
        }
        if (focusField == FocusField.OPEN_WAIT) {
            return openWaitText;
        }
        return "";
    }

    private void setActiveFieldValue(String value) {
        if (focusField == FocusField.BUY_PERCENT) {
            buyPercentText = value == null ? "" : value;
        } else if (focusField == FocusField.SELL_PERCENT) {
            sellPercentText = value == null ? "" : value;
        } else if (focusField == FocusField.OPEN_WAIT) {
            openWaitText = value == null ? "" : value;
        }
    }

    private void rememberState() {
        rememberedPanelX = panelX;
        rememberedPanelY = panelY;
    }

    private void closeScreenOnly() {
        rememberState();
        saveFields();
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    private void closeToParent() {
        rememberState();
        saveFields();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void close() {
        closeToParent();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawField(DrawContext matrices, int x, int y, int w, int h, String value, boolean focused, String placeholder) {
        matrices.fill(x, y, x + w, y + h, focused ? 0xE6181F44 : 0xC4121730);
        drawBorder(matrices, x, y, w, h, focused ? 0xFF5865FF : 0x353B46A3);
        String text = value == null || value.isEmpty() ? placeholder : value;
        int color = value == null || value.isEmpty() ? 0xFF7E869C : 0xFFF0F3FC;
        drawText(matrices, text, x + 6, y + 5, color);
        if (focused && (System.currentTimeMillis() / 450L) % 2L == 0L) {
            int caretX = x + 6 + this.textRenderer.getWidth(text);
            matrices.fill(caretX, y + 4, caretX + 1, y + h - 4, 0xFFFFFFFF);
        }
    }

    private void drawButton(DrawContext matrices, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        matrices.fill(x, y, x + w, y + h, hovered ? 0xA31B214B : 0x7A121938);
        drawBorder(matrices, x, y, w, h, hovered ? 0xFF95A7FF : 0x3A6C79D0);
        int tx = x + (w - this.textRenderer.getWidth(label)) / 2;
        drawText(matrices, label, tx, y + 7, 0xFFE8EAFF);
    }

    private void drawBorder(DrawContext matrices, int x, int y, int w, int h, int color) {
        matrices.fill(x, y, x + w, y + 1, color);
        matrices.fill(x, y + h - 1, x + w, y + h, color);
        matrices.fill(x, y, x + 1, y + h, color);
        matrices.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawText(DrawContext matrices, String text, int x, int y, int color) {
        matrices.drawText(this.textRenderer, text, x, y, color, false);
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }
}
