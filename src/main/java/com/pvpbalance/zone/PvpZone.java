package com.pvpbalance.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * An axis-aligned PVP zone with independent damage, healing and durability settings.
 *
 * <p>Zones are dimension-specific. The dimension id is recorded when the zone is
 * created and every position lookup checks it first.</p>
 */
public final class PvpZone {
    public static final float DEFAULT_DAMAGE_BASELINE = 20.0F;
    public static final float DEFAULT_RESTORE_BASELINE = 20.0F;
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";

    private final String id;
    private final String dimension;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;
    private final float standardHealth; // damage baseline
    private final float restoreBaseline; // healing baseline
    private final boolean keepDurability;
    private final boolean damageDisabled;
    private final boolean healDisabled;

    public PvpZone(String id, double x1, double y1, double z1, double x2, double y2, double z2,
                   float standardHealth) {
        this(id, DEFAULT_DIMENSION, x1, y1, z1, x2, y2, z2,
                standardHealth, DEFAULT_RESTORE_BASELINE, true, false, false);
    }

    public PvpZone(String id, double x1, double y1, double z1, double x2, double y2, double z2,
                   float standardHealth, float restoreBaseline, boolean keepDurability) {
        this(id, DEFAULT_DIMENSION, x1, y1, z1, x2, y2, z2,
                standardHealth, restoreBaseline, keepDurability, false, false);
    }

    public PvpZone(String id, double x1, double y1, double z1, double x2, double y2, double z2,
                   float standardHealth, float restoreBaseline, boolean keepDurability,
                   boolean damageDisabled, boolean healDisabled) {
        this(id, DEFAULT_DIMENSION, x1, y1, z1, x2, y2, z2,
                standardHealth, restoreBaseline, keepDurability, damageDisabled, healDisabled);
    }

    public PvpZone(String id, String dimension,
                   double x1, double y1, double z1, double x2, double y2, double z2,
                   float standardHealth, float restoreBaseline, boolean keepDurability,
                   boolean damageDisabled, boolean healDisabled) {
        this.id = id;
        this.dimension = dimension == null || dimension.isBlank() ? DEFAULT_DIMENSION : dimension;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.standardHealth = standardHealth;
        this.restoreBaseline = restoreBaseline;
        this.keepDurability = keepDurability;
        this.damageDisabled = damageDisabled;
        this.healDisabled = healDisabled;
    }

    public String getId() {
        return this.id;
    }

    public String getDimension() {
        return this.dimension;
    }

    public double getMinX() {
        return this.minX;
    }

    public double getMinY() {
        return this.minY;
    }

    public double getMinZ() {
        return this.minZ;
    }

    public double getMaxX() {
        return this.maxX;
    }

    public double getMaxY() {
        return this.maxY;
    }

    public double getMaxZ() {
        return this.maxZ;
    }

    public float getStandardHealth() {
        return this.standardHealth;
    }

    public float getDamageBaseline() {
        return this.standardHealth;
    }

    public float getRestoreBaseline() {
        return this.restoreBaseline;
    }

    public boolean isKeepDurability() {
        return this.keepDurability;
    }

    public boolean isDamageDisabled() {
        return this.damageDisabled;
    }

    public boolean isHealDisabled() {
        return this.healDisabled;
    }

    public boolean isDimension(Level level) {
        return level != null && this.dimension.equals(level.dimension().location().toString());
    }

    public boolean contains(ServerPlayer player) {
        return this.isDimension(player.level())
                && this.contains(player.getX(), player.getY(), player.getZ());
    }

    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x <= this.maxX
                && y >= this.minY && y <= this.maxY
                && z >= this.minZ && z <= this.maxZ;
    }

    public boolean containsBlock(Level level, BlockPos pos) {
        return this.isDimension(level) && this.containsBlock(pos);
    }

    /**
     * Returns the zone as world-space AABB in block units. The maximum edge is one
     * block beyond {@link #getMaxBlockX()} because the configured maximum coordinate
     * denotes an included block, not an excluded edge.
     */
    public AABB getBlockBounds() {
        return new AABB(
                this.getMinBlockX(), this.getMinBlockY(), this.getMinBlockZ(),
                this.getMaxBlockX() + 1.0D, this.getMaxBlockY() + 1.0D, this.getMaxBlockZ() + 1.0D);
    }

    public boolean intersectsBlockBounds(AABB bounds) {
        if (bounds == null) {
            return false;
        }
        AABB zoneBounds = this.getBlockBounds();
        return bounds.maxX > zoneBounds.minX && bounds.minX < zoneBounds.maxX
                && bounds.maxY > zoneBounds.minY && bounds.minY < zoneBounds.maxY
                && bounds.maxZ > zoneBounds.minZ && bounds.minZ < zoneBounds.maxZ;
    }

    /**
     * Checks whether a whole contraption bounds fits inside this zone. A tiny epsilon
     * avoids rejecting a perfectly aligned contraption after floating-point rotation.
     */
    public boolean fullyContainsBlockBounds(AABB bounds) {
        if (bounds == null) {
            return false;
        }
        double epsilon = 1.0E-6D;
        AABB zoneBounds = this.getBlockBounds();
        return bounds.minX >= zoneBounds.minX - epsilon
                && bounds.minY >= zoneBounds.minY - epsilon
                && bounds.minZ >= zoneBounds.minZ - epsilon
                && bounds.maxX <= zoneBounds.maxX + epsilon
                && bounds.maxY <= zoneBounds.maxY + epsilon
                && bounds.maxZ <= zoneBounds.maxZ + epsilon;
    }

    public boolean containsBlock(BlockPos pos) {
        return pos.getX() >= this.getMinBlockX() && pos.getX() <= this.getMaxBlockX()
                && pos.getY() >= this.getMinBlockY() && pos.getY() <= this.getMaxBlockY()
                && pos.getZ() >= this.getMinBlockZ() && pos.getZ() <= this.getMaxBlockZ();
    }

    public int getMinBlockX() {
        return Mth.floor(this.minX);
    }

    public int getMinBlockY() {
        return Mth.floor(this.minY);
    }

    public int getMinBlockZ() {
        return Mth.floor(this.minZ);
    }

    public int getMaxBlockX() {
        return Mth.floor(this.maxX);
    }

    public int getMaxBlockY() {
        return Mth.floor(this.maxY);
    }

    public int getMaxBlockZ() {
        return Mth.floor(this.maxZ);
    }

    public BlockPos getMinBlockPos() {
        return new BlockPos(this.getMinBlockX(), this.getMinBlockY(), this.getMinBlockZ());
    }

    public Vec3i getBlockSize() {
        return new Vec3i(this.getSizeX(), this.getSizeY(), this.getSizeZ());
    }

    public int getSizeX() {
        return this.getMaxBlockX() - this.getMinBlockX() + 1;
    }

    public int getSizeY() {
        return this.getMaxBlockY() - this.getMinBlockY() + 1;
    }

    public int getSizeZ() {
        return this.getMaxBlockZ() - this.getMinBlockZ() + 1;
    }

    public long getBlockVolume() {
        return (long) this.getSizeX() * (long) this.getSizeY() * (long) this.getSizeZ();
    }

    public PvpZone withStandardHealth(float newStandardHealth) {
        return new PvpZone(this.id, this.dimension, this.minX, this.minY, this.minZ,
                this.maxX, this.maxY, this.maxZ, newStandardHealth, this.restoreBaseline,
                this.keepDurability, this.damageDisabled, this.healDisabled);
    }

    public PvpZone withRestoreBaseline(float newRestoreBaseline) {
        return new PvpZone(this.id, this.dimension, this.minX, this.minY, this.minZ,
                this.maxX, this.maxY, this.maxZ, this.standardHealth, newRestoreBaseline,
                this.keepDurability, this.damageDisabled, this.healDisabled);
    }

    public PvpZone withKeepDurability(boolean newKeepDurability) {
        return new PvpZone(this.id, this.dimension, this.minX, this.minY, this.minZ,
                this.maxX, this.maxY, this.maxZ, this.standardHealth, this.restoreBaseline,
                newKeepDurability, this.damageDisabled, this.healDisabled);
    }

    public PvpZone withDamageDisabled(boolean newDamageDisabled) {
        return new PvpZone(this.id, this.dimension, this.minX, this.minY, this.minZ,
                this.maxX, this.maxY, this.maxZ, this.standardHealth, this.restoreBaseline,
                this.keepDurability, newDamageDisabled, this.healDisabled);
    }

    public PvpZone withHealDisabled(boolean newHealDisabled) {
        return new PvpZone(this.id, this.dimension, this.minX, this.minY, this.minZ,
                this.maxX, this.maxY, this.maxZ, this.standardHealth, this.restoreBaseline,
                this.keepDurability, this.damageDisabled, newHealDisabled);
    }

    public String describeBounds() {
        return String.format(java.util.Locale.ROOT,
                "(%.2f, %.2f, %.2f) -> (%.2f, %.2f, %.2f)",
                this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public String describeSettings() {
        String damage = this.damageDisabled
                ? "禁用"
                : String.format(java.util.Locale.ROOT, "%.1f", this.standardHealth);
        String heal = this.healDisabled
                ? "禁用"
                : String.format(java.util.Locale.ROOT, "%.1f", this.restoreBaseline);
        return String.format(java.util.Locale.ROOT,
                "维度: %s，伤害: %s，恢复: %s，耐久保护: %s",
                this.dimension, damage, heal, this.keepDurability ? "开启" : "关闭");
    }
}
