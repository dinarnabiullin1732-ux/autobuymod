package ru.malfix.autobuy.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.malfix.autobuy.MalfixAutoBuyMod;
import ru.malfix.autobuy.auction.AuctionCheapestHighlighter;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$handleLocalChat(String message, CallbackInfo ci) {
        handleOutgoing(message, ci);
    }

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$handleLocalCommand(String command, CallbackInfo ci) {
        handleOutgoing(command == null ? "" : "/" + command, ci);
    }

    private static void handleOutgoing(String message, CallbackInfo ci) {
        AuctionCheapestHighlighter.onLocalChatMessage(message);

        if (MalfixAutoBuyMod.handleClientChatMessage(message)) {
            ci.cancel();
        }
    }
}
