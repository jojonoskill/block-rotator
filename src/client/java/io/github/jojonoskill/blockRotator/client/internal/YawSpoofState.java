package io.github.jojonoskill.blockRotator.client.internal;

import net.minecraft.util.math.BlockPos;

public final class YawSpoofState {
    private YawSpoofState() {}

    // restore target yaw
    public static volatile float restoreYaw = 0f;

    // wait until server confirms the placed block (or timeout)
    public static volatile boolean waiting = false;
    public static volatile BlockPos waitPos = BlockPos.ORIGIN;
    public static volatile int timeoutTicks = 0;
}
