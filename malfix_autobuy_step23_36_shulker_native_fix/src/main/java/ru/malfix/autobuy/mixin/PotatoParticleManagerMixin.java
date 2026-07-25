package ru.malfix.autobuy.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.malfix.autobuy.render.PotatoMode;

@Mixin(ParticleManager.class)
public abstract class PotatoParticleManagerMixin {

    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelParticles(CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;)V", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelEmitter(Entity entity, ParticleEffect parameters, CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;I)V", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelEmitterWithAge(Entity entity, ParticleEffect parameters, int maxAge, CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelParticle(ParticleEffect parameters, double x, double y, double z,
                                                     double velocityX, double velocityY, double velocityZ,
                                                     CallbackInfoReturnable<Particle> cir) {
        if (PotatoMode.isEnabled()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelParticleObject(Particle particle, CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelBlockBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "addBlockBreakingParticles", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$potatoCancelBlockBreakingParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
        if (PotatoMode.isEnabled()) {
            ci.cancel();
        }
    }
}
