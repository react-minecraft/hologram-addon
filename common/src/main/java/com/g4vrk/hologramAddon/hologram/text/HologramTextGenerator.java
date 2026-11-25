package com.g4vrk.hologramAddon.hologram.text;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Generates rendered hologram lines for a tracked source.
 *
 * @param <S> source type
 */
@ApiStatus.Internal
public interface HologramTextGenerator<S> {

    @NotNull List<Component> generate(
        @NotNull S source
    );

}
