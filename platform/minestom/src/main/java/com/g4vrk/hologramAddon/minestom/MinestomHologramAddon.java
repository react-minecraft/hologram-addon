package com.g4vrk.hologramAddon.minestom;

import com.g4vrk.hologramAddon.HologramConfiguration;
import com.g4vrk.hologramAddon.command.HologramCommand;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Displays inference history with Minestom text display entities.
 */
public final class MinestomHologramAddon extends JavaAddon {

    private MinestomDisplayManager displays;
    private volatile HologramConfiguration settings;
    private volatile HologramCommand command;
    private volatile CompletableFuture<Void> pendingReload;
    private volatile boolean disabled;

    @Override
    public void onEnable() {

        this.settings = HologramConfiguration.load(environment(), getClass().getClassLoader());
        this.displays = new MinestomDisplayManager(environment(), () -> this.settings, () -> this.disabled);
        this.command = new HologramCommand(
                environment().actions(),
                environment().scheduler(),
                this::reloadAsync,
                this::resync,
                this.settings.main()
        );
        this.command.register(environment().commands());
        environment().listeners().register(new InferenceListener());
        environment().listeners().register(EventListener.builder(PlayerMoveEvent.class)
                .handler(this.displays::move)
                .build());
        environment().listeners().register(EventListener.builder(PlayerDisconnectEvent.class)
                .handler(event -> this.displays.remove(event.getPlayer().getUuid()))
                .build());

    }

    @Override
    public void onDisable() {

        synchronized (this) {
            this.disabled = true;
            if (this.pendingReload != null) {
                this.pendingReload.cancel(false);
                this.pendingReload = null;
            }
        }
        this.displays.clear();

    }

    private synchronized @NotNull CompletableFuture<Void> reloadAsync() {

        if (this.disabled) {
            return CompletableFuture.failedFuture(new IllegalStateException("Hologram addon is disabled"));
        }
        if (this.pendingReload != null && !this.pendingReload.isDone()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Hologram addon is already reloading"));
        }

        final CompletableFuture<Void> result = CompletableFuture
                .supplyAsync(() -> HologramConfiguration.load(environment(), getClass().getClassLoader()))
                .thenAcceptAsync(this::applyReload, task -> environment().scheduler().schedule(task, TickSchedule.instant()));
        this.pendingReload = result;
        return result;

    }

    private synchronized void applyReload(final @NotNull HologramConfiguration newSettings) {

        if (this.disabled) {
            return;
        }
        this.settings = newSettings;
        this.command.reload(newSettings.main());
        this.displays.clear();
        environment().players().forEach(this.displays::update);

    }

    private void resync(final @NotNull PlatformPlayer player) {

        this.displays.remove(player.uniqueId());
        final TrackedPlayer trackedPlayer = environment().players().get(player.uniqueId());
        if (trackedPlayer != null) {
            this.displays.update(trackedPlayer);
        }

    }

    /**
     * Schedules display updates after inference results.
     */
    private final class InferenceListener {

        @Subscribe(priority = EventPriority.MONITOR)
        public void onInferenceResult(final @NotNull InferenceResultEvent event) {

            final TrackedPlayer target = event.check().player();
            final PlatformPlayer platformPlayer = target.platform();
            environment().scheduler().scheduleEntity(
                    platformPlayer,
                    () -> MinestomHologramAddon.this.displays.update(target),
                    TickSchedule.instant()
            );

        }

    }

}
