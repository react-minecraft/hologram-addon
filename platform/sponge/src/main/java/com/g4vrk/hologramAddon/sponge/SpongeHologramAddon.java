package com.g4vrk.hologramAddon.sponge;

import com.g4vrk.hologramAddon.HologramConfiguration;
import com.g4vrk.hologramAddon.command.HologramCommand;
import com.g4vrk.hologramAddon.sponge.display.SpongeDisplay;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders per-viewer fake text displays on Sponge through PacketEvents.
 */
public final class SpongeHologramAddon extends JavaAddon {

    private final Map<UUID, SpongeDisplay> displays = new ConcurrentHashMap<>();
    private volatile HologramConfiguration settings;
    private volatile HologramCommand command;
    private volatile CompletableFuture<Void> pendingReload;
    private volatile boolean disabled;

    @Override
    public void onEnable() {

        this.settings = HologramConfiguration.load(environment(), getClass().getClassLoader());
        this.command = new HologramCommand(
                environment().actions(),
                environment().scheduler(),
                this::reloadAsync,
                this::resync,
                this.settings.main()
        );
        this.command.register(environment().commands());
        environment().listeners().register(new InferenceListener());
        environment().listeners().register(new SpongeListener());

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
        this.displays.values().forEach(SpongeDisplay::hide);
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

    private synchronized void applyReload(final @NotNull HologramConfiguration settings) {

        if (this.disabled) {
            return;
        }
        this.settings = settings;
        this.command.reload(settings.main());
        this.displays.values().forEach(SpongeDisplay::hide);
        this.displays.clear();
        environment().players().forEach(this::update);

    }

    private void update(final @NotNull TrackedPlayer tracked) {

        if (this.disabled || !tracked.platform().online()) {
            return;
        }
        final ServerPlayer target = tracked.platform().unwrap(ServerPlayer.class);
        if (target == null) {
            return;
        }
        final List<Component> lines = this.settings.textGenerator().generate(tracked);
        if (lines.isEmpty()) {
            final SpongeDisplay removed = this.displays.remove(target.uniqueId());
            if (removed != null) {
                removed.hide();
            }
            return;
        }
        this.displays.computeIfAbsent(target.uniqueId(), ignored -> new SpongeDisplay(environment(), () -> this.settings, target)).update(lines);

    }

    private void resync(final @NotNull PlatformPlayer viewer) {

        for (final SpongeDisplay display : this.displays.values()) {
            display.remove(viewer.uniqueId());
            display.updateViewer(viewer);
        }
        final TrackedPlayer tracked = environment().players().get(viewer.uniqueId());
        if (tracked != null) {
            update(tracked);
        }

    }

    /**
     * Schedules display updates for inference results.
     */
    private final class InferenceListener {

        @Subscribe(priority = EventPriority.MONITOR)
        public void onInferenceResult(final @NotNull InferenceResultEvent event) {

            final TrackedPlayer tracked = event.check().player();
            environment().scheduler().scheduleEntity(tracked.platform(), () -> update(tracked), TickSchedule.instant());

        }

    }

    /**
     * Synchronizes display visibility with Sponge player events.
     */
    private final class SpongeListener {

        @Listener
        public void onMove(final @NotNull MoveEntityEvent event) {

            if (event.entity() instanceof final ServerPlayer player) {
                final PlatformPlayer platformViewer = environment().platform().adaptPlayer(player);
                for (final SpongeDisplay display : displays.values()) {
                    if (display.targets(player.uniqueId())) {
                        display.move();
                    }
                    display.updateViewer(platformViewer);
                }
            }

        }

        @Listener
        public void onJoin(final @NotNull ServerSideConnectionEvent.Join event) {

            final ServerPlayer player = event.player();
            final PlatformPlayer platformPlayer = environment().platform().adaptPlayer(player);
            environment().scheduler().scheduleEntity(
                    platformPlayer,
                    () -> resync(platformPlayer),
                    TickSchedule.instant()
            );

        }

        @Listener
        public void onDisconnect(final @NotNull ServerSideConnectionEvent.Disconnect event) {

            event.profile().ifPresent(profile -> {
                final UUID uuid = profile.uniqueId();
                for (final SpongeDisplay display : displays.values()) {
                    display.forget(uuid);
                }
            });

        }

    }

}
