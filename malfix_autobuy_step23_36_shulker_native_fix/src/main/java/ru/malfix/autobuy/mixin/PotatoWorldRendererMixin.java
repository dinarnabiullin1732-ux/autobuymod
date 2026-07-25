package ru.malfix.autobuy.mixin;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.malfix.autobuy.render.PotatoMode;

@Mixin(WorldRenderer.class)
public abstract class PotatoWorldRendererMixin {

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelWorldLayer(CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }
}
