package com.g4vrk.hologramAddon.fabric;

import com.g4vrk.hologramAddon.HologramConfiguration;
import com.g4vrk.hologramAddon.command.HologramCommand;
import com.g4vrk.hologramAddon.fabric.display.FabricDisplayRenderer;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Renders inference history as per-viewer PacketEvents text display entities.
 */
public final class FabricHologramAddon extends JavaAddon {

    private FabricDisplayRenderer renderer;
    private volatile HologramConfiguration settings;
    private volatile HologramCommand command;
    private volatile CompletableFuture<Void> pendingReload;
    private volatile boolean disabled;

    @Override
    public void onEnable() {

        this.settings = HologramConfiguration.load(environment(), getClass().getClassLoader());
        this.renderer = new FabricDisplayRenderer(environment(), () -> this.settings, () -> this.disabled);
        this.command = new HologramCommand(
                environment().actions(),
                environment().scheduler(),
                this::reloadAsync,
                this::resync,
                this.settings.main()
        );
        this.command.register(environment().commands());
        environment().listeners().register(new InferenceListener());
        environment().scheduler().schedule(this.renderer::tick, TickSchedule.fixedRate(5));

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
        if (this.renderer != null) {
            this.renderer.close();
        }

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
                .thenAcceptAsync(
                        this::applyReload,
                        task -> environment().scheduler().schedule(task, TickSchedule.instant())
                );
        this.pendingReload = result;
        return result;

    }

    private synchronized void applyReload(final @NotNull HologramConfiguration newSettings) {

        if (this.disabled) {
            return;
        }
        this.settings = newSettings;
        this.command.reload(newSettings.main());
        this.renderer.clearDisplays();
        for (final TrackedPlayer player : environment().players()) {
            this.renderer.update(player);
        }

    }

    private void resync(final @NotNull PlatformPlayer player) {

        this.renderer.removeViewer(player.uniqueId());
        final TrackedPlayer trackedPlayer = environment().players().get(player.uniqueId());
        if (trackedPlayer != null) {
            this.renderer.update(trackedPlayer);
        }

    }

    /**
     * Schedules display updates for inference results.
     */
    private final class InferenceListener {

        @Subscribe(priority = EventPriority.MONITOR)
        public void onInferenceResult(final @NotNull InferenceResultEvent event) {

            final TrackedPlayer target = event.check().player();
            environment().scheduler().scheduleEntity(target.platform(), () -> FabricHologramAddon.this.renderer.update(target), TickSchedule.instant());

        }

    }

}
