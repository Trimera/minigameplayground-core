package com.minigameplayground;

/**
 * Marks the short interval in which a playground snapshot is being written back.
 *
 * <p>Create's kinetic blocks clear their speed/network data from
 * {@code updateIndirectNeighbourShapes} when a block is placed. WorldEdit and
 * vanilla structure placement both call that hook before applying block entity
 * NBT, which otherwise makes motors, shafts and belts come back at zero speed.
 * The Create addon checks this context and skips that destructive hook during
 * restore.</p>
 */
public final class RestoreContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private RestoreContext() {
    }

    public static void beginRestore() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void endRestore() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static boolean isRestoring() {
        return DEPTH.get() > 0;
    }
}
