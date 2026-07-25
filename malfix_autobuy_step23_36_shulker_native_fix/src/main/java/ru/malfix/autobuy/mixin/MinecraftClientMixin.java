package ru.malfix.autobuy.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.malfix.autobuy.MalfixAutoBuyMod;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void malfix_autobuy$onClientTick(CallbackInfo ci) {
        MalfixAutoBuyMod.onClientTick((MinecraftClient) (Object) this);
    }
}
