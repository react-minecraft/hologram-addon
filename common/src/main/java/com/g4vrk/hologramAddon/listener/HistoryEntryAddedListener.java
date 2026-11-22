package com.g4vrk.hologramAddon.listener;

import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.text.HologramTextGenerator;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Updates a player's hologram when a new model result reaches their history.
 *
 * <p>The event may arrive on any thread, so the update runs on the target's entity scheduler,
 * which is the main thread on Paper and the player's region on Folia.</p>
 */
public final class HistoryEntryAddedListener {

    private static final boolean LEGACY_TRACKING = legacyTracking();
    private final Supplier<Set<UUID>> viewersSupplier;
    private final Function<Player, TargetedHologram> hologramFactory;
    private final Supplier<HologramTextGenerator<TrackedPlayer>> hologramTextGeneratorSupplier;
    private final TaskScheduler<Player> scheduler;

    public HistoryEntryAddedListener(
            final @NotNull Supplier<Set<UUID>> viewersSupplier,
            final @NotNull Function<Player, TargetedHologram> hologramFactory,
            final @NotNull Supplier<HologramTextGenerator<TrackedPlayer>> hologramTextGeneratorSupplier,
            final @NotNull TaskScheduler<Player> scheduler
    ) {

        this.viewersSupplier = viewersSupplier;
        this.hologramFactory = hologramFactory;
        this.hologramTextGeneratorSupplier = hologramTextGeneratorSupplier;
        this.scheduler = scheduler;

    }

    @Subscribe(priority = EventPriority.MONITOR)
    public void onInferenceResult(final @NotNull InferenceResultEvent event) {

        final TrackedPlayer player = event.check().player();
        final Player target = ((Player) player.platform().handle());
        this.scheduler.scheduleEntity(target, () -> update(player, target), TickSchedule.instant());

    }

    private void update(final @NotNull TrackedPlayer player, final @NotNull Player target) {

        if (!target.isOnline()) {
            return;
        }

        final TargetedHologram hologram = this.hologramFactory.apply(target);
        if (hologram == null) {
            return;
        }
        final Set<UUID> viewers = this.viewersSupplier.get();
        final List<Component> lines = this.hologramTextGeneratorSupplier.get().generate(player);
        final long revision = hologram.nextRevision();

        for (final UUID uuid : viewers) {
            final Player viewer = Bukkit.getPlayer(uuid);

            if (viewer == null) {
                viewers.remove(uuid);
                continue;
            }
            this.scheduler.scheduleEntity(viewer, () -> {
                final boolean permitted = viewer.isOnline() && viewer.hasPermission("react.hologram.view");
                final UUID viewerWorld = viewer.getWorld().getUID();
                final boolean legacyTracking = !LEGACY_TRACKING || viewer.equals(target) || viewer.getTrackedPlayers().contains(target);
                this.scheduler.scheduleEntity(target, () -> {
                    final boolean tracking = LEGACY_TRACKING ? legacyTracking : viewer.equals(target) || target.getTrackedPlayers().contains(viewer);
                    hologram.updateViewer(viewer, permitted && tracking && target.isOnline() && target.getWorld().getUID().equals(viewerWorld), lines, revision);
                }, TickSchedule.instant());
            }, TickSchedule.instant());
        }

    }

    private static boolean legacyTracking() {

        try {
            Entity.class.getMethod("getTrackedPlayers");
            return false;
        } catch (final NoSuchMethodException ignored) {
            return true;
        }

    }

}
