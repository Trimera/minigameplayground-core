package com.minigameplayground.zone;

import java.util.Locale;

/**
 * Damage balancing mode for a playground zone.
 */
public enum DamageMode {
    PVP("pvp", "仅 PvP"),
    ALL("all", "全部伤害");

    private final String id;
    private final String displayName;

    DamageMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return this.id;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public static DamageMode fromId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (DamageMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return mode;
            }
        }
        return null;
    }
}
