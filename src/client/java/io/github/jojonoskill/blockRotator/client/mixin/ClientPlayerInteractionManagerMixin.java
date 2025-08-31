package io.github.jojonoskill.blockRotator.client.mixin;

import com.mojang.blaze3d.systems.RenderCall;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.jojonoskill.blockRotator.client.BlockRotatorClient;
import io.github.jojonoskill.blockRotator.client.internal.PlacePacketGate;
import io.github.jojonoskill.blockRotator.client.internal.YawSpoofState;
import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.function.IntFunction;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = {"interactBlock","interactBlockInternal"}, at = @At("HEAD"), cancellable = true)
    private void br$customPlace(ClientPlayerEntity player, Hand hand, BlockHitResult originalHit,
                                CallbackInfoReturnable<ActionResult> cir) {
        final boolean wantTop    = BlockRotatorClient.forceTopHalf;
        final boolean wantFacing = BlockRotatorClient.forceFacing;
        if (!wantTop && !wantFacing) return;

        final MinecraftClient mc = MinecraftClient.getInstance();
        if (player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        // vanilla placement cell
        final ItemStack stack = player.getStackInHand(hand);
        final Block block = stack.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
        final BlockPos clicked = originalHit.getBlockPos();
        final boolean replaceable =
                mc.world.getBlockState(clicked).canReplace(new ItemPlacementContext(player, hand, stack, originalHit));
        final BlockPos placePos = replaceable ? clicked : clicked.offset(originalHit.getSide());

        // forge top-half hit if needed
        BlockHitResult hit = originalHit;
        if (wantTop && block != null && (block instanceof StairsBlock || block instanceof SlabBlock)) {
            Vec3d p = new Vec3d(placePos.getX() + 0.5, placePos.getY() + 0.88, placePos.getZ() + 0.5);
            hit = new BlockHitResult(p, Direction.DOWN, placePos, false);
        }

        // client prediction + force mesh rebuild
        if (wantTop && block != null) {
            if (block instanceof StairsBlock stairs) {
                Direction finalFacing = BlockRotatorClient.forceFacing
                        ? BlockRotatorClient.desiredFacing
                        : player.getHorizontalFacing().getOpposite();
                var predicted = stairs.getDefaultState()
                        .with(StairsBlock.HALF, BlockHalf.TOP)
                        .with(StairsBlock.FACING, finalFacing);
                if (predicted.contains(Properties.WATERLOGGED)) {
                    boolean water = mc.world.getFluidState(placePos).isOf(Fluids.WATER);
                    predicted = predicted.with(Properties.WATERLOGGED, water);
                }
                mc.world.setBlockState(placePos, predicted, 3);
                rebuildSectionNow(mc, placePos); // <— instead of worldRenderer.reload()

                try {
                    var wr = mc.worldRenderer;
                    // 1. schedule normal updates so Sodium/vanilla catch it
                    wr.scheduleBlockRenders(placePos.getX()-1, placePos.getY()-1, placePos.getZ()-1,
                            placePos.getX()+1, placePos.getY()+1, placePos.getZ()+1);
                    // 2. ALSO force a synchronous rebuild of the containing chunk section
                    var method = wr.getClass().getDeclaredMethod("reloadSection", int.class, int.class, int.class);
                    method.setAccessible(true);
                    method.invoke(wr, placePos.getX() >> 4, placePos.getY() >> 4, placePos.getZ() >> 4);
                } catch (Throwable ignored) {
                    // fallback to brute-force if mappings differ
                    mc.worldRenderer.reload();
                }

            } else if (block instanceof SlabBlock slab) {
                var predicted = slab.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
                if (predicted.contains(Properties.WATERLOGGED)) {
                    boolean water = mc.world.getFluidState(placePos).isOf(Fluids.WATER);
                    predicted = predicted.with(Properties.WATERLOGGED, water);
                }
                mc.world.setBlockState(placePos, predicted, 3);
                mc.world.setBlockState(placePos, predicted, 3);

            }
        }

        // yaw spoof (server-facing); restore on S2C ack
        if (wantFacing) {
            Direction sendDir = BlockRotatorClient.desiredFacing.getOpposite();
            float spoofYaw = yawFor(sendDir);
            YawSpoofState.restoreYaw   = player.getYaw();
            YawSpoofState.waitPos      = placePos;
            YawSpoofState.timeoutTicks = 10;
            YawSpoofState.waiting      = true;
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.LookAndOnGround(spoofYaw, player.getPitch(), player.isOnGround()));
        }

        // block vanilla + send our sequenced packet (make captured vars final)
        PlacePacketGate.suppressVanilla = true;
        final Hand handRef = hand;
        final BlockHitResult hitRef = hit;
        final ClientWorld worldRef = (ClientWorld) mc.world;
        final Object selfRef = this;

        // find sendSequencedPacket(ClientWorld, IntFunction<Packet<?>>)
        Method seqSend = null;
        for (Method m : selfRef.getClass().getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() == void.class &&
                    p.length == 2 &&
                    p[0] == ClientWorld.class &&
                    IntFunction.class.isAssignableFrom(p[1])) {
                seqSend = m; break;
            }
        }

        if (seqSend != null) {
            final Method seqSendRef = seqSend;
            try {
                seqSendRef.setAccessible(true);
                PlacePacketGate.allowOnce(() -> {
                    try {
                        IntFunction<Packet<?>> factory = (int seq) ->
                                new PlayerInteractBlockC2SPacket(handRef, hitRef, seq);
                        seqSendRef.invoke(selfRef, worldRef, factory);
                    } catch (Throwable ignored) {}
                });
            } finally {
                PlacePacketGate.suppressVanilla = false;
            }
            player.swingHand(handRef);
            cir.setReturnValue(ActionResult.SUCCESS);
            cir.cancel();
        } else {
            PlacePacketGate.suppressVanilla = false;
        }
    }

    private static float yawFor(Direction d) {
        return switch (d) {
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case NORTH -> 180f;
            case EAST  -> -90f;
            default    -> 0f;
        };
    }

    // imports:
// import com.mojang.blaze3d.systems.RenderSystem;
// import net.minecraft.client.render.WorldRenderer;
// import java.lang.reflect.Method;

    private static void rebuildSectionNow(MinecraftClient mc, BlockPos pos) {
        Runnable rebuild = () -> {
            WorldRenderer wr = mc.worldRenderer;
            int sx = pos.getX() >> 4, sy = pos.getY() >> 4, sz = pos.getZ() >> 4;

            String[] names = { "rebuildSectionImmediate", "rebuildSection", "reloadSection", "scheduleRebuild" };
            for (String n : names) {
                try {
                    var m = wr.getClass().getDeclaredMethod(n, int.class, int.class, int.class);
                    m.setAccessible(true);
                    m.invoke(wr, sx, sy, sz);
                    return;
                } catch (Throwable ignored) {}
            }
            wr.scheduleBlockRenders(pos.getX()-1, pos.getY()-1, pos.getZ()-1,
                    pos.getX()+1, pos.getY()+1, pos.getZ()+1);
        };

        if (RenderSystem.isOnRenderThread()) {
            rebuild.run();
        } else {
            RenderSystem.recordRenderCall(new RenderCall() {
                @Override public void execute() { rebuild.run(); }
            });
        }
    }

}
