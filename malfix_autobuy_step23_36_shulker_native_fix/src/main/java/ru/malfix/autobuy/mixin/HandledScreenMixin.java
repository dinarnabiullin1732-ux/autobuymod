package ru.malfix.autobuy.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.malfix.autobuy.auction.AuctionCheapestHighlighter;
import ru.malfix.autobuy.auction.AuctionFpsOverlay;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void malfix_autobuy$drawCheapestAuctionSlot(DrawContext matrices, Slot slot, CallbackInfo ci) {
        if (!AuctionCheapestHighlighter.shouldHighlight(slot)) {
            return;
        }

        /*
         * In Minecraft 1.16.5 HandledScreen already renders drawSlot() inside a matrix
         * translated to the container origin. slot.x/slot.y are local container coords here.
         * Do NOT add HandledScreen.x/y again.
         */
        int sx = slot.x;
        int sy = slot.y;

        // Only a small corner square: minimal visual noise and almost no render cost.
        matrices.fill(sx + 11, sy + 1, sx + 15, sy + 5, 0xDD00FF42);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void malfix_autobuy$drawAuctionFps(DrawContext matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AuctionFpsOverlay.render(matrices, this.x, this.y, this.backgroundWidth);
    }
}
