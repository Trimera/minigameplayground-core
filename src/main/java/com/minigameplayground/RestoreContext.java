package com.minigameplayground;

/**
 * Marks the interval in which a playground snapshot is being written back.
 *
 * <p>Create's kinetic blocks clear their speed/network data from
 * {@code updateIndirectNeighbourShapes} when a block is placed or its state changes.
 * WorldEdit and vanilla structure placement both call that hook, and WorldEdit may
 * flush block-update side effects after the paste method has returned. The Create
 * addon checks this context and skips that destructive hook during and shortly after
 * a restore.</p>
 *
 * <p>The grace period is deliberately counted in server ticks rather than wall-clock
 * time so a lagging server cannot leave the protection active indefinitely.</p>
 */
public final class RestoreContext {
    private static final int GRACE_TICKS_AFTER_RESTORE = 3;
    // WorldEdit may apply block-entity NBT from a worker/relight thread rather than
    // the command thread. A ThreadLocal would make isRestoring() false there, which
    // is exactly when Create needs the protection, so keep this state global.
    private static final java.util.concurrent.atomic.AtomicInteger DEPTH =
            new java.util.concurrent.atomic.AtomicInteger();
    private static volatile int graceTicks;

    private RestoreContext() {
    }

    public static void beginRestore() {
        DEPTH.incrementAndGet();
    }

    public static void endRestore() {
        if (DEPTH.decrementAndGet() <= 0) {
            DEPTH.set(0);
            graceTicks = GRACE_TICKS_AFTER_RESTORE;
        }
    }

    public static boolean isRestoring() {
        return DEPTH.get() > 0 || graceTicks > 0;
    }

    /** Called once per server tick to expire the post-restore grace window. */
    public static void tick() {
        if (graceTicks > 0) {
            graceTicks--;
        }
    }
}
