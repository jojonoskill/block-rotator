package io.github.jojonoskill.blockRotator.client.state;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;

public record OrientationState(Direction facing, Axis axis) {
    public OrientationState cycleFacing() {
        Direction next = switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST  -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST  -> Direction.NORTH;
            default    -> Direction.NORTH;
        };
        return new OrientationState(next, axis);
    }

    public OrientationState cycleAxis() {
        Axis next = switch (axis) {
            case X -> Axis.Y;
            case Y -> Axis.Z;
            case Z -> Axis.X;
        };
        return new OrientationState(facing, next);
    }
}
