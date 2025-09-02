package io.github.jojonoskill.blockRotator.client;

import io.github.jojonoskill.blockRotator.client.state.OrientationState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import io.github.jojonoskill.blockRotator.client.ui.BlockRotatorHud;

public class BlockRotatorClient implements ClientModInitializer {
    public static boolean forceTopHalf = false;
    public static boolean rotateActive = false;
    public static final String CAT = "key.categories.blockrotator";

    public static OrientationState ORIENT = new OrientationState(Direction.NORTH, Axis.Y);

    private static KeyBinding toggleTop;
    private static KeyBinding toggleSmart;
    private static KeyBinding cycleOrientation;

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((ctx, counter) -> BlockRotatorHud.render(ctx)); // ignore counter

        toggleTop = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockrotator.toggleTopHalf", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CAT));
        toggleSmart = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockrotator.toggleSmartRotate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, CAT));
        cycleOrientation = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockrotator.cycleOrientation", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, CAT));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleTop.wasPressed()) {
                forceTopHalf = !forceTopHalf;
                toast("[BlockRotator] Top-half: " + onOff(forceTopHalf));
            }
            while (toggleSmart.wasPressed()) {
                rotateActive = !rotateActive;
                toast("[BlockRotator] Smart rotate: " + onOff(rotateActive) +
                        " (facing=" + ORIENT.facing() + ", axis=" + ORIENT.axis() + ")");
            }
            while (cycleOrientation.wasPressed()) {
                Block held = heldBlock(client);
                if (held instanceof StairsBlock) {
                    ORIENT = ORIENT.cycleFacing();
                    toast("[BlockRotator] Facing→ " + ORIENT.facing());
                } else if (hasAxisProperty(held)) {
                    ORIENT = ORIENT.cycleAxis();
                    toast("[BlockRotator] Axis→ " + ORIENT.axis());
                } else if (held instanceof SlabBlock) {
                    ORIENT = ORIENT.cycleFacing();
                    toast("[BlockRotator] Facing→ " + ORIENT.facing());
                } else {
                    ORIENT = ORIENT.cycleFacing();
                    toast("[BlockRotator] Facing→ " + ORIENT.facing());
                }
            }
        });
    }

    private static Block heldBlock(MinecraftClient mc) {
        if (mc == null || mc.player == null) return null;
        ItemStack s = mc.player.getMainHandStack();
        if (s.getItem() instanceof BlockItem bi) return bi.getBlock();
        return null;
    }

    private static boolean hasAxisProperty(Block block) {
        return block != null && block.getDefaultState().contains(Properties.AXIS);
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    private static void toast(String s) {
        var p = MinecraftClient.getInstance().player;
        if (p != null) p.sendMessage(Text.of(s), true);
    }
}
