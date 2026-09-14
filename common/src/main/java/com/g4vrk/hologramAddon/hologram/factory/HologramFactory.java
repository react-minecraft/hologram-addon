package com.g4vrk.hologramAddon.hologram.factory;

import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.config.impl.LegacyHologramConfiguration;
import com.g4vrk.hologramAddon.hologram.config.impl.ModernHologramConfiguration;
import com.g4vrk.hologramAddon.hologram.impl.VersionedTargetedHologram;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class HologramFactory {

    private final ModernHologramConfiguration modernHologramConfiguration;
    private final LegacyHologramConfiguration legacyHologramConfiguration;
    private final boolean createTeam;

    public HologramFactory(
            @NotNull ModernHologramConfiguration modernHologramConfiguration,
            @NotNull LegacyHologramConfiguration legacyHologramConfiguration
    ) {
        this(modernHologramConfiguration, legacyHologramConfiguration, true);
    }

    public HologramFactory(
            @NotNull ModernHologramConfiguration modernHologramConfiguration,
            @NotNull LegacyHologramConfiguration legacyHologramConfiguration,
            boolean createTeam
    ) {
        this.modernHologramConfiguration = modernHologramConfiguration;
        this.legacyHologramConfiguration = legacyHologramConfiguration;
        this.createTeam = createTeam;
    }

    public @NotNull TargetedHologram create(
            final @NotNull Player target
    ) {
        return new VersionedTargetedHologram(target, modernHologramConfiguration, legacyHologramConfiguration, createTeam);
    }

}