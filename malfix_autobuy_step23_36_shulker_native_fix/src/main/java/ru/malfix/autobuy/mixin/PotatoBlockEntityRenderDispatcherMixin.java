package ru.malfix.autobuy.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.malfix.autobuy.render.PotatoMode;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class PotatoBlockEntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void malfix_autobuy$potatoCancelBlockEntityRender(E blockEntity, float tickDelta,
                                                                                      MatrixStack matrices,
                                                                                      VertexConsumerProvider vertexConsumers,
                                                                                      CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }
}
