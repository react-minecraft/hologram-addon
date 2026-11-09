package com.g4vrk.hologramAddon.listener;

import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.text.HologramTextGenerator;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
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

    private final Supplier<Set<UUID>> viewersSupplier;
    private final Function<Player, TargetedHologram> hologramFactory;
    private final Supplier<HologramTextGenerator<TrackedPlayer>> hologramTextGeneratorSupplier;
    private final Scheduler scheduler;

    public HistoryEntryAddedListener(
            final @NotNull Supplier<Set<UUID>> viewersSupplier,
            final @NotNull Function<Player, TargetedHologram> hologramFactory,
            final @NotNull Supplier<HologramTextGenerator<TrackedPlayer>> hologramTextGeneratorSupplier,
            final @NotNull Scheduler scheduler
    ) {
        this.viewersSupplier = viewersSupplier;
        this.hologramFactory = hologramFactory;
        this.hologramTextGeneratorSupplier = hologramTextGeneratorSupplier;
        this.scheduler = scheduler;
    }

    @Subscribe(priority = EventPriority.MONITOR)
    public void onInferenceResult(final @NotNull InferenceResultEvent event) {
        final TrackedPlayer player = event.check().player();
        final Player target = player.bukkit();
        this.scheduler.scheduleEntity(target, () -> update(player, target), TickSchedule.instant());
    }

    private void update(final @NotNull TrackedPlayer player, final @NotNull Player target) {
        if (!target.isOnline()) {
            return;
        }

        final TargetedHologram hologram = this.hologramFactory.apply(target);

        for (final UUID uuid : this.viewersSupplier.get()) {
            final Player viewer = Bukkit.getPlayer(uuid);

            if (viewer == null
                    || !viewer.isOnline()
                    || !viewer.getWorld().equals(target.getWorld())) {
                continue;
            }

            final boolean tracking = viewer.equals(target) || target.getTrackedPlayers().contains(viewer);

            if (!tracking) {
                hologram.removeViewer(viewer);
                continue;
            }

            hologram.addViewer(viewer);
        }

        hologram.display(this.hologramTextGeneratorSupplier.get().generate(player));
    }

}
