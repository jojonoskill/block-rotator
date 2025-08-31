package io.github.jojonoskill.blockRotator.client.mixin;

import io.github.jojonoskill.blockRotator.client.internal.YawSpoofState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void br$onBlockUpdate(BlockUpdateS2CPacket pkt, CallbackInfo ci) {
        if (!YawSpoofState.waiting) return;
        if (pkt.getPos().equals(YawSpoofState.waitPos)) restoreYaw();
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void br$onChunkDelta(ChunkDeltaUpdateS2CPacket pkt, CallbackInfo ci) {
        if (!YawSpoofState.waiting) return;
        BlockPos target = YawSpoofState.waitPos;
        pkt.visitUpdates((pos, state) -> { if (pos.equals(target)) restoreYaw(); });
    }

    private static void restoreYaw() {
        YawSpoofState.waiting = false;
        var mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    YawSpoofState.restoreYaw, mc.player.getPitch(), mc.player.isOnGround()));
        }
    }
}
