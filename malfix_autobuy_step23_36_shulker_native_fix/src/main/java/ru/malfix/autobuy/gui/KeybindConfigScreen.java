package ru.malfix.autobuy.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;
import ru.malfix.autobuy.client.MalfixClientRuntime;
import ru.malfix.autobuy.config.AutoBuyConfig;

public final class KeybindConfigScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 236;
    private static final int ROW_H = 28;
    private static final int ROW_GAP = 8;

    private static int rememberedPanelX = Integer.MIN_VALUE;
    private static int rememberedPanelY = Integer.MIN_VALUE;

    private static final Action[] ACTIONS = new Action[] {
            new Action("parser", "Парсер"),
            new Action("gui", "Открытие GUI"),
            new Action("fullAuto", "Включение автобая"),
            new Action("sellOnly", "Чисто продажа")
    };

    private final MalfixClientRuntime runtime;
    private final Screen parent;

    private int panelX;
    private int panelY;
    private int waitingIndex = -1;
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public KeybindConfigScreen(MalfixClientRuntime runtime, Screen parent) {
        super(Text.literal("MalfixAutoBuy — Бинды"));
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
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        drawFlatDimBackground(matrices);
        drawBase(matrices, mouseX, mouseY);
        drawRows(matrices, mouseX, mouseY);
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

    private void drawBase(DrawContext matrices, int mouseX, int mouseY) {
        drawRoundedRect(matrices, panelX - 10, panelY - 10, PANEL_W + 20, PANEL_H + 20, 8, 0x52000000);
        drawRoundedRect(matrices, panelX - 6, panelY - 6, PANEL_W + 12, PANEL_H + 12, 7, 0x22000000);
        drawRoundedRect(matrices, panelX, panelY, PANEL_W, PANEL_H, 7, 0xE6121631);
        drawRoundedBorder(matrices, panelX, panelY, PANEL_W, PANEL_H, 7, 0xA93B46A3);
        drawRoundedBorder(matrices, panelX + 1, panelY + 1, PANEL_W - 2, PANEL_H - 2, 6, 0x2A2A3478);

        drawRoundedRect(matrices, panelX + 10, panelY + 10, PANEL_W - 20, PANEL_H - 20, 6, 0x8A181D3F);
        drawRoundedBorder(matrices, panelX + 10, panelY + 10, PANEL_W - 20, PANEL_H - 20, 6, 0x2A36408E);

        int titleY = panelY + 16;
        drawRect(matrices, panelX + 30, titleY + 6, 68, 1, 0x2A4652B8);
        drawRect(matrices, panelX + PANEL_W - 98, titleY + 6, 42, 1, 0x2A4652B8);
        drawText(matrices, "⚛", panelX + 20, titleY + 1, 0x8A7D89FF);
        drawText(matrices, "⚛", panelX + PANEL_W - 28, titleY + 1, 0x8A7D89FF);
        matrices.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Бинды"), panelX + PANEL_W / 2 - 4, titleY, 0xFFE8EAFF);

    }

    private void drawRows(DrawContext matrices, int mouseX, int mouseY) {
        int x = panelX + 25;
        int y = panelY + 52;
        int w = PANEL_W - 50;

        for (int i = 0; i < ACTIONS.length; i++) {
            Action action = ACTIONS[i];
            int rowY = y + i * (ROW_H + ROW_GAP);
            boolean hovered = inside(mouseX, mouseY, x, rowY, w, ROW_H);
            boolean waiting = waitingIndex == i;

            int bg = waiting ? 0xC02A2448 : (hovered ? 0x8F12182F : 0x73161B3D);
            int border = waiting ? 0xFFFFD45A : (hovered ? 0xFF5865FF : 0x2A303B96);

            drawRoundedRect(matrices, x, rowY, w, ROW_H, 5, bg);
            drawRoundedBorder(matrices, x, rowY, w, ROW_H, 5, border);
            drawRect(matrices, x, rowY, 2, ROW_H, waiting ? 0xFFFFD45A : 0xFF5865FF);

            drawText(matrices, action.title, x + 10, rowY + 9, 0xFFE8EAFF);

            String key = getKeyName(runtime.getConfig().getKeyCode(action.id));
            int keyW = Math.max(46, this.textRenderer.getWidth(key) + 18);
            drawKeyPill(matrices, x + w - keyW - 8, rowY + 6, keyW, 16, key, waiting, mouseX, mouseY);
        }

        int bottom = panelY + PANEL_H - 34;
        drawButton(matrices, panelX + 25, bottom, 70, 20, "Сброс", mouseX, mouseY);
        drawButton(matrices, panelX + 103, bottom, 84, 20, "Сохранить", mouseX, mouseY);
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

        int x = panelX + 25;
        int y = panelY + 52;
        int w = PANEL_W - 50;

        for (int i = 0; i < ACTIONS.length; i++) {
            int rowY = y + i * (ROW_H + ROW_GAP);
            if (inside(mouseX, mouseY, x, rowY, w, ROW_H)) {
                waitingIndex = i;
                return true;
            }
        }

        int bottom = panelY + PANEL_H - 34;
        if (inside(mouseX, mouseY, panelX + 25, bottom, 70, 20)) {
            resetDefaults();
            runtime.saveConfig();
            return true;
        }

        if (inside(mouseX, mouseY, panelX + 103, bottom, 84, 20)) {
            runtime.saveConfig();
            return true;
        }


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
        return inside(mouseX, mouseY, panelX + 12, panelY + 8, PANEL_W - 24, 32);
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (waitingIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                waitingIndex = -1;
                return true;
            }


            Action action = ACTIONS[waitingIndex];
            runtime.getConfig().setKeyCode(action.id, keyCode);
            runtime.saveConfig();
            waitingIndex = -1;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeToParent();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }


    private void resetDefaults() {
        AutoBuyConfig config = runtime.getConfig();
        config.setKeyParser(GLFW.GLFW_KEY_P);
        config.setKeyGui(GLFW.GLFW_KEY_G);
        config.setKeyFullAuto(GLFW.GLFW_KEY_R);
        config.setKeySellOnly(GLFW.GLFW_KEY_V);
    }

    private String getKeyName(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) keyCode);
        }

        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) keyCode);
        }

        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
            return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE: return "SPACE";
            case GLFW.GLFW_KEY_ENTER: return "ENTER";
            case GLFW.GLFW_KEY_TAB: return "TAB";
            case GLFW.GLFW_KEY_BACKSPACE: return "BACKSPACE";
            case GLFW.GLFW_KEY_INSERT: return "INSERT";
            case GLFW.GLFW_KEY_DELETE: return "DELETE";
            case GLFW.GLFW_KEY_RIGHT: return "RIGHT";
            case GLFW.GLFW_KEY_LEFT: return "LEFT";
            case GLFW.GLFW_KEY_DOWN: return "DOWN";
            case GLFW.GLFW_KEY_UP: return "UP";
            case GLFW.GLFW_KEY_PAGE_UP: return "PAGE UP";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PAGE DOWN";
            case GLFW.GLFW_KEY_HOME: return "HOME";
            case GLFW.GLFW_KEY_END: return "END";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "CAPS";
            case GLFW.GLFW_KEY_SCROLL_LOCK: return "SCROLL";
            case GLFW.GLFW_KEY_NUM_LOCK: return "NUM";
            case GLFW.GLFW_KEY_PRINT_SCREEN: return "PRINT";
            case GLFW.GLFW_KEY_PAUSE: return "PAUSE";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "L-SHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "R-SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "L-CTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "R-CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT: return "L-ALT";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "R-ALT";
            case GLFW.GLFW_KEY_LEFT_SUPER: return "L-SUPER";
            case GLFW.GLFW_KEY_RIGHT_SUPER: return "R-SUPER";
            case GLFW.GLFW_KEY_MENU: return "MENU";
            case GLFW.GLFW_KEY_MINUS: return "-";
            case GLFW.GLFW_KEY_EQUAL: return "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET: return "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET: return "]";
            case GLFW.GLFW_KEY_BACKSLASH: return "\\";
            case GLFW.GLFW_KEY_SEMICOLON: return ";";
            case GLFW.GLFW_KEY_APOSTROPHE: return "'";
            case GLFW.GLFW_KEY_GRAVE_ACCENT: return "`";
            case GLFW.GLFW_KEY_COMMA: return ",";
            case GLFW.GLFW_KEY_PERIOD: return ".";
            case GLFW.GLFW_KEY_SLASH: return "/";
            case GLFW.GLFW_KEY_KP_0: return "NUM0";
            case GLFW.GLFW_KEY_KP_1: return "NUM1";
            case GLFW.GLFW_KEY_KP_2: return "NUM2";
            case GLFW.GLFW_KEY_KP_3: return "NUM3";
            case GLFW.GLFW_KEY_KP_4: return "NUM4";
            case GLFW.GLFW_KEY_KP_5: return "NUM5";
            case GLFW.GLFW_KEY_KP_6: return "NUM6";
            case GLFW.GLFW_KEY_KP_7: return "NUM7";
            case GLFW.GLFW_KEY_KP_8: return "NUM8";
            case GLFW.GLFW_KEY_KP_9: return "NUM9";
            case GLFW.GLFW_KEY_KP_DECIMAL: return "NUM.";
            case GLFW.GLFW_KEY_KP_DIVIDE: return "NUM/";
            case GLFW.GLFW_KEY_KP_MULTIPLY: return "NUM*";
            case GLFW.GLFW_KEY_KP_SUBTRACT: return "NUM-";
            case GLFW.GLFW_KEY_KP_ADD: return "NUM+";
            case GLFW.GLFW_KEY_KP_ENTER: return "NUM ENTER";
            case GLFW.GLFW_KEY_KP_EQUAL: return "NUM=";
            default: break;
        }

        try {
            String translated = InputUtil.fromKeyCode(keyCode, 0).getLocalizedText().getString();
            if (translated != null && !translated.trim().isEmpty()) {
                return translated.toUpperCase();
            }
        } catch (Throwable ignored) {
        }

        return "KEY_" + keyCode;
    }

    private void rememberState() {
        rememberedPanelX = panelX;
        rememberedPanelY = panelY;
    }

    private void closeToParent() {
        rememberState();
        runtime.saveConfig();
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

    private void drawKeyPill(DrawContext matrices, int x, int y, int w, int h, String label, boolean active, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        drawRoundedRect(matrices, x, y, w, h, 4, active ? 0xFF3B315B : (hovered ? 0xA31B214B : 0x7A121938));
        drawRoundedBorder(matrices, x, y, w, h, 4, active ? 0xFFFFD45A : (hovered ? 0xFF95A7FF : 0x3A6C79D0));
        int tx = x + (w - this.textRenderer.getWidth(label)) / 2;
        drawText(matrices, label, tx, y + 4, active ? 0xFFFFD45A : 0xFFE8EAFF);
    }

    private void drawButton(DrawContext matrices, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        drawRoundedRect(matrices, x, y, w, h, 4, hovered ? 0xA31B214B : 0x7A121938);
        drawRoundedBorder(matrices, x, y, w, h, 4, hovered ? 0xFF95A7FF : 0x3A6C79D0);
        int tx = x + (w - this.textRenderer.getWidth(label)) / 2;
        drawText(matrices, label, tx, y + 6, 0xFFE8EAFF);
    }

    private void drawRoundedRect(DrawContext matrices, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0 || w <= 2 || h <= 2) {
            drawRect(matrices, x, y, w, h, color);
            return;
        }

        int r = Math.min(Math.min(radius, w / 2), h / 2);
        matrices.fill(x + r, y, x + w - r, y + h, color);
        matrices.fill(x, y + r, x + w, y + h - r, color);

        if (r >= 2) {
            matrices.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        }

        if (r >= 4) {
            matrices.fill(x + 2, y + 1, x + w - 2, y + h - 1, color);
            matrices.fill(x + 1, y + 2, x + w - 1, y + h - 2, color);
        }
    }

    private void drawRoundedBorder(DrawContext matrices, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0 || w <= 2 || h <= 2) {
            drawBorder(matrices, x, y, w, h, color);
            return;
        }

        int r = Math.min(Math.min(radius, w / 2), h / 2);
        matrices.fill(x + r, y, x + w - r, y + 1, color);
        matrices.fill(x + r, y + h - 1, x + w - r, y + h, color);
        matrices.fill(x, y + r, x + 1, y + h - r, color);
        matrices.fill(x + w - 1, y + r, x + w, y + h - r, color);

        matrices.fill(x + 1, y + 1, x + 2, y + 2, color);
        matrices.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
        matrices.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
        matrices.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);

        if (r >= 4) {
            matrices.fill(x + 2, y, x + 3, y + 1, color);
            matrices.fill(x + w - 3, y, x + w - 2, y + 1, color);
            matrices.fill(x + 2, y + h - 1, x + 3, y + h, color);
            matrices.fill(x + w - 3, y + h - 1, x + w - 2, y + h, color);
            matrices.fill(x, y + 2, x + 1, y + 3, color);
            matrices.fill(x + w - 1, y + 2, x + w, y + 3, color);
            matrices.fill(x, y + h - 3, x + 1, y + h - 2, color);
            matrices.fill(x + w - 1, y + h - 3, x + w, y + h - 2, color);
        }
    }

    private void drawRect(DrawContext matrices, int x, int y, int w, int h, int color) {
        matrices.fill(x, y, x + w, y + h, color);
    }

    private void drawBorder(DrawContext matrices, int x, int y, int w, int h, int color) {
        matrices.fill(x, y, x + w, y + 1, color);
        matrices.fill(x, y + h - 1, x + w, y + h, color);
        matrices.fill(x, y, x + 1, y + h, color);
        matrices.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawText(DrawContext matrices, String text, int x, int y, int color) {
        matrices.drawTextWithShadow(this.textRenderer, text == null ? "" : text, x, y, color);
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    private static final class Action {
        private final String id;
        private final String title;

        private Action(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }
}
