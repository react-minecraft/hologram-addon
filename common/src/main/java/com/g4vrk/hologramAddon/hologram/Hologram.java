package com.g4vrk.hologramAddon.hologram;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Hologram {

    void display(@NotNull List<Component> lines);

    void hide();

}