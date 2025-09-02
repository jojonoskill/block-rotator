package io.github.jojonoskill.blockRotator.client.ui;

import io.github.jojonoskill.blockRotator.client.BlockRotatorClient;
import net.minecraft.block.Block;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;

public final class BlockRotatorHud {
    private static final int PAD = 4;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.options.hudHidden) return;

        Block held = heldBlock();
        boolean relevant = held instanceof StairsBlock || hasAxisProperty(held);
        if (!BlockRotatorClient.rotateActive && !relevant) return;

        String line;
        if (!BlockRotatorClient.rotateActive) {
            line = "Rotate: OFF";
        } else if (held instanceof StairsBlock) {
            line = "Yaw: " + BlockRotatorClient.ORIENT.facing().asString();
        } else if (hasAxisProperty(held)) {
            line = "Axis: " + BlockRotatorClient.ORIENT.axis().asString();
        } else {
            line = "Yaw: " + BlockRotatorClient.ORIENT.facing().asString();
        }

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x = PAD;
        int y = sh - PAD - 10;
        int textW = mc.textRenderer.getWidth(line);
        int bg = 0x80000000;

        ctx.fill(x - 2, y - 2, x + textW + 2, y + 10, bg);
        int color = BlockRotatorClient.rotateActive ? 0x80FF80 : 0xA0A0A0;
        ctx.drawTextWithShadow(mc.textRenderer, line, x, y, color);

        if (BlockRotatorClient.forceTopHalf) {
            String t = "Top";
            int tw = mc.textRenderer.getWidth(t);
            int tx = x + textW + 8;
            ctx.fill(tx - 2, y - 2, tx + tw + 2, y + 10, bg);
            ctx.drawTextWithShadow(mc.textRenderer, t, tx, y, 0xFFD080);
        }
    }

    private static Block heldBlock() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return null;
        ItemStack s = mc.player.getMainHandStack();
        return (s.getItem() instanceof BlockItem bi) ? bi.getBlock() : null;
    }

    private static boolean hasAxisProperty(Block b) {
        return b != null && b.getDefaultState().contains(Properties.AXIS);
    }
}
