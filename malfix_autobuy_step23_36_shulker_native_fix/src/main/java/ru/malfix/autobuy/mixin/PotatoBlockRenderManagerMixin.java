package ru.malfix.autobuy.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.malfix.autobuy.render.PotatoMode;

import java.util.Random;

@Mixin(BlockRenderManager.class)
public abstract class PotatoBlockRenderManagerMixin {

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelBlockRender(BlockState state, BlockPos pos, BlockRenderView world,
                                                        MatrixStack matrices, VertexConsumer vertexConsumer,
                                                        boolean cull, Random random,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (PotatoMode.isEnabled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelFluidRender(BlockPos pos, BlockRenderView world,
                                                        VertexConsumer vertexConsumer, FluidState state,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (PotatoMode.isEnabled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "renderDamage", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelDamageRender(BlockState state, BlockPos pos, BlockRenderView world,
                                                         MatrixStack matrices, VertexConsumer vertexConsumer,
                                                         CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBlockAsEntity", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelBlockEntityItemRender(BlockState state, MatrixStack matrices,
                                                                  VertexConsumerProvider vertexConsumers,
                                                                  int light, int overlay, CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }
}
