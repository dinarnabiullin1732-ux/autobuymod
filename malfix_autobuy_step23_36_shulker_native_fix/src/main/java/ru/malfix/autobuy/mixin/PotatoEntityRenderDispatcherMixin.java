package ru.malfix.autobuy.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.malfix.autobuy.render.PotatoMode;

@Mixin(EntityRenderDispatcher.class)
public abstract class PotatoEntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void malfix_autobuy$potatoCancelEntityRender(E entity, double x, double y, double z,
                                                                            float yaw, float tickDelta,
                                                                            MatrixStack matrices,
                                                                            VertexConsumerProvider vertexConsumers,
                                                                            int light, CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }
}
