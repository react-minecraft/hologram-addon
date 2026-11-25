package com.g4vrk.hologramAddon.hologram.impl;

import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.config.impl.LegacyHologramConfiguration;
import com.g4vrk.hologramAddon.hologram.config.impl.ModernHologramConfiguration;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Routes hologram rendering between {@code TextDisplay} and armor-stand backends per client version.
 */
@ApiStatus.Internal
public final class VersionedTargetedHologram extends TargetedHologram {

    private final ModernTargetedHologram modern;
    private final LegacyTargetedHologram legacy;

    public VersionedTargetedHologram(
        final @NotNull Player target,
        final @NotNull ModernHologramConfiguration modernHologramConfiguration,
        final @NotNull LegacyHologramConfiguration legacyHologramConfiguration,
        final boolean createTeam
    ) {

        super(target);
        this.modern = new ModernTargetedHologram(target, modernHologramConfiguration, createTeam);
        this.legacy = new LegacyTargetedHologram(target, legacyHologramConfiguration, createTeam);

    }

    @Override
    protected void showTo(
        final @NotNull Player viewer,
        final @NotNull List<Component> lines
    ) {

        if (supportsTextDisplay(viewer)) {
            this.legacy.hideFrom(viewer);
            this.modern.showTo(viewer, lines);
        } else {
            this.modern.hideFrom(viewer);
            this.legacy.showTo(viewer, lines);
        }

    }

    @Override
    protected void prepareMove() {

        this.modern.prepareMove();
        this.legacy.prepareMove();

    }

    @Override
    protected void moveTo(
        final @NotNull Player viewer
    ) {

        if (this.modern.hasViewer(viewer.getUniqueId())) {
            this.modern.moveTo(viewer);
        } else {
            this.legacy.moveTo(viewer);
        }

    }

    @Override
    public void hideFrom(
        final @NotNull Player viewer
    ) {

        this.modern.hideFrom(viewer);
        this.legacy.hideFrom(viewer);

    }

    private static boolean supportsTextDisplay(
        final @NotNull Player player
    ) {

        final ClientVersion version = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
        return version.isNewerThanOrEquals(ClientVersion.V_1_19_4);

    }

}
