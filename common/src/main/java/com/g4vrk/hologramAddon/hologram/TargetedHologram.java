package com.g4vrk.hologramAddon.hologram;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public abstract class TargetedHologram implements Hologram {

    private final Set<Player> viewers = new ObjectOpenHashSet<>();

    protected final Player target;

    private boolean displayed;

    protected TargetedHologram(
            @NotNull Player target
    ) {
        this.target = target;
    }

    public final void addViewer(@NotNull Player viewer) {
        this.viewers.add(viewer);
    }

    public final void removeViewer(@NotNull Player viewer) {
        if (!this.viewers.remove(viewer)) {
            return;
        }

        hideFrom(viewer);
    }

    @Override
    public final void display(@NotNull List<Component> lines) {

        this.displayed = true;

        for (final Player viewer : this.viewers) {
            this.showTo(viewer, lines);
        }

    }

    @Override
    public final void hide() {
        if (!this.displayed) {
            return;
        }

        this.displayed = false;

        for (final Player viewer : this.viewers) {
            this.hideFrom(viewer);
        }
    }

    protected abstract void showTo(
            @NotNull Player viewer,
            @NotNull List<Component> lines
    );

    public abstract void hideFrom(@NotNull Player viewer);

}