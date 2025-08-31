package io.github.jojonoskill.blockRotator.client.mixin;

import io.github.jojonoskill.blockRotator.client.internal.YawSpoofState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void br$yawSpoofTimeout(CallbackInfo ci) {
        if (!YawSpoofState.waiting) return;
        if (--YawSpoofState.timeoutTicks > 0) return;

        YawSpoofState.waiting = false;
        MinecraftClient mc = (MinecraftClient)(Object)this;
        if (mc.player != null && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    YawSpoofState.restoreYaw, mc.player.getPitch(), mc.player.isOnGround()));
        }
    }
}
