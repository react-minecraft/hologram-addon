package com.g4vrk.hologramAddon.listener;

import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.text.HologramTextGenerator;
import com.g4vrk.react.api.event.InferenceHistoryEntryAddedEvent;
import com.g4vrk.react.player.ReactPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class HistoryEntryAddedListener implements Listener {

    private final Supplier<Set<UUID>> viewersSupplier;

    private final Function<Player, TargetedHologram> hologramFactory;

    private final Supplier<HologramTextGenerator<ReactPlayer>> hologramTextGeneratorSupplier;

    public HistoryEntryAddedListener(
            @NotNull Supplier<Set<UUID>> viewersSupplier,
            @NotNull Function<Player, TargetedHologram> hologramFactory,
            @NotNull Supplier<HologramTextGenerator<ReactPlayer>> hologramTextGeneratorSupplier
    ) {
        this.viewersSupplier = viewersSupplier;
        this.hologramFactory = hologramFactory;
        this.hologramTextGeneratorSupplier = hologramTextGeneratorSupplier;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInferenceHistoryEntryAdded(
            @NotNull InferenceHistoryEntryAddedEvent event
    ) {
        final ReactPlayer reactPlayer = event.player();

        final Player target = reactPlayer.bukkitPlayer;

        final TargetedHologram hologram = this.hologramFactory.apply(target);

        for (final UUID uuid : this.viewersSupplier.get()) {

            final Player viewer = Bukkit.getPlayer(uuid);

            if (viewer == null
                    || !viewer.isOnline()
                    || !viewer.getWorld().equals(target.getWorld())) {
                continue;
            }

            final boolean tracking =
                    viewer.equals(target) || target.getTrackedPlayers().contains(viewer);

            if (!tracking) {
                hologram.removeViewer(viewer);
                continue;
            }

            hologram.addViewer(viewer);
        }

        hologram.display(hologramTextGeneratorSupplier.get().generate(reactPlayer));
    }

}
