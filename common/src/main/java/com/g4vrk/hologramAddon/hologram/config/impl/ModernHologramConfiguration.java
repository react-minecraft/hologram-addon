package com.g4vrk.hologramAddon.hologram.config.impl;

import com.g4vrk.hologramAddon.hologram.config.TargetedHologramConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Display configuration for modern {@code TextDisplay}-based holograms.
 */
@ApiStatus.Internal
public final class ModernHologramConfiguration implements TargetedHologramConfiguration {

    private final double yOffset;
    private final float textScale;
    private final float translationY;
    private final int backgroundColor;
    private final boolean shadow;

    public ModernHologramConfiguration(
        final @NotNull ConfigurationNode node
    ) {

        this.yOffset = node.node("y-offset").getDouble(1.5D);
        this.textScale = clampScale(node.node("text-scale").getFloat(0.75F));
        this.translationY = node.node("translation-y").getFloat(1.0F);
        this.backgroundColor = node.node("background-color").getInt(0);
        this.shadow = node.node("shadow").getBoolean(true);

    }

    @Override
    public double yOffset() {

        return this.yOffset;

    }

    public float textScale() {

        return this.textScale;

    }

    public float translationY() {

        return this.translationY;

    }

    public int backgroundColor() {

        return this.backgroundColor;

    }

    public boolean shadow() {

        return this.shadow;

    }

    public byte textFlags() {

        byte flags = 0;

        if (this.shadow) {
            flags |= 0x01;
        }

        return flags;

    }

    private static float clampScale(final float value) {

        return Math.max(0.01F, Math.min(4.0F, value));

    }

}
