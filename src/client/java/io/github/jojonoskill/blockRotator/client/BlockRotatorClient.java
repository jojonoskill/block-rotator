package io.github.jojonoskill.blockRotator.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

public class BlockRotatorClient implements ClientModInitializer {
    public static boolean forceTopHalf = false;
    public static boolean forceFacing  = false;
    public static Direction desiredFacing = Direction.NORTH;

    private static KeyBinding toggleTop;
    private static KeyBinding toggleFacing;
    private static KeyBinding cycleFacing;

    @Override
    public void onInitializeClient() {
        toggleTop = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockrotator.toggleTopHalf", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.misc"));
        toggleFacing = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockrotator.toggleFacing", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.misc"));
        cycleFacing = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockrotator.cycleFacing", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.misc"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleTop.wasPressed()) {
                forceTopHalf = !forceTopHalf; toast("[BlockRotator] Top-half: " + onOff(forceTopHalf));
            }
            while (toggleFacing.wasPressed()) {
                forceFacing = !forceFacing; toast("[BlockRotator] Facing: " + onOff(forceFacing) + hint());
            }
            while (cycleFacing.wasPressed()) {
                desiredFacing = next(desiredFacing); toast("[BlockRotator] Desired facing: " + desiredFacing);
            }
        });
    }

    private static Direction next(Direction d) {
        return switch (d) { case NORTH -> Direction.EAST; case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST; case WEST -> Direction.NORTH; default -> Direction.NORTH; };
    }
    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }
    private static String hint() { return " (" + desiredFacing + ")"; }
    private static void toast(String s) {
        var p = MinecraftClient.getInstance().player; if (p != null) p.sendMessage(Text.of(s), true);
    }
}
