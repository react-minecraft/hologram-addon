package com.g4vrk.hologramAddon.hologram;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the viewer and packet state of a single target hologram.
 */
@ApiStatus.Internal
public abstract class TargetedHologram implements Hologram {

    private final Set<Player> viewers = new ObjectOpenHashSet<>();
    private final Map<UUID, Long> revisions = new HashMap<>();

    protected final Player target;

    private boolean displayed;
    private boolean closed;
    private long revision;

    protected TargetedHologram(
        final @NotNull Player target
    ) {

        this.target = target;

    }

    public final synchronized long nextRevision() {

        return ++this.revision;

    }

    public final synchronized void updateViewer(
        final @NotNull Player viewer,
        final boolean visible,
        final @NotNull List<Component> lines,
        final long revision
    ) {

        if (this.closed) {
            return;
        }

        final UUID uuid = viewer.getUniqueId();

        if (!viewer.isOnline()) {
            this.revisions.remove(uuid);

            if (this.viewers.remove(viewer)) {
                this.hideFrom(viewer);
            }

            return;
        }

        if (revision < this.revisions.getOrDefault(uuid, 0L)) {
            return;
        }

        this.revisions.put(uuid, revision);

        if (visible) {
            this.viewers.add(viewer);
            this.displayed = true;
            this.showTo(viewer, lines);
        } else if (this.viewers.remove(viewer)) {
            this.hideFrom(viewer);
        }

    }

    public final synchronized void removeViewer(
        final @NotNull Player viewer
    ) {

        final UUID uuid = viewer.getUniqueId();

        if (viewer.isOnline()) {
            this.revisions.put(uuid, ++this.revision);
        } else {
            this.revisions.remove(uuid);
        }

        if (!this.viewers.remove(viewer)) {
            return;
        }

        this.hideFrom(viewer);

    }

    @Override
    public final synchronized void hide() {

        this.closed = true;

        if (!this.displayed) {
            this.viewers.clear();
            this.revisions.clear();
            return;
        }

        this.displayed = false;

        for (final Player viewer : this.viewers) {
            this.hideFrom(viewer);
        }

        this.viewers.clear();
        this.revisions.clear();

    }

    public final synchronized boolean move() {

        if (this.closed) {
            return false;
        }

        this.prepareMove();

        for (final Player viewer : this.viewers) {
            this.moveTo(viewer);
        }

        return true;

    }

    protected void prepareMove() {

    }

    protected void moveTo(
        final @NotNull Player viewer
    ) {

    }

    protected abstract void showTo(
        @NotNull Player viewer,
        @NotNull List<Component> lines
    );

    public abstract void hideFrom(
        @NotNull Player viewer
    );

}
