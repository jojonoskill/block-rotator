package io.github.jojonoskill.blockRotator.client.internal;

public final class PlacePacketGate {
    private PlacePacketGate() {}
    public static volatile boolean suppressVanilla = false;
    private static final ThreadLocal<Boolean> allowThisThread = ThreadLocal.withInitial(() -> false);
    public static void allowOnce(Runnable send) {
        boolean prev = allowThisThread.get();
        try { allowThisThread.set(true); send.run(); } finally { allowThisThread.set(prev); }
    }
    public static boolean isAllowedHere() { return allowThisThread.get(); }
}
