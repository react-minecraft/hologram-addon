package com.g4vrk.hologramAddon.hologram;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Owns the viewer and packet state of a single target hologram.
 */
public abstract class TargetedHologram implements Hologram {

    private final Set<Player> viewers = new ObjectOpenHashSet<>();

    protected final Player target;

    private boolean displayed;
    private boolean closed;

    protected TargetedHologram(
            @NotNull Player target
    ) {
        this.target = target;
    }

    public final synchronized void addViewer(final @NotNull Player viewer) {

        if (!this.closed) {
            this.viewers.add(viewer);
        }

    }

    public final synchronized void removeViewer(final @NotNull Player viewer) {

        if (!this.viewers.remove(viewer)) {
            return;
        }

        this.hideFrom(viewer);

    }

    @Override
    public final synchronized void display(final @NotNull List<Component> lines) {

        if (this.closed) {
            return;
        }

        this.displayed = true;

        for (final Player viewer : this.viewers) {
            this.showTo(viewer, lines);
        }

    }

    @Override
    public final synchronized void hide() {

        this.closed = true;
        if (!this.displayed) {
            this.viewers.clear();
            return;
        }

        this.displayed = false;

        for (final Player viewer : this.viewers) {
            this.hideFrom(viewer);
        }
        this.viewers.clear();

    }

    protected abstract void showTo(
            @NotNull Player viewer,
            @NotNull List<Component> lines
    );

    public abstract void hideFrom(@NotNull Player viewer);

}
