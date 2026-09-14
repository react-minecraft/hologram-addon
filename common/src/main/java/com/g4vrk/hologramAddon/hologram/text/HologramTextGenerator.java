package com.g4vrk.hologramAddon.hologram.text;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface HologramTextGenerator<S> {

    @NotNull List<Component> generate(@NotNull S source);

}
