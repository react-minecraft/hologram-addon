package com.g4vrk.hologramAddon.hologram.text.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text formatting settings for React player holograms.
 */
@ApiStatus.Internal
public final class ReactHologramTextConfiguration {

    private static final List<String> DEFAULT_LINES = List.of("<gray>ᴀᴠɢ ᴘʀᴏʙ </gray>{statistic:avg-probability:colored}<gray> | ᴍᴀx ᴘʀᴏʙ </gray>{statistic:max-probability:colored}", "{history:last-5}");

    private final List<String> lines;
    private final String probabilityFormat;
    private final String historySeparator;

    public ReactHologramTextConfiguration(
        final @NotNull ConfigurationNode node
    ) {

        this.lines = readLines(node.node("lines"));
        this.probabilityFormat = validateFormat(node.node("format", "probability").getString("0.000%"));
        this.historySeparator = node.node("history", "separator").getString(" ");

    }

    public @NotNull List<String> lines() {

        return this.lines;

    }

    public @NotNull String probabilityFormat() {

        return this.probabilityFormat;

    }

    public @NotNull String historySeparator() {

        return this.historySeparator;

    }

    private static @NotNull List<String> readLines(
        final @NotNull ConfigurationNode node
    ) {

        final List<String> result = new ArrayList<>();

        for (final ConfigurationNode child : node.childrenList()) {
            final String value = child.getString();

            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }

        if (result.isEmpty()) {
            return DEFAULT_LINES;
        }

        return List.copyOf(result);

    }

    private static @NotNull String validateFormat(
        final @NotNull String format
    ) {

        try {
            new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.US));
            return format;
        } catch (final IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid decimal format: " + format, ex);
        }

    }

}
