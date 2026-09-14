package com.g4vrk.hologramAddon.hologram.impl;

import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.config.impl.LegacyHologramConfiguration;
import com.g4vrk.hologramAddon.hologram.config.impl.ModernHologramConfiguration;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class VersionedTargetedHologram extends TargetedHologram {

    private final ModernTargetedHologram modern;
    private final LegacyTargetedHologram legacy;

    public VersionedTargetedHologram(
            @NotNull Player target,
            @NotNull ModernHologramConfiguration modernHologramConfiguration,
            @NotNull LegacyHologramConfiguration legacyHologramConfiguration
    ) {
        this(target, modernHologramConfiguration, legacyHologramConfiguration, true);
    }

    public VersionedTargetedHologram(
            @NotNull Player target,
            @NotNull ModernHologramConfiguration modernHologramConfiguration,
            @NotNull LegacyHologramConfiguration legacyHologramConfiguration,
            boolean createTeam
    ) {
        super(target);

        this.modern = new ModernTargetedHologram(target, modernHologramConfiguration, createTeam);
        this.legacy = new LegacyTargetedHologram(target, legacyHologramConfiguration, createTeam);
    }

    @Override
    protected void showTo(
            @NotNull Player viewer,
            @NotNull List<Component> lines
    ) {
        if (supportsTextDisplay(viewer)) {
            this.modern.showTo(viewer, lines);
        } else {
            this.legacy.showTo(viewer, lines);
        }
    }

    @Override
    public void hideFrom(@NotNull Player viewer) {
        if (supportsTextDisplay(viewer)) {
            this.modern.hideFrom(viewer);
        } else {
            this.legacy.hideFrom(viewer);
        }
    }

    private boolean supportsTextDisplay(
            final @NotNull Player player
    ) {

        final ClientVersion version = PacketEvents.getAPI()
                .getPlayerManager()
                .getClientVersion(player);

        return version.isNewerThanOrEquals(
                ClientVersion.V_1_19_4
        );

    }
}