package com.g4vrk.hologramAddon.hologram.config.impl;

import com.g4vrk.hologramAddon.hologram.config.TargetedHologramConfiguration;
import org.spongepowered.configurate.ConfigurationNode;
import org.jetbrains.annotations.NotNull;

public final class LegacyHologramConfiguration implements TargetedHologramConfiguration {

    private final double yOffset;

    private final double lineSpacing;

    public LegacyHologramConfiguration(
            @NotNull ConfigurationNode node
    ) {
        this.yOffset = node
                .node("y-offset")
                .getDouble(0.38D);

        this.lineSpacing = Math.max(
                0.0D,
                node.node("line-spacing").getDouble(0.26D)
        );
    }

    @Override
    public double yOffset() {
        return this.yOffset;
    }

    public double lineSpacing() {
        return this.lineSpacing;
    }
}