package ru.malfix.autobuy.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.malfix.autobuy.MalfixAutoBuyMod;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void malfix_autobuy$handleServerChatMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (packet == null) {
            return;
        }

        try {
            Text message = packet.content();
            if (message != null) {
                MalfixAutoBuyMod.handleServerChatMessage(message.getString());
            }
        } catch (Throwable throwable) {
            System.out.println("[MAB] Incoming chat hook error:");
            throwable.printStackTrace(System.out);
        }
    }
}
