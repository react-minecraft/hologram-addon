package com.g4vrk.hologramAddon.hologram.factory;

import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.config.impl.LegacyHologramConfiguration;
import com.g4vrk.hologramAddon.hologram.config.impl.ModernHologramConfiguration;
import com.g4vrk.hologramAddon.hologram.impl.VersionedTargetedHologram;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Creates version-aware targeted holograms from loaded display configurations.
 */
@ApiStatus.Internal
public final class HologramFactory {

    private final ModernHologramConfiguration modernHologramConfiguration;
    private final LegacyHologramConfiguration legacyHologramConfiguration;
    private final boolean createTeam;

    public HologramFactory(
        final @NotNull ModernHologramConfiguration modernHologramConfiguration,
        final @NotNull LegacyHologramConfiguration legacyHologramConfiguration,
        final boolean createTeam
    ) {

        this.modernHologramConfiguration = modernHologramConfiguration;
        this.legacyHologramConfiguration = legacyHologramConfiguration;
        this.createTeam = createTeam;

    }

    public @NotNull TargetedHologram create(
        final @NotNull Player target
    ) {

        return new VersionedTargetedHologram(target, this.modernHologramConfiguration, this.legacyHologramConfiguration, this.createTeam);

    }

}
