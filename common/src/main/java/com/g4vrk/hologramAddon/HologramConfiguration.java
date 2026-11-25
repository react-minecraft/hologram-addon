package com.g4vrk.hologramAddon;

import com.g4vrk.hologramAddon.hologram.config.impl.LegacyHologramConfiguration;
import com.g4vrk.hologramAddon.hologram.config.impl.ModernHologramConfiguration;
import com.g4vrk.hologramAddon.hologram.text.HologramTextGenerator;
import com.g4vrk.hologramAddon.hologram.text.impl.ReactHologramTextConfiguration;
import com.g4vrk.hologramAddon.hologram.text.impl.ReactHologramTextGenerator;
import com.g4vrk.react.api.extension.environment.ExtensionEnvironment;
import com.g4vrk.react.api.player.TrackedPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;

/**
 * Loads the shared text and display settings used by platform renderers.
 */
@ApiStatus.Internal
public record HologramConfiguration(
    @NotNull CommentedConfigurationNode main,
    @NotNull ModernHologramConfiguration modern,
    @NotNull LegacyHologramConfiguration legacy,
    boolean createTeam,
    @NotNull HologramTextGenerator<TrackedPlayer> textGenerator
) {

    private static final String MAIN_CONFIG = "main-config.yml";

    public static @NotNull HologramConfiguration load(
        final @NotNull ExtensionEnvironment environment,
        final @NotNull ClassLoader classLoader
    ) {

        final String language = "en".equalsIgnoreCase(System.getProperty("user.language")) ? "en" : "ru";
        final CommentedConfigurationNode main;

        try {
            main = environment.configs().file(MAIN_CONFIG, ConfigurationNode.class)
                .defaults(classLoader, language + "/" + MAIN_CONFIG)
                .build()
                .loadNode();
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not load " + MAIN_CONFIG, exception);
        }

        final ConfigurationNode settings = main.node("settings");
        return new HologramConfiguration(
            main,
            new ModernHologramConfiguration(settings.node("text-display")),
            new LegacyHologramConfiguration(settings.node("armor-stand")),
            settings.node("create-team").getBoolean(false),
            new ReactHologramTextGenerator(new ReactHologramTextConfiguration(settings.node("text")), environment.text())
        );

    }

}
