package io.github.jojonoskill.blockRotator.client.mixin;

import io.github.jojonoskill.blockRotator.client.internal.PlacePacketGate;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    // Hook the sender (works across 1.21/1.21.1 without hardcoded descriptors)
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void br$gatePlace(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof PlayerInteractBlockC2SPacket) {
            // allow only our custom send wrapped in PlacePacketGate.allowOnce(...)
            if (PlacePacketGate.isAllowedHere()) return;
            if (PlacePacketGate.suppressVanilla) ci.cancel();
        }
    }
}
