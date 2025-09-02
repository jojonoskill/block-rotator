package io.github.jojonoskill.blockRotator.client.mixin;

import io.github.jojonoskill.blockRotator.client.BlockRotatorClient;
import io.github.jojonoskill.blockRotator.client.common.RotateMode;
import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void br$interceptInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult originalHit,
                                           CallbackInfoReturnable<ActionResult> cir) {
        // Single “smart rotate” modifier (bind one key to toggle this)
        final boolean rotateActive = BlockRotatorClient.rotateActive; // or: BlockRotatorClient.forceFacing || BlockRotatorClient.forceAxis
        final boolean forceTopHalf = BlockRotatorClient.forceTopHalf;

        if (!rotateActive && !forceTopHalf) return;

        final MinecraftClient mc = MinecraftClient.getInstance();
        if (player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        final ItemStack stack = player.getStackInHand(hand);
        final Block block = (stack.getItem() instanceof BlockItem bi) ? bi.getBlock() : null;

        // vanilla placePos
        final BlockPos clicked = originalHit.getBlockPos();
        final boolean replaceable =
                mc.world.getBlockState(clicked).canReplace(new ItemPlacementContext(player, hand, stack, originalHit));
        final BlockPos placePos = replaceable ? clicked : clicked.offset(originalHit.getSide());

        // Decide the rotation mode per-block
        final RotateMode mode = decideMode(block);

        // If nothing to do and no top-half trick, bail
        if (mode == RotateMode.NONE && !(forceTopHalf && isTopEligible(block))) return;

        // Start from original hit and override only what’s needed
        BlockHitResult hit = originalHit;

        // Top-half trick for slabs/stairs can coexist with YAW mode
        if (forceTopHalf && isTopEligible(block)) {
            hit = forgeTopHit(placePos);
        }

        if (mode == RotateMode.AXIS) {
            hit = forgeAxisHit(placePos, BlockRotatorClient.ORIENT.axis());
        }
        if (mode == RotateMode.YAW) {
            final float spoofYaw = yawFor(BlockRotatorClient.ORIENT.facing().getOpposite());
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.LookAndOnGround(spoofYaw, player.getPitch(), player.isOnGround()));
        }

        // Keep your sequence/slot dance
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(player.getInventory().selectedSlot));
        final ClientWorld world = (ClientWorld) mc.world;
        final PendingUpdateManager pending =
                ((ClientWorldAccessor) (Object) world).blockrotator$getPendingUpdateManager().incrementSequence();

        try {
            final int seq = pending.getSequence();
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, hit, seq));
        } finally {
            try { pending.close(); } catch (Throwable ignored) {}
        }

        player.swingHand(hand);
        cir.setReturnValue(ActionResult.SUCCESS);
        cir.cancel();
    }

    @Unique
    private static RotateMode decideMode(Block block) {
        if (block instanceof StairsBlock) return RotateMode.YAW;
        if (hasAxisProperty(block)) return RotateMode.AXIS;
        return RotateMode.NONE;
    }

    @Unique
    private static boolean isTopEligible(Block block) {
        return block instanceof StairsBlock || block instanceof SlabBlock;
    }

    @Unique
    private static boolean hasAxisProperty(Block block) {
        return block != null && block.getDefaultState().contains(Properties.AXIS);
    }

    @Unique
    private static BlockHitResult forgeTopHit(BlockPos pos) {
        Vec3d p = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.999, pos.getZ() + 0.5);
        return new BlockHitResult(p, Direction.DOWN, pos, false);
    }

    @Unique
    private static BlockHitResult forgeAxisHit(BlockPos pos, Direction.Axis axis) {
        // Axis is chosen by face: X→EAST/WEST, Y→UP/DOWN, Z→SOUTH/NORTH. One representative is enough.
        Direction side = switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
        Vec3d p = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return new BlockHitResult(p, side, pos, false);
    }

    @Unique
    private static float yawFor(Direction d) {
        return switch (d) {
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case NORTH -> 180f;
            case EAST  -> -90f;
            default    -> 0f;
        };
    }
}
