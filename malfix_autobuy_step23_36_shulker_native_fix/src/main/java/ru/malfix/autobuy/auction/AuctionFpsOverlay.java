package ru.malfix.autobuy.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;
import ru.malfix.autobuy.profiler.MalfixProfiler;

/** Lightweight FPS marker above auction/search windows only. */
public final class AuctionFpsOverlay {
    private static long fpsWindowStartedAtMs = 0L;
    private static int framesInWindow = 0;
    private static int displayedFps = 0;

    private AuctionFpsOverlay() {
    }

    public static void render(DrawContext matrices, int screenLeft, int screenTop, int backgroundWidth) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null || !isAuctionLikeScreen(client.currentScreen)) {
            return;
        }

        int fps = updateAndGetFps();
        String text = "FPS: " + fps;
        int width = client.textRenderer.getWidth(text);

        int x = screenLeft + Math.max(0, backgroundWidth - width - 4);
        int y = Math.max(2, screenTop - 12);

        // Transparent background: show only text above the auction window.
        matrices.drawTextWithShadow(client.textRenderer, text, x, y, 0xFFFFFFFF);

        if (!MalfixProfiler.isOverlayEnabled()) {
            return;
        }

        int px = screenLeft + backgroundWidth + 6;
        int py = Math.max(2, screenTop + 2);
        drawLine(matrices, client, MalfixProfiler.overlayLine1(), px, py);
        drawLine(matrices, client, MalfixProfiler.overlayLine2(), px, py + 10);
        drawLine(matrices, client, MalfixProfiler.overlayLine3(), px, py + 20);
        drawLine(matrices, client, MalfixProfiler.overlayLine4(), px, py + 30);
    }

    private static void drawLine(DrawContext matrices, MinecraftClient client, String text, int x, int y) {
        if (text == null || text.isEmpty() || client == null || client.textRenderer == null) {
            return;
        }
        matrices.drawTextWithShadow(client.textRenderer, text, x, y, 0xFFE6E6E6);
    }

    private static int updateAndGetFps() {
        long now = System.currentTimeMillis();
        if (fpsWindowStartedAtMs <= 0L) {
            fpsWindowStartedAtMs = now;
            framesInWindow = 0;
            displayedFps = 0;
        }

        framesInWindow++;
        long elapsed = now - fpsWindowStartedAtMs;
        if (elapsed >= 1000L) {
            displayedFps = (int) Math.max(0L, (framesInWindow * 1000L) / Math.max(1L, elapsed));
            framesInWindow = 0;
            fpsWindowStartedAtMs = now;
        }

        return displayedFps;
    }

    private static boolean isAuctionLikeScreen(Screen screen) {
        if (!(screen instanceof GenericContainerScreen)) {
            return false;
        }

        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        String lower = title.toLowerCase(Locale.ROOT).replace('ё', 'е');

        if (lower.contains("храни") || lower.contains("storage") || lower.contains("склад") || lower.contains("vault")) {
            return false;
        }

        return lower.contains("аукцион")
                || lower.contains("auction")
                || lower.contains("поиск")
                || lower.contains("search")
                || isPagedSearchTitle(lower);
    }

    private static boolean isPagedSearchTitle(String lower) {
        if (lower == null) {
            return false;
        }
        String s = lower.replace('ё', 'е').trim();
        boolean hasPageMarker = s.contains("[") && s.contains("/") && s.contains("]");
        boolean hasSearchPrefix = s.startsWith("☃ п:") || s.startsWith("п:") || s.contains(" п:");
        return hasPageMarker && hasSearchPrefix;
    }
}
