package ru.malfix.autobuy.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.lwjgl.glfw.GLFW;
import ru.malfix.autobuy.client.MalfixClientRuntime;
import ru.malfix.autobuy.config.TargetConfig;

import java.util.List;
import java.util.Base64;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class TargetsConfigScreen extends Screen {

    private enum FocusField {
        NONE,
        BUY_PRICE,
        SELL_PRICE,
        UNSTACK_AMOUNT,
        POTION_MIN_SOURCE
    }

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 342;
    private static final int HEADER_H = 30;
    private static final int ROW_H = 24;
    private static final int EXPANDED_H = 142;
    private static final int ROW_GAP = 5;

    private static int rememberedPanelX = Integer.MIN_VALUE;
    private static int rememberedPanelY = Integer.MIN_VALUE;
    private static int rememberedScrollPixels = 0;
    private static int rememberedSelectedIndex = -1;

    private final MalfixClientRuntime runtime;
    private final Screen parent;

    private int panelX;
    private int panelY;

    private int scrollPixels = 0;
    private int selectedIndex = -1;

    private String buyPriceText = "";
    private String sellPriceText = "";
    private String unstackAmountText = "1";
    private String potionMinSourceText = "24";

    private FocusField focusField = FocusField.NONE;
    private boolean replaceFieldOnNextTyping = false;
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public TargetsConfigScreen(MalfixClientRuntime runtime, Screen parent) {
        super(Text.literal("MalfixAutoBuy — Предметы"));
        this.runtime = runtime;
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (rememberedPanelX != Integer.MIN_VALUE && rememberedPanelY != Integer.MIN_VALUE) {
            panelX = clamp(rememberedPanelX, 4, Math.max(4, this.width - PANEL_W - 4));
            panelY = clamp(rememberedPanelY, 4, Math.max(4, this.height - PANEL_H - 4));
            scrollPixels = Math.max(0, rememberedScrollPixels);
            selectedIndex = rememberedSelectedIndex;
        } else {
            panelX = (this.width - PANEL_W) / 2;
            panelY = (this.height - PANEL_H) / 2;
        }

        int targetCount = runtime == null || runtime.getConfig() == null || runtime.getConfig().getTargets() == null
                ? 0
                : runtime.getConfig().getTargets().size();
        if (selectedIndex >= targetCount) {
            selectedIndex = -1;
        }
        clampScroll();
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        drawFlatDimBackground(matrices);
        drawMockupBase(matrices, mouseX, mouseY);
        drawTargetRows(matrices, mouseX, mouseY);
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

    private void drawMockupBase(DrawContext matrices, int mouseX, int mouseY) {
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
        matrices.drawCenteredTextWithShadow(this.textRenderer, Text.literal("MalfixAutoBuy"), panelX + PANEL_W / 2 - 4, titleY, 0xFFE8EAFF);


        drawHeaderButton(matrices, panelX + PANEL_W - 132, panelY + 15, 56, 15, "Парсер", mouseX, mouseY);
        drawHeaderButton(matrices, panelX + PANEL_W - 72, panelY + 15, 50, 15, "Бинды", mouseX, mouseY);
    }

    private void drawTargetRows(DrawContext matrices, int mouseX, int mouseY) {
        List<TargetConfig> targets = runtime.getConfig().getTargets();
        int x = panelX + 25;
        int top = getListTop();
        int width = PANEL_W - 50;
        int bottom = getListBottom();

        clampScroll();

        enableListScissor(x - 4, top - 2, width + 8, bottom - top + 4);

        int y = top - scrollPixels;
        for (int index = 0; index < targets.size(); index++) {
            TargetConfig target = targets.get(index);
            boolean expanded = index == selectedIndex;
            int blockH = getBlockHeight(index);

            if (y + blockH >= top && y <= bottom) {
                drawTargetRow(matrices, target, x, y, width, expanded, mouseX, mouseY);
            }

            y += blockH + ROW_GAP;
        }

        disableListScissor();
        drawScrollbar(matrices, getContentHeight(), top, bottom - top);
    }

    private void drawTargetRow(DrawContext matrices, TargetConfig target, int x, int y, int w, boolean expanded, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, ROW_H);
        int rowBg = expanded ? 0xB4141A38 : (hovered ? 0x8F12182F : 0x73161B3D);
        int rowBorder = expanded ? 0xFF5865FF : 0x2A303B96;

        drawRoundedRect(matrices, x, y, w, ROW_H, 5, rowBg);
        drawRoundedBorder(matrices, x, y, w, ROW_H, 5, rowBorder);
        renderItemIcon(matrices, target, x + 8, y + 4);
        drawText(matrices, shortText(target.getLabel(), 23), x + 28, y + 8, target.isEnabled() ? 0xFFE8EAFF : 0xFFC4C9F0);

        drawAhTag(matrices, x + w - 66, y + 5, 30, 15, mouseX, mouseY);
        drawText(matrices, expanded ? "∨" : "›", x + w - 20, y + 7, expanded ? 0xFF8E98FF : 0xFFA8B0E0);
        drawRect(matrices, x, y, 2, ROW_H, target.isEnabled() ? 0xFF63FF76 : 0xFF555D71);

        if (expanded) {
            drawExpandedBlock(matrices, target, x + 10, y + ROW_H + 4, w - 20, mouseX, mouseY);
        }
    }

    private void drawExpandedBlock(DrawContext matrices, TargetConfig target, int x, int y, int w, int mouseX, int mouseY) {
        drawRoundedRect(matrices, x, y, w, EXPANDED_H - 5, 4, 0x76151A35);
        drawRoundedBorder(matrices, x, y, w, EXPANDED_H - 5, 4, 0x2A303B96);

        int iconX = x + 8;
        int fieldX = x + 33;
        int fieldW = w - 45;

        int buyY = y + 7;
        int sellY = y + 28;
        int parseY = y + 49;
        int unstackY = y + 70;
        int countY = y + 91;
        int potionMinY = y + 112;

        drawSmallIcon(matrices, iconX, buyY, 0xFFFFBB3E, "$");
        drawValueField(matrices, fieldX, buyY, fieldW, 16, buyPriceText, focusField == FocusField.BUY_PRICE, 0xFFFFBB3E, "0");

        drawSmallIcon(matrices, iconX, sellY, 0xFF63EF6D, "$");
        drawValueField(matrices, fieldX, sellY, fieldW, 16, sellPriceText, focusField == FocusField.SELL_PRICE, 0xFF63EF6D, "0");

        drawSmallIcon(matrices, iconX, parseY, 0xFF80FF9D, "P");
        drawText(matrices, "Парсить", fieldX, parseY + 4, 0xFFE8EAFF);
        drawToggle(matrices, x + w - 28, parseY + 2, 18, 9, target.isParserEnabled(), mouseX, mouseY);

        drawSmallIcon(matrices, iconX, unstackY, 0xFFB7BED1, "□");
        drawText(matrices, "Расстакивать", fieldX, unstackY + 4, 0xFFE8EAFF);
        drawToggle(matrices, x + w - 28, unstackY + 2, 18, 9, target.isUnstack(), mouseX, mouseY);

        drawSmallIcon(matrices, iconX, countY, 0xFFB7BED1, "#");
        drawValueField(matrices, fieldX, countY, fieldW, 16, unstackAmountText, focusField == FocusField.UNSTACK_AMOUNT, 0xFF9CA7FF, isPotionDragGuiTarget(target) ? "слоты" : "1");

        drawSmallIcon(matrices, iconX, potionMinY, 0xFFB7BED1, "M");
        if (isPotionDragGuiTarget(target)) {
            int labelW = Math.min(74, fieldW / 2);
            drawText(matrices, "Мин. стак", fieldX, potionMinY + 4, 0xFFC8CEE8);
            drawValueField(matrices, fieldX + labelW, potionMinY, fieldW - labelW, 16, potionMinSourceText, focusField == FocusField.POTION_MIN_SOURCE, 0xFF9CA7FF, "24");
        } else {
            drawText(matrices, "Мин. стак: только drag-зелья", fieldX, potionMinY + 4, 0xFF7E869C);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!inside((int) mouseX, (int) mouseY, panelX, panelY, PANEL_W, PANEL_H)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int step = 32;
        if (verticalAmount > 0.0D) {
            scrollPixels -= step;
        } else if (verticalAmount < 0.0D) {
            scrollPixels += step;
        }

        clampScroll();
        rememberState();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseXRaw, double mouseYRaw, int button) {
        int mouseX = (int) mouseXRaw;
        int mouseY = (int) mouseYRaw;


        if (inside(mouseX, mouseY, panelX + PANEL_W - 132, panelY + 15, 56, 15)) {
            rememberState();
            runtime.openParserGui(this);
            return true;
        }

        if (inside(mouseX, mouseY, panelX + PANEL_W - 72, panelY + 15, 50, 15)) {
            rememberState();
            runtime.openKeybindGui(this);
            return true;
        }

        if (button == 0 && isDragArea(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            return true;
        }

        int top = getListTop();
        int bottom = getListBottom();
        if (!inside(mouseX, mouseY, panelX + 18, top, PANEL_W - 36, bottom - top)) {
            focusField = FocusField.NONE;
            return super.mouseClicked(mouseXRaw, mouseYRaw, button);
        }

        List<TargetConfig> targets = runtime.getConfig().getTargets();
        int x = panelX + 25;
        int y = top - scrollPixels;
        int width = PANEL_W - 50;

        for (int index = 0; index < targets.size(); index++) {
            TargetConfig target = targets.get(index);
            boolean expanded = index == selectedIndex;
            int blockH = getBlockHeight(index);

            if (y + blockH < top) {
                y += blockH + ROW_GAP;
                continue;
            }

            if (y > bottom) {
                break;
            }

            if (inside(mouseX, mouseY, x + width - 66, y + 5, 30, 15)) {
                rememberState();
                saveAndApply();
                runtime.openAuctionSearchForTarget(target);
                return true;
            }

            if (inside(mouseX, mouseY, x, y, width, ROW_H)) {
                if (selectedIndex == index) {
                    selectedIndex = -1;
                    focusField = FocusField.NONE;
                } else {
                    selectedIndex = index;
                    loadSelectedFields();
                    focusField = FocusField.NONE;
                    ensureSelectedVisible();
                }
                clampScroll();
                rememberState();
                return true;
            }

            if (expanded) {
                int innerX = x + 10;
                int innerY = y + ROW_H + 4;
                int innerW = width - 20;
                int fieldX = innerX + 33;
                int fieldW = innerW - 45;

                if (inside(mouseX, mouseY, fieldX, innerY + 7, fieldW, 17)) {
                    focusField = FocusField.BUY_PRICE;
                    replaceFieldOnNextTyping = true;
                    return true;
                }

                if (inside(mouseX, mouseY, fieldX, innerY + 29, fieldW, 17)) {
                    focusField = FocusField.SELL_PRICE;
                    replaceFieldOnNextTyping = true;
                    return true;
                }

                if (inside(mouseX, mouseY, innerX + innerW - 29, innerY + 51, 18, 9)) {
                    target.setParserEnabled(!target.isParserEnabled());
                    loadSelectedFields();
                    saveAndApply();
                    return true;
                }

                if (inside(mouseX, mouseY, innerX + innerW - 29, innerY + 72, 18, 9)) {
                    target.setUnstack(!target.isUnstack());
                    if (target.isUnstack() && target.getUnstackAmount() <= 0) {
                        target.setUnstackAmount(1);
                    }
                    loadSelectedFields();
                    saveAndApply();
                    return true;
                }

                if (inside(mouseX, mouseY, fieldX, innerY + 94, fieldW, 17)) {
                    focusField = FocusField.UNSTACK_AMOUNT;
                    replaceFieldOnNextTyping = true;
                    return true;
                }

                if (isPotionDragGuiTarget(target)) {
                    int labelW = Math.min(74, fieldW / 2);
                    if (inside(mouseX, mouseY, fieldX + labelW, innerY + 115, fieldW - labelW, 17)) {
                        focusField = FocusField.POTION_MIN_SOURCE;
                        replaceFieldOnNextTyping = true;
                        return true;
                    }
                }
            }

            y += blockH + ROW_GAP;
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
        return inside(mouseX, mouseY, panelX + 12, panelY + 8, PANEL_W - 206, 32);
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

        if (Character.isDigit(chr) || chr == ',' || chr == ' ' || chr == '_') {
            if (replaceFieldOnNextTyping) {
                setActiveFieldValue("");
                replaceFieldOnNextTyping = false;
            }

            if (getActiveFieldValue().length() < 18) {
                setActiveFieldValue(getActiveFieldValue() + chr);
                applySelectedField();
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

        if (keyCode == GLFW.GLFW_KEY_UP) {
            scrollPixels -= 32;
            clampScroll();
            rememberState();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            scrollPixels += 32;
            clampScroll();
            rememberState();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applySelectedField();
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

            applySelectedField();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE && focusField != FocusField.NONE) {
            setActiveFieldValue("");
            replaceFieldOnNextTyping = false;
            applySelectedField();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void adjustUnstackAmount(int delta) {
        TargetConfig target = getSelectedTarget();
        if (target == null) {
            return;
        }

        int value = target.getUnstackAmount() + delta;
        if (value < 1) {
            value = 1;
        } else if (value > 64) {
            value = 64;
        }

        target.setUnstackAmount(value);
        unstackAmountText = String.valueOf(value);
        saveAndApply();
    }

    private void applySelectedField() {
        TargetConfig target = getSelectedTarget();
        if (target == null) {
            return;
        }

        if (focusField == FocusField.BUY_PRICE) {
            long value = parseMoney(buyPriceText);
            if (value >= 0L) {
                target.setMaxUnitPrice(value);
                if (value > 0L) {
                    target.setEnabled(true);
                }
                saveAndApply();
            }
            return;
        }

        if (focusField == FocusField.SELL_PRICE) {
            long value = parseMoney(sellPriceText);
            if (value >= 0L) {
                target.setSellUnitPrice(value);
                saveAndApply();
            }
            return;
        }

        if (focusField == FocusField.UNSTACK_AMOUNT) {
            int value = parseCount(unstackAmountText);
            if (value > 0) {
                target.setUnstackAmount(value);
                saveAndApply();
            }
            return;
        }

        if (focusField == FocusField.POTION_MIN_SOURCE) {
            int value = parseCount(potionMinSourceText);
            if (value > 0) {
                target.setPotionDragMinSourceCount(value);
                saveAndApply();
            }
        }
    }

    private void loadSelectedFields() {
        TargetConfig target = getSelectedTarget();

        if (target == null) {
            buyPriceText = "";
            sellPriceText = "";
            unstackAmountText = "1";
            potionMinSourceText = "24";
            return;
        }

        buyPriceText = target.getMaxUnitPrice() <= 0L ? "" : String.valueOf(target.getMaxUnitPrice());
        sellPriceText = target.getSellUnitPrice() <= 0L ? "" : String.valueOf(target.getSellUnitPrice());
        unstackAmountText = String.valueOf(Math.max(1, target.getUnstackAmount()));
        potionMinSourceText = String.valueOf(Math.max(1, target.getPotionDragMinSourceCount()));
    }

    private TargetConfig getSelectedTarget() {
        List<TargetConfig> targets = runtime.getConfig().getTargets();

        if (selectedIndex < 0 || selectedIndex >= targets.size()) {
            return null;
        }

        return targets.get(selectedIndex);
    }

    private void saveAndApply() {
        runtime.applyConfigToRuntime();
        runtime.saveConfig();
    }

    private void clampScroll() {
        int max = getMaxScrollPixels();

        if (scrollPixels < 0) {
            scrollPixels = 0;
        }

        if (scrollPixels > max) {
            scrollPixels = max;
        }
    }

    private int getListTop() {
        return panelY + HEADER_H + 18;
    }

    private int getListBottom() {
        return panelY + PANEL_H - 18;
    }

    private int getBlockHeight(int index) {
        return ROW_H + (index == selectedIndex ? EXPANDED_H : 0);
    }

    private int getContentHeight() {
        int total = runtime.getConfig().getTargets().size();
        if (total <= 0) {
            return 0;
        }

        int height = 0;
        for (int i = 0; i < total; i++) {
            height += getBlockHeight(i);
            if (i + 1 < total) {
                height += ROW_GAP;
            }
        }
        return height;
    }

    private int getMaxScrollPixels() {
        int viewportH = Math.max(0, getListBottom() - getListTop());
        return Math.max(0, getContentHeight() - viewportH);
    }

    private int getSelectedTopY() {
        if (selectedIndex < 0) {
            return 0;
        }

        int y = 0;
        int total = runtime.getConfig().getTargets().size();
        int limit = Math.min(selectedIndex, total);
        for (int i = 0; i < limit; i++) {
            y += getBlockHeight(i) + ROW_GAP;
        }
        return y;
    }

    private void ensureSelectedVisible() {
        if (selectedIndex < 0) {
            return;
        }

        int viewportH = Math.max(0, getListBottom() - getListTop());
        int selectedTop = getSelectedTopY();
        int selectedBottom = selectedTop + getBlockHeight(selectedIndex);

        if (selectedTop < scrollPixels) {
            scrollPixels = selectedTop;
        } else if (selectedBottom > scrollPixels + viewportH) {
            scrollPixels = selectedBottom - viewportH;
        }

        clampScroll();
    }

    private long parseMoney(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0L;
        }

        try {
            String normalized = raw.replace(",", "").replace(" ", "").replace("_", "").trim();
            if (normalized.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(normalized);
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private int parseCount(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 1;
        }

        try {
            String normalized = raw.replace(",", "").replace(" ", "").replace("_", "").trim();
            int value = Integer.parseInt(normalized);

            if (value < 1) {
                return -1;
            }

            if (value > 64) {
                return 64;
            }

            return value;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private boolean isPotionDragGuiTarget(TargetConfig target) {
        if (target == null || !target.isUnstack()) {
            return false;
        }

        String itemId = target.getItemId() == null ? "" : target.getItemId().trim().toLowerCase(java.util.Locale.ROOT);
        String label = target.getLabel() == null ? "" : target.getLabel().toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        String tag = target.getTagContains() == null ? "" : target.getTagContains().toLowerCase(java.util.Locale.ROOT);

        return "minecraft:potion".equals(itemId)
                && (label.contains("несоздаваемое зелье")
                || tag.contains("effect:speed:3600:2")
                || tag.contains("effect:strength:3600:2"));
    }

    private void renderItemIcon(DrawContext matrices, TargetConfig target, int x, int y) {
        if (this.client == null || target == null) {
            return;
        }

        try {
            Item item = Items.PAPER;
            String itemId = resolveIconItemId(target);

            if (itemId != null && !itemId.trim().isEmpty()) {
                item = Registries.ITEM.get(Identifier.of(itemId));
            }

            if (item == null || item == Items.AIR) {
                item = Items.PAPER;
            }

            ItemStack stack = new ItemStack(item);
            applySyntheticIconNbt(target, itemId, stack);
            matrices.drawItem(stack, x, y);
        } catch (Throwable ignored) {
        }
    }

    private String resolveIconItemId(TargetConfig target) {
        if (target == null) {
            return "minecraft:paper";
        }

        String itemId = target.getItemId();

        String text = ((target.getLabel() == null ? "" : target.getLabel()) + " "
                + (target.getTagContains() == null ? "" : target.getTagContains()))
                .toLowerCase(java.util.Locale.ROOT)
                .replace('ё', 'е');

        String inferred = inferIconItemIdFromTargetText(text);
        if (itemId != null && !itemId.trim().isEmpty()) {
            String safeItemId = itemId.trim();
            // Older configs could store paper as a placeholder for custom/script targets.
            // Do not let that placeholder block a better GUI icon inferred from label/tag.
            if (("minecraft:paper".equalsIgnoreCase(safeItemId) || "paper".equalsIgnoreCase(safeItemId))
                    && !"minecraft:paper".equals(inferred)) {
                return inferred;
            }
            return safeItemId;
        }

        return inferred;
    }

    private String inferIconItemIdFromTargetText(String text) {
        if (text == null) {
            return "minecraft:paper";
        }

        if (text.contains("серебро") || text.contains("spookystash:currency") || text.contains("silver")) {
            // Silver itself is still matched by NBT/tag in the scanner; this is only a GUI icon fallback.
            return "minecraft:white_dye";
        }
        if (text.contains("effect-item-god") || text.contains("божья аура")) {
            return "minecraft:phantom_membrane";
        }
        if (text.contains("spawner-item-spawner-break") || text.contains("божье касание")) {
            return "minecraft:golden_pickaxe";
        }
        if (text.contains("radius-item-mega-buldozer") || text.contains("молот тора")) {
            return "minecraft:netherite_pickaxe";
        }
        if (text.contains("schematic-item-trap") || text.contains("трапка")) {
            return "minecraft:netherite_scrap";
        }
        if (text.contains("отмычка к сферам") || text.contains("spheres")) {
            return "minecraft:tripwire_hook";
        }
        if (text.contains("спавнер")) {
            return "minecraft:spawner";
        }
        if (text.contains("custompotioncolor") || text.contains("зелье") || text.contains("хлопушка") || text.contains("снотворное")) {
            return "minecraft:splash_potion";
        }
        if (text.contains("сфера") || text.contains("attribute-item-") || text.contains("sphere-item")) {
            return "minecraft:player_head";
        }
        if (text.contains("талисман") || text.contains("talisman")) {
            return "minecraft:totem_of_undying";
        }

        return "minecraft:paper";
    }

    private void applySyntheticIconNbt(TargetConfig target, String itemId, ItemStack stack) {
        if (target == null || stack == null || itemId == null) {
            return;
        }

        String id = itemId.trim().toLowerCase(java.util.Locale.ROOT);
        String tag = target.getTagContains() == null ? "" : target.getTagContains().toLowerCase(java.util.Locale.ROOT);
        String label = target.getLabel() == null ? "" : target.getLabel().toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');

        if ("minecraft:splash_potion".equals(id) || "minecraft:potion".equals(id)) {
            int color = parseIconPotionColor(tag);
            if (color < 0 && label.contains("несоздаваемое зелье")) {
                color = 0x7CAFC6;
            }
            // Potion icon color is cosmetic. In 1.21.4 this moved from raw NBT
            // to item components, so leave the vanilla splash-potion icon here.
            if (color >= 0) {
                // no-op
            }
        }

        if ("minecraft:player_head".equals(id)) {
            String textureHash = resolveSphereTextureHash(label, tag);
            if (!textureHash.isEmpty()) {
                applyPlayerHeadTexture(stack, textureHash);
            }
        }
    }

    private String resolveSphereTextureHash(String label, String tag) {
        String text = ((label == null ? "" : label) + " " + (tag == null ? "" : tag))
                .toLowerCase(java.util.Locale.ROOT)
                .replace('ё', 'е');

        if (text.contains("сфера хаоса")) {
            return "e7a7ae7cdcf616e8b7a4221a621b2435753c60ed6a258ea060dae3002ffe9e28";
        }
        if (text.contains("сфера сатира") || text.contains("сфера сатир") || text.contains("сфера статира")) {
            return "771a9a498b4fa5ec49362f9bc88eda4f52b04de49d75aa3ca332a1fea1aa0e57";
        }
        if (text.contains("сфера бестии") || text.contains("сфера бестий")) {
            return "5411ac17381b9fce9bab3c72afdb7f198570daf4732bd811d31c227d80fa39b1";
        }
        if (text.contains("сфера ареса")) {
            return "c16adc6bafcb57fd707dee7dd6a736fe126711d53a1fd6ce789da41b3be13f2a";
        }
        if (text.contains("сфера гидры")) {
            return "3e3c118d696d910e54de02ca4d807543f9b18c008c9838d2ff69377622fb1d32";
        }
        if (text.contains("сфера титана")) {
            return "81e9698458b7841c96ae4f24ec84ae01724100641c564e2a7b185f406e8ed23";
        }
        if (text.contains("сфера афины") || text.contains("сфера афина") || text.contains("attribute-item-safina")) {
            return "93f9eeda3ba23fe1423c4036e7dd0a74461dff96badc5b2f2b9faa7cc16f382f";
        }

        return "";
    }

    private void applyPlayerHeadTexture(ItemStack stack, String textureHash) {
        if (stack == null || textureHash == null || textureHash.trim().isEmpty()) {
            return;
        }

        try {
            String hash = textureHash.trim();
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
            String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)), "MalfixSphere");
            profile.getProperties().put("textures", new Property("textures", encoded));
            stack.set(DataComponentTypes.PROFILE, new ProfileComponent(profile));
        } catch (Throwable ignored) {
        }
    }

    private int parseIconPotionColor(String tag) {
        if (tag == null) {
            return -1;
        }
        String lower = tag.toLowerCase(java.util.Locale.ROOT);
        int idx = lower.indexOf("custompotioncolor:");
        if (idx < 0) {
            return -1;
        }

        int start = idx + "custompotioncolor:".length();
        StringBuilder digits = new StringBuilder();
        for (int i = start; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
                continue;
            }
            break;
        }

        if (digits.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String getActiveFieldValue() {
        if (focusField == FocusField.BUY_PRICE) {
            return buyPriceText;
        }

        if (focusField == FocusField.SELL_PRICE) {
            return sellPriceText;
        }

        if (focusField == FocusField.UNSTACK_AMOUNT) {
            return unstackAmountText;
        }

        if (focusField == FocusField.POTION_MIN_SOURCE) {
            return potionMinSourceText;
        }

        return "";
    }

    private void setActiveFieldValue(String value) {
        if (focusField == FocusField.BUY_PRICE) {
            buyPriceText = value == null ? "" : value;
        } else if (focusField == FocusField.SELL_PRICE) {
            sellPriceText = value == null ? "" : value;
        } else if (focusField == FocusField.UNSTACK_AMOUNT) {
            unstackAmountText = value == null ? "" : value;
        } else if (focusField == FocusField.POTION_MIN_SOURCE) {
            potionMinSourceText = value == null ? "" : value;
        }
    }

    private void rememberState() {
        rememberedPanelX = panelX;
        rememberedPanelY = panelY;
        rememberedScrollPixels = Math.max(0, scrollPixels);
        rememberedSelectedIndex = selectedIndex;
    }

    private void closeToParent() {
        rememberState();
        saveAndApply();
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

    private void drawValueField(DrawContext matrices, int x, int y, int w, int h, String value, boolean focused, int accentColor, String placeholder) {
        drawRoundedRect(matrices, x, y, w, h, 3, focused ? 0xE6181F44 : 0xC4121730);
        drawRoundedBorder(matrices, x, y, w, h, 3, focused ? accentColor : 0x353B46A3);

        String text = value == null || value.isEmpty() ? placeholder : value;
        int color = value == null || value.isEmpty() ? 0xFF7E869C : 0xFFF0F3FC;
        String shown = shortText(text, Math.max(3, w / 6));

        drawText(matrices, shown, x + 7, y + 5, color);

        if (focused && (System.currentTimeMillis() / 450L) % 2L == 0L) {
            int caretX = x + 7 + this.textRenderer.getWidth(shown);
            drawRect(matrices, caretX, y + 4, 1, h - 8, 0xFFFFFFFF);
        }
    }

    private void drawSmallIcon(DrawContext matrices, int x, int y, int color, String label) {
        drawRoundedRect(matrices, x, y, 14, 17, 3, 0x4010172F);
        drawRoundedBorder(matrices, x, y, 14, 17, 3, 0x353B46A3);
        int tx = x + (14 - this.textRenderer.getWidth(label)) / 2;
        drawText(matrices, label, tx, y + 5, color);
    }

    private void drawMiniAction(DrawContext matrices, int x, int y, int w, int h, String label, int color) {
        drawRoundedRect(matrices, x, y, w, h, 3, 0x2A11162D);
        drawRoundedBorder(matrices, x, y, w, h, 3, 0x353B46A3);
        int tx = x + (w - this.textRenderer.getWidth(label)) / 2;
        drawText(matrices, label, tx, y + 5, color);
    }

    private void drawStepper(DrawContext matrices, int x, int y, int w, int h, int color) {
        drawRoundedRect(matrices, x, y, w, h, 3, 0x2A11162D);
        drawRoundedBorder(matrices, x, y, w, h, 3, 0x353B46A3);
        drawRect(matrices, x, y + h / 2, w, 1, 0x2A5865FF);
        drawText(matrices, "˄", x + 4, y + 1, color);
        drawText(matrices, "˅", x + 4, y + 9, color);
    }

    private void drawAhTag(DrawContext matrices, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int bg = hovered ? 0xFF243A28 : 0xFF131B15;
        int border = hovered ? 0xFF77CC77 : 0xFF4D8D4D;
        drawRoundedRect(matrices, x, y, w, h, 3, bg);
        drawRoundedBorder(matrices, x, y, w, h, 3, border);
        int tx = x + (w - this.textRenderer.getWidth("Ah")) / 2;
        drawText(matrices, "Ah", tx, y + 4, hovered ? 0xFFA0FFA0 : 0xFF77FF77);
    }

    private void drawToggle(DrawContext matrices, int x, int y, int w, int h, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int bg = enabled ? 0xFF5865FF : (hovered ? 0xFF1B2230 : 0xFF111522);
        drawRoundedRect(matrices, x, y, w, h, 4, bg);
        drawRoundedBorder(matrices, x, y, w, h, 4, enabled ? 0xFF99A3FF : 0xFF3B46A3);
        int knobW = 7;
        int knobX = enabled ? x + w - knobW - 1 : x + 1;
        drawRoundedRect(matrices, knobX, y + 1, knobW, h - 2, 3, 0xFFF0F4FF);
        drawRoundedBorder(matrices, knobX, y + 1, knobW, h - 2, 3, 0x88A9B9D4);
    }

    private void drawHeaderButton(DrawContext matrices, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        drawRoundedRect(matrices, x, y, w, h, 4, hovered ? 0xA31B214B : 0x7A121938);
        drawRoundedBorder(matrices, x, y, w, h, 4, hovered ? 0xFF95A7FF : 0x3A6C79D0);
        int tx = x + (w - this.textRenderer.getWidth(label)) / 2;
        drawText(matrices, label, tx, y + 4, 0xFFE8EAFF);
    }

    private void drawScrollbar(DrawContext matrices, int contentHeight, int y, int h) {
        int x = panelX + PANEL_W - 12;
        drawRect(matrices, x, y, 2, h, 0x1E000000);

        if (contentHeight <= h || contentHeight <= 0) {
            drawRect(matrices, x, y, 2, h, 0x446A74CC);
            return;
        }

        int maxScroll = Math.max(1, contentHeight - h);
        int thumbH = Math.max(18, h * h / contentHeight);
        int thumbY = y + (h - thumbH) * scrollPixels / maxScroll;
        drawRect(matrices, x, thumbY, 2, thumbH, 0xFF7E8AFF);
    }

    private void enableListScissor(int x, int y, int w, int h) {
        if (this.client == null || w <= 0 || h <= 0) {
            return;
        }

        double scale = this.client.getWindow().getScaleFactor();
        int sx = (int) Math.floor(x * scale);
        int sy = (int) Math.floor((this.height - (y + h)) * scale);
        int sw = (int) Math.ceil(w * scale);
        int sh = (int) Math.ceil(h * scale);
        RenderSystem.enableScissor(sx, sy, sw, sh);
    }

    private void disableListScissor() {
        RenderSystem.disableScissor();
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

    private String shortText(String text, int max) {
        if (text == null) {
            return "";
        }

        if (text.length() <= max) {
            return text;
        }

        return text.substring(0, Math.max(0, max - 3)) + "...";
    }
}
